package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageRegressionTest {
  @TempDir Path root;

  private Path path() {
    return root.resolve("world/shirokaipass/state.json");
  }

  private static class FaultIO implements AtomicFiles.IO {
    boolean denyReplace, failAfterReplace, noAtomic, partialCopy;
    int directories;

    @Override
    public void replace(Path source, Path target) throws IOException {
      if (!target.getFileName().toString().equals("state.json")) {
        AtomicFiles.IO.super.replace(source, target);
        return;
      }
      if (denyReplace) throw new AccessDeniedException(target.toString());
      if (noAtomic)
        throw new AtomicMoveNotSupportedException(
            source.toString(), target.toString(), "test provider");
      AtomicFiles.IO.super.replace(source, target);
      if (failAfterReplace) throw new IOException("fault after committed rename");
    }

    @Override
    public void write(Path target, byte[] bytes) throws IOException {
      if (partialCopy && target.getFileName().toString().equals("state.json")) {
        AtomicFiles.IO.super.write(target, Arrays.copyOf(bytes, Math.min(12, bytes.length)));
        throw new IOException("interrupted non-atomic copy");
      }
      AtomicFiles.IO.super.write(target, bytes);
    }

    @Override
    public void syncDirectory(Path directory) throws IOException {
      directories++;
      AtomicFiles.IO.super.syncDirectory(directory);
    }
  }

  private PassService prepared(Store store, AtomicLong now) throws Exception {
    PassService service = new PassService(store, now::get);
    service.create("s", 10000, 5000, new Config());
    service.edit(s -> s.seasons.get("s").rewards.values().forEach(r -> r.enabled = true));
    return service;
  }

  @Test
  void createsMissingDirectoriesAndRoundTrips() throws Exception {
    Store store = new Store(path());
    State data = new State();
    data.names.put("alice", UUID.randomUUID());
    store.save(data);
    assertTrue(Files.isRegularFile(path()));
    assertEquals(data.names, new Store(path()).load().names);
  }

  @Test
  void normalWriteWorksOnWindowsWithoutOpeningDirectoryChannels() throws Exception {
    Store store = new Store(path());
    State data = new State();
    store.save(data);
    data.names.put("alice", UUID.randomUUID());
    store.save(data);
    assertEquals(1, new Store(path()).load().names.size());
  }

  @Test
  void unsupportedDirectorySyncDoesNotFailAnAlreadyCommittedSave() throws Exception {
    AtomicInteger attempts = new AtomicInteger();
    AtomicFiles.IO windowsStyle =
        new AtomicFiles.IO() {
          @Override
          public boolean directorySyncSupported() {
            return false;
          }

          @Override
          public void syncDirectory(Path directory) throws IOException {
            if (directorySyncSupported()) {
              attempts.incrementAndGet();
              throw new AccessDeniedException(directory.toString());
            }
          }
        };
    new Store(path(), windowsStyle).save(new State());
    assertEquals(0, attempts.get());
    assertNotNull(new Store(path()).load());
  }

  @Test
  void rejectsDirectoryInPlaceOfStateFileWithoutDeletingIt() throws Exception {
    Files.createDirectories(path());
    Path unknown = path().resolve("keep.txt");
    Files.writeString(unknown, "preserve");
    assertThrows(IOException.class, () -> new Store(path()).load());
    assertThrows(IOException.class, () -> new Store(path()).save(new State()));
    assertEquals("preserve", Files.readString(unknown));
  }

  @Test
  void rejectsFileInPlaceOfDirectoryWithoutDeletingIt() throws Exception {
    Path directory = path().getParent();
    Files.createDirectories(directory.getParent());
    Files.writeString(directory, "unknown data");
    assertThrows(IOException.class, () -> new Store(path()).load());
    assertThrows(IOException.class, () -> new Store(path()).save(new State()));
    assertEquals("unknown data", Files.readString(directory));
  }

  @Test
  void missingPreviouslyCreatedDataNeverBecomesEmptyOnRestart() throws Exception {
    Store store = new Store(path());
    store.save(new State());
    Files.delete(path());
    assertThrows(IOException.class, () -> new Store(path()).load());
  }

  @Test
  void actualWritePermissionDenialPreservesPriorFile() throws Exception {
    assumeTrue(Files.getFileStore(root).supportsFileAttributeView("posix"));
    assumeFalse("root".equals(System.getProperty("user.name")));
    Store store = new Store(path());
    store.save(new State());
    String before = Files.readString(path());
    Path parent = path().getParent();
    var old = Files.getPosixFilePermissions(parent);
    try {
      Files.setPosixFilePermissions(parent, PosixFilePermissions.fromString("r-xr-xr-x"));
      assertThrows(IOException.class, () -> store.save(new State()));
      assertEquals(before, Files.readString(path()));
    } finally {
      Files.setPosixFilePermissions(parent, old);
    }
  }

  @Test
  void failureBeforeRenameKeepsOldDataAndBlocksMutation() throws Exception {
    FaultIO io = new FaultIO();
    Store store = new Store(path(), io);
    AtomicLong now = new AtomicLong(100000);
    PassService service = prepared(store, now);
    UUID id = UUID.randomUUID();
    String before = Files.readString(path());
    io.denyReplace = true;
    assertThrows(IllegalStateException.class, () -> service.add(id, 100, "xp", false));
    assertFalse(service.available());
    assertEquals(before, Files.readString(path()));
    assertThrows(
        IllegalStateException.class, () -> service.claim(id, "s", List.of("free:1"), new Random()));
    io.denyReplace = false;
    service.resume();
    assertTrue(service.available());
    assertEquals(0, service.progress("s", id).xp);
    assertFalse(service.add(id, 100, "xp", false).duplicate());
  }

  @Test
  void renameCommittedThenFailedDoesNotDoubleApplyReceiptOnRecovery() throws Exception {
    FaultIO io = new FaultIO();
    AtomicLong now = new AtomicLong(100000);
    PassService service = prepared(new Store(path(), io), now);
    UUID id = UUID.randomUUID();
    io.failAfterReplace = true;
    assertThrows(IllegalStateException.class, () -> service.add(id, 100, "xp", false));
    assertEquals(100, new Store(path()).load().players.get(playerKey("s", id)).xp);
    io.failAfterReplace = false;
    service.resume();
    assertTrue(service.add(id, 100, "xp", false).duplicate());
    assertEquals(100, service.progress("s", id).xp);
  }

  @Test
  void fallbackJournalRecoversInterruptedCopyWithoutRollback() throws Exception {
    FaultIO io = new FaultIO();
    AtomicLong now = new AtomicLong(100000);
    PassService service = prepared(new Store(path(), io), now);
    UUID id = UUID.randomUUID();
    io.noAtomic = true;
    io.partialCopy = true;
    assertThrows(IllegalStateException.class, () -> service.add(id, 100, "xp", false));
    assertTrue(Files.isRegularFile(AtomicFiles.pending(path())));
    io.partialCopy = false;
    service.resume();
    assertTrue(service.available());
    assertEquals(100, service.progress("s", id).xp);
    assertTrue(service.add(id, 100, "xp", false).duplicate());
    assertFalse(Files.exists(AtomicFiles.pending(path())));
    try (var entries = Files.list(path().getParent())) {
      assertTrue(entries.anyMatch(p -> p.getFileName().toString().contains(".interrupted-")));
    }
  }

  @Test
  void normalFallbackWithoutAtomicRenamePersists() throws Exception {
    FaultIO io = new FaultIO();
    io.noAtomic = true;
    Store store = new Store(path(), io);
    State data = new State();
    data.names.put("offline", UUID.randomUUID());
    store.save(data);
    assertEquals(data.names, store.load().names);
    assertFalse(Files.exists(AtomicFiles.pending(path())));
  }

  @Test
  void unknownJournalIsNotDeletedOrUsedToOverwriteValidData() throws Exception {
    Store store = new Store(path());
    store.save(new State());
    String before = Files.readString(path());
    Files.writeString(AtomicFiles.pending(path()), "unknown content");
    assertThrows(IOException.class, store::load);
    assertThrows(IOException.class, () -> store.save(new State()));
    assertEquals(before, Files.readString(path()));
    assertEquals("unknown content", Files.readString(AtomicFiles.pending(path())));
  }

  @Test
  void recoveryRefusesAValidButUnexpectedRollbackOrExternalEdit() throws Exception {
    FaultIO io = new FaultIO();
    AtomicLong now = new AtomicLong(100000);
    PassService service = prepared(new Store(path(), io), now);
    UUID id = UUID.randomUUID();
    io.denyReplace = true;
    assertThrows(IllegalStateException.class, () -> service.add(id, 100, null, false));
    io.denyReplace = false;
    State external = new Store(path()).load();
    external.names.put("external", UUID.randomUUID());
    new Store(path()).save(external);
    String before = Files.readString(path());
    assertThrows(IOException.class, service::resume);
    assertFalse(service.available());
    assertEquals(before, Files.readString(path()));
  }

  @Test
  void loginBoundaryAbsorbsPersistenceFailureAndSkipsAllDeliveryEffects() throws Exception {
    FaultIO io = new FaultIO();
    PassService service = prepared(new Store(path(), io), new AtomicLong(100000));
    io.denyReplace = true;
    AtomicInteger errors = new AtomicInteger(), effects = new AtomicInteger();
    assertFalse(
        LoginGuard.run(
            () -> {
              service.remember(UUID.randomUUID(), "NewPlayer");
              effects.incrementAndGet();
            },
            e -> errors.incrementAndGet()));
    assertFalse(
        LoginGuard.run(
            () -> {
              service.requireWritable();
              effects.incrementAndGet();
            },
            e -> errors.incrementAndGet()));
    assertEquals(0, effects.get());
    assertEquals(2, errors.get());
  }

  @Test
  void failedInitialLoadStillProvidesGuardedFacadeAndRecoversWithoutRestart() throws Exception {
    Store store = new Store(path());
    State original = new State();
    UUID id = UUID.randomUUID();
    original.names.put("known", id);
    store.save(original);
    String good = Files.readString(path());
    Files.writeString(path(), "broken");
    PassService service = PassService.open(store, System::currentTimeMillis);
    assertFalse(service.available());
    assertFalse(LoginGuard.run(() -> service.remember(UUID.randomUUID(), "New"), e -> {}));
    assertThrows(IllegalStateException.class, service::snapshot);
    assertEquals("broken", Files.readString(path()));
    Files.writeString(path(), good);
    service.resume();
    assertTrue(service.available());
    assertEquals(id, service.known("known"));
  }

  @Test
  void recoveryMarksUncertainDeliveryReviewAndNeverGrantsAgain() throws Exception {
    FaultIO io = new FaultIO();
    AtomicLong now = new AtomicLong(100000);
    PassService service = prepared(new Store(path(), io), now);
    UUID id = UUID.randomUUID();
    service.add(id, 100, null, false);
    Delivery delivery = service.claim(id, "s", List.of("free:1"), new Random()).getFirst();
    io.failAfterReplace = true;
    assertThrows(
        IllegalStateException.class, () -> service.status(delivery.id, "READY", "DELIVERING", ""));
    io.failAfterReplace = false;
    service.resume();
    assertEquals("REVIEW", service.snapshot().deliveries.get(delivery.id).status);
    assertTrue(service.claim(id, "s", List.of("free:1"), new Random()).isEmpty());
  }

  @Test
  void restartAfterFailedSaveLoadsCommittedProgressAndReceipts() throws Exception {
    FaultIO io = new FaultIO();
    AtomicLong now = new AtomicLong(100000);
    PassService service = prepared(new Store(path(), io), now);
    UUID id = UUID.randomUUID();
    io.failAfterReplace = true;
    assertThrows(IllegalStateException.class, () -> service.grantPremium(id, 1000, "order"));
    PassService restarted = new PassService(new Store(path()), now::get);
    assertTrue(restarted.hasPremium("s", id));
    long expiry = restarted.premiumExpiresAt(id);
    assertEquals(expiry, restarted.grantPremium(id, 1000, "order"));
  }

  @Test
  void uncheckedProviderFailureAfterRenameAlsoBlocksUntilReload() throws Exception {
    AtomicBoolean failing = new AtomicBoolean();
    AtomicFiles.IO io =
        new AtomicFiles.IO() {
          @Override
          public void replace(Path source, Path target) throws IOException {
            AtomicFiles.IO.super.replace(source, target);
            if (failing.get() && target.getFileName().toString().equals("state.json"))
              throw new SecurityException("provider denied directory synchronization");
          }
        };
    AtomicLong now = new AtomicLong(100000);
    PassService service = prepared(new Store(path(), io), now);
    UUID id = UUID.randomUUID();
    failing.set(true);
    assertThrows(IllegalStateException.class, () -> service.add(id, 100, "order", false));
    assertFalse(service.available());
    failing.set(false);
    service.resume();
    assertEquals(100, service.progress("s", id).xp);
    assertTrue(service.add(id, 100, "order", false).duplicate());
  }
}
