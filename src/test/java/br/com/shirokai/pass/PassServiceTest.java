package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class PassServiceTest {
  @TempDir Path dir;
  AtomicLong now;
  Store store;
  PassService service;
  UUID player;

  @BeforeEach
  void setup() throws Exception {
    now = new AtomicLong(1_000_000);
    store = new Store(dir.resolve("state.json"));
    service = new PassService(store, now::get);
    service.create("s1", 10000, 5000, new Config());
    service.edit(s -> s.seasons.get("s1").rewards.values().forEach(r -> r.enabled = true));
    player = UUID.randomUUID();
  }

  @Test
  void progressiveCurveAndCap() {
    assertEquals(0, level(service.active(), 99));
    assertEquals(1, level(service.active(), 100));
    assertEquals(2, level(service.active(), 225));
    service.add(player, Long.MAX_VALUE, null, false);
    assertEquals(100, level(service.active(), service.progress("s1", player).xp));
  }

  @Test
  void offlineAwardPersistsOnRestart() throws Exception {
    service.remember(player, "Alice");
    service.add(player, 700, "quest/1", false);
    PassService restarted = new PassService(store, now::get);
    assertEquals(700, restarted.progress("s1", player).xp);
    assertEquals(player, restarted.known("ALICE"));
    assertTrue(restarted.add(player, 700, "quest/1", false).duplicate());
  }

  @Test
  void duplicateCannotBeReusedWithDifferentParameters() {
    service.add(player, 10, "tx", false);
    assertThrows(IllegalArgumentException.class, () -> service.add(player, 11, "tx", false));
    assertThrows(
        IllegalArgumentException.class, () -> service.add(UUID.randomUUID(), 10, "tx", false));
  }

  @Test
  void duplicateSurvivesSeasonBoundary() {
    service.add(player, 10, "tx", false);
    now.addAndGet(10001);
    service.create("s2", 10000, 5000, new Config());
    assertTrue(service.add(player, 10, "tx", false).duplicate());
    assertEquals(0, service.progress("s2", player).xp);
  }

  @Test
  void onlyAddGetsMultiplier() {
    service.startEvent(2, 5000);
    assertEquals(200, service.add(player, 100, null, false).after());
    assertEquals(100, service.adjust(player, "set", 100));
    assertEquals(60, service.adjust(player, "remove", 40));
    assertEquals(0, service.adjust(player, "reset", 0));
    assertEquals(100, service.add(player, 100, null, true).after());
  }

  @Test
  void eventExpiresAndHistoryRemains() {
    service.startEvent(2, 1000);
    now.addAndGet(1000);
    assertEquals(1, service.multiplier());
    assertEquals(100, service.add(player, 100, null, false).after());
    assertEquals(1, service.snapshot().events.size());
  }

  @Test
  void validatesNegativeZeroAndOverflow() {
    assertThrows(IllegalArgumentException.class, () -> service.add(player, 0, null, false));
    assertThrows(IllegalArgumentException.class, () -> service.add(player, -1, null, false));
    service.startEvent(2, 1000);
    assertThrows(ArithmeticException.class, () -> service.add(player, Long.MAX_VALUE, null, false));
    assertEquals(0, service.progress("s1", player).xp);
    assertThrows(IllegalArgumentException.class, () -> service.adjust(player, "set", -1));
  }

  @Test
  void claimsFreeAndPremiumOnce() {
    service.add(player, 225, null, false);
    assertEquals(
        2,
        service
            .claim(player, "s1", List.of("free:1", "free:2", "premium:1"), new Random(1))
            .size());
    assertTrue(service.claim(player, "s1", List.of("free:1"), new Random(1)).isEmpty());
    service.premium("s1", player, true);
    assertEquals(1, service.claim(player, "s1", List.of("premium:1"), new Random(1)).size());
  }

  @Test
  void reducingXpDoesNotClearClaims() {
    service.add(player, 100, null, false);
    service.claim(player, "s1", List.of("free:1"), new Random());
    service.adjust(player, "reset", 0);
    service.add(player, 100, null, false);
    assertTrue(service.claim(player, "s1", List.of("free:1"), new Random()).isEmpty());
  }

  @Test
  void graceAllowsClaimButNotEarning() {
    service.add(player, 100, null, false);
    now.addAndGet(10000);
    assertThrows(IllegalArgumentException.class, () -> service.add(player, 1, null, false));
    assertEquals(1, service.claim(player, "s1", List.of("free:1"), new Random()).size());
    now.addAndGet(5000);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.claim(player, "s1", List.of("free:2"), new Random()));
  }

  @Test
  void randomChoiceStoredAndRecoveredWithoutReroll() throws Exception {
    service.add(player, 100, null, false);
    service.edit(
        s -> {
          Reward r = s.seasons.get("s1").rewards.get("free:1");
          r.mode = "random";
          r.choices.add(new Choice("item", "minecraft:emerald", 1, 9));
        });
    Delivery d = service.claim(player, "s1", List.of("free:1"), new Random(1)).getFirst();
    assertEquals(1, d.prizes.size());
    service.status(d.id, "READY", "DELIVERING", "");
    PassService restarted = new PassService(store, now::get);
    restarted.recover();
    Delivery recovered = restarted.snapshot().deliveries.get(d.id);
    assertEquals("REVIEW", recovered.status);
    assertEquals(d.prizes.getFirst().value, recovered.prizes.getFirst().value);
  }

  @Test
  void corruptStateFailsClosed() throws Exception {
    Files.writeString(dir.resolve("state.json"), "broken!");
    assertThrows(java.io.IOException.class, () -> new PassService(store, now::get));
  }

  @Test
  void atomicMutationRollsBackOnValidationFailure() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.edit(
                s -> {
                  s.seasons.clear();
                  throw new IllegalArgumentException("fail");
                }));
    assertEquals("s1", service.active().id);
  }

  @Test
  void seasonsKeepPermanentHistory() {
    service.add(player, 225, null, false);
    now.addAndGet(10000);
    service.create("s2", 10000, 1000, new Config());
    assertEquals(225, service.progress("s1", player).xp);
    assertEquals(0, service.progress("s2", player).xp);
  }

  @Test
  void returnedDeliveryCannotMutateStoredState() {
    service.add(player, 100, null, false);
    Delivery delivery = service.claim(player, "s1", List.of("free:1"), new Random()).getFirst();
    delivery.status = "DONE";
    delivery.prizes.getFirst().value = "minecraft:air";
    Delivery stored = service.snapshot().deliveries.get(delivery.id);
    assertEquals("READY", stored.status);
    assertEquals("minecraft:diamond", stored.prizes.getFirst().value);
  }

  @Test
  void concurrentRetriesAwardOnlyOnce() throws Exception {
    try (var pool = java.util.concurrent.Executors.newFixedThreadPool(4)) {
      List<java.util.concurrent.Future<XpResult>> results = new ArrayList<>();
      for (int i = 0; i < 12; i++)
        results.add(pool.submit(() -> service.add(player, 100, "quest:uuid/event-1", false)));
      int fresh = 0;
      for (var result : results) if (!result.get().duplicate()) fresh++;
      assertEquals(1, fresh);
      assertEquals(100, service.progress("s1", player).xp);
    }
  }

  @Test
  void failedDiskWriteBlocksFurtherMutations() throws Exception {
    Files.delete(dir.resolve("state.json"));
    Files.createDirectory(dir.resolve("state.json"));
    assertThrows(IllegalStateException.class, () -> service.add(player, 100, null, false));
    assertEquals(0, service.progress("s1", player).xp);
    Files.delete(dir.resolve("state.json"));
    assertThrows(IllegalStateException.class, () -> service.add(player, 100, null, false));
  }

  @Test
  void incompleteOrNegativeStateFailsClosed() throws Exception {
    Files.writeString(dir.resolve("state.json"), "{\"schema\":1}");
    assertThrows(java.io.IOException.class, () -> new PassService(store, now::get));
    State state = service.snapshot();
    Progress p = new Progress();
    p.xp = -1;
    state.players.put(playerKey("s1", player), p);
    store.save(state);
    assertThrows(java.io.IOException.class, () -> new PassService(store, now::get));
  }

  @Test
  void newSeasonRewardsRequireActivation() {
    now.addAndGet(10000);
    service.create("s2", 10000, 5000, new Config());
    service.add(player, 100, null, false);
    assertTrue(service.claim(player, "s2", List.of("free:1"), new Random()).isEmpty());
    service.edit(state -> state.seasons.get("s2").rewards.get("free:1").enabled = true);
    assertEquals(1, service.claim(player, "s2", List.of("free:1"), new Random()).size());
  }

  @Test
  void earlyEndPreservesGraceAndHistory() {
    service.add(player, 100, null, false);
    now.addAndGet(500);
    service.end("s1");
    Season ended = service.snapshot().seasons.get("s1");
    assertEquals(now.get(), ended.end);
    assertEquals(now.get() + 5000, ended.graceEnd);
    assertThrows(IllegalArgumentException.class, () -> service.add(player, 100, null, false));
    assertEquals(1, service.claim(player, "s1", List.of("free:1"), new Random()).size());
    assertThrows(IllegalArgumentException.class, () -> service.end("s1"));
  }

  @Test
  void timedPremiumExpiresExactlyAtDeadlineAndBlocksClaims() {
    service.add(player, 100, null, false);
    long end = service.grantPremium(player, 1000, null);
    assertTrue(service.hasPremium("s1", player));
    now.set(end);
    assertFalse(service.hasPremium("s1", player));
    assertTrue(service.claim(player, "s1", List.of("premium:1"), new Random()).isEmpty());
  }

  @Test
  void timedPremiumUnlocksPremiumReward() {
    service.add(player, 100, null, false);
    service.grantPremium(player, 1000, null);
    assertEquals(1, service.claim(player, "s1", List.of("premium:1"), new Random()).size());
  }

  @Test
  void renewalAddsToRemainingDaysAndRestartsFromNowAfterExpiry() {
    long first = service.grantPremium(player, 1000, null);
    now.addAndGet(300);
    assertEquals(first + 1000, service.grantPremium(player, 1000, null));
    now.addAndGet(5000);
    assertEquals(now.get() + 1000, service.grantPremium(player, 1000, null));
  }

  @Test
  void paidPremiumSurvivesOfflineRestartAndSeasonChange() throws Exception {
    long until = service.grantPremium(player, 50000, "purchase/1");
    now.addAndGet(10000);
    service.create("s2", 10000, 1000, new Config());
    service = new PassService(store, now::get);
    assertTrue(service.hasPremium("s2", player));
    assertEquals(until, service.premiumExpiresAt(player));
  }

  @Test
  void paymentRetryDoesNotExtendAndCannotResurrectRevokedSubscription() {
    long until = service.grantPremium(player, 5000, "order:123");
    now.addAndGet(100);
    assertEquals(until, service.grantPremium(player, 5000, "order:123"));
    assertThrows(
        IllegalArgumentException.class, () -> service.grantPremium(player, 4000, "order:123"));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.grantPremium(UUID.randomUUID(), 5000, "order:123"));
    service.revokePremium(player);
    service.grantPremium(player, 5000, "order:123");
    assertFalse(service.hasPremium("s1", player));
  }

  @Test
  void timedGrantReplacesLegacyPermanentStatusAndFalseRevokesAll() {
    service.premium("s1", player, true);
    long until = service.grantPremium(player, 1000, null);
    now.set(until);
    assertFalse(service.hasPremium("s1", player));
    service.premium("s1", player, true);
    assertTrue(service.hasPremium("s1", player));
    service.revokePremium(player);
    assertFalse(service.hasPremium("s1", player));
  }

  @Test
  void subscriptionsCanBeGrantedAndRevokedBeforeAnySeason() throws Exception {
    PassService empty = new PassService(new Store(dir.resolve("empty.json")), now::get);
    empty.grantPremium(player, 1000, null);
    assertTrue(empty.hasPremium(null, player));
    empty.revokePremium(player);
    assertFalse(empty.hasPremium(null, player));
  }

  @Test
  void rejectsInvalidPremiumDurationsAndOverflowWithoutChangingAccess() {
    assertThrows(IllegalArgumentException.class, () -> service.grantPremium(player, 0, null));
    assertThrows(IllegalArgumentException.class, () -> service.grantPremium(player, -1, null));
    assertThrows(
        ArithmeticException.class, () -> service.grantPremium(player, Long.MAX_VALUE, null));
    assertEquals(0, service.premiumExpiresAt(player));
  }

  @Test
  void oldStateWithoutSubscriptionFieldsStillLoads() throws Exception {
    var json =
        com.google.gson.JsonParser.parseString(Files.readString(dir.resolve("state.json")))
            .getAsJsonObject();
    json.remove("premiumUntil");
    json.remove("premiumTransactions");
    Files.writeString(dir.resolve("state.json"), json.toString());
    PassService old = new PassService(store, now::get);
    assertFalse(old.hasPremium("s1", player));
  }
}
