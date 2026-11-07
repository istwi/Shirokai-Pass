package br.com.shirokai.pass;

import static java.nio.file.StandardOpenOption.*;

import com.google.gson.*;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

/** Closed-file replacement with a validated redo journal for providers without atomic rename. */
final class AtomicFiles {
  interface IO {
    default void replace(Path source, Path target) throws IOException {
      Files.move(
          source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    default void write(Path file, byte[] bytes) throws IOException {
      try (FileChannel channel = FileChannel.open(file, CREATE, TRUNCATE_EXISTING, WRITE)) {
        ByteBuffer data = ByteBuffer.wrap(bytes);
        while (data.hasRemaining()) channel.write(data);
        channel.force(true);
      }
    }

    default boolean directorySyncSupported() {
      // Java's Windows file provider cannot open a directory as a FileChannel (AccessDenied).
      return !System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");
    }

    default void syncDirectory(Path directory) throws IOException {
      if (!directorySyncSupported()) return;
      try (FileChannel channel = FileChannel.open(directory, READ)) {
        channel.force(true);
      } catch (UnsupportedOperationException unsupported) {
        /* Provider explicitly lacks directory fsync. */
      }
    }
  }

  static final IO NIO = new IO() {};

  record Document(String text, boolean journal) {}

  private record Journal(
      int shirokaipassJournal, String target, String previous, String checksum, String payload) {}

  static Path pending(Path target) {
    return target.resolveSibling(target.getFileName() + ".pending");
  }

  static Path absolute(Path target) {
    return target.toAbsolutePath().normalize();
  }

  private static BasicFileAttributes attributes(Path path) throws IOException {
    try {
      return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    } catch (NoSuchFileException missing) {
      return null;
    }
  }

  static void layout(Path target, boolean create) throws IOException {
    for (Path ancestor = target.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
      BasicFileAttributes a = attributes(ancestor);
      if (a != null && !Files.isDirectory(ancestor))
        throw new IOException(
            "Esperado diretório de armazenamento, mas existe um arquivo: " + ancestor);
    }
    regularOrMissing(target);
    regularOrMissing(pending(target));
    if (create) Files.createDirectories(target.getParent());
  }

  private static void regularOrMissing(Path path) throws IOException {
    BasicFileAttributes a = attributes(path);
    if (a != null && !a.isRegularFile())
      throw new IOException(
          "Esperado arquivo regular, mas o caminho é diretório ou link: "
              + path
              + ". Nada foi removido.");
  }

  static Document read(Path file) throws IOException {
    Path target = absolute(file);
    layout(target, false);
    if (attributes(pending(target)) != null) return new Document(journal(target).payload(), true);
    if (attributes(target) == null) return null;
    return new Document(Files.readString(target), false);
  }

  private static String hash(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }

  private static Journal journal(Path target) throws IOException {
    try {
      Journal journal = Store.JSON.fromJson(Files.readString(pending(target)), Journal.class);
      if (journal == null
          || journal.shirokaipassJournal() != 1
          || !target.getFileName().toString().equals(journal.target())
          || journal.payload() == null
          || !hash(journal.payload().getBytes(StandardCharsets.UTF_8)).equals(journal.checksum()))
        throw new IllegalArgumentException("assinatura/checksum inválido");
      if (!JsonParser.parseString(journal.payload()).isJsonObject())
        throw new IllegalArgumentException("documento inválido");
      return journal;
    } catch (RuntimeException e) {
      throw new IOException(
          "Journal desconhecido ou incompleto: "
              + pending(target)
              + ". Arquivos preservados para reparo.",
          e);
    }
  }

  /** Caller must validate the candidate's application schema before completing its journal. */
  static void complete(Path file, IO io) throws IOException {
    Path target = absolute(file);
    layout(target, true);
    if (attributes(pending(target)) == null) return;
    Journal journal = journal(target);
    byte[] payload = journal.payload().getBytes(StandardCharsets.UTF_8);
    if (attributes(target) != null) {
      byte[] current = Files.readAllBytes(target);
      String currentHash = hash(current);
      if (!currentHash.equals(journal.checksum()) && !currentHash.equals(journal.previous())) {
        boolean validJson;
        try {
          JsonParser.parseString(new String(current, StandardCharsets.UTF_8));
          validJson = true;
        } catch (RuntimeException invalid) {
          validJson = false;
        }
        if (validJson)
          throw new IOException(
              "Arquivo foi alterado fora da transação: "
                  + target
                  + ". Journal e arquivo preservados; recuperação manual necessária.");
        // Preserve an interrupted partial copy before replay; never delete unknown contents.
        Files.copy(
            target,
            target.resolveSibling(
                target.getFileName() + ".interrupted-" + UUID.randomUUID() + ".bak"));
      }
    }
    io.write(target, payload);
    io.syncDirectory(target.getParent());
    Files.delete(pending(target));
    io.syncDirectory(target.getParent());
  }

  static void write(Path file, String json, IO io) throws IOException {
    Path target = absolute(file);
    layout(target, true);
    if (attributes(pending(target)) != null)
      throw new IOException(
          "Transação pendente em "
              + pending(target)
              + "; recupere o armazenamento antes de gravar.");
    Path temp = Files.createTempFile(target.getParent(), ".shirokai-", ".tmp");
    try {
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
      io.write(temp, bytes);
      try {
        io.replace(temp, target);
      } catch (AtomicMoveNotSupportedException unsupported) {
        String previous = attributes(target) == null ? null : hash(Files.readAllBytes(target));
        Journal journal =
            new Journal(1, target.getFileName().toString(), previous, hash(bytes), json);
        // CREATE_NEW protects an existing/unknown journal. An incomplete journal always fails
        // closed.
        Files.createFile(pending(target));
        io.write(pending(target), Store.JSON.toJson(journal).getBytes(StandardCharsets.UTF_8));
        io.syncDirectory(target.getParent());
        complete(target, io);
        return;
      }
      io.syncDirectory(target.getParent());
    } finally {
      try {
        Files.deleteIfExists(temp);
      } catch (IOException cleanup) {
        System.getLogger(AtomicFiles.class.getName())
            .log(
                System.Logger.Level.WARNING,
                "Não foi possível remover temporário próprio: " + temp,
                cleanup);
      }
    }
  }
}
