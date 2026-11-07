package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;

import java.io.IOException;
import java.math.*;
import java.util.*;
import java.util.function.*;

/**
 * Single progression authority for command and Java API. All mutations are serialized and durable.
 */
public final class PassService {
  private final Store store;
  private State state;
  private final LongSupplier clock;
  private boolean failed;
  private Throwable failure;
  private final Set<com.google.gson.JsonElement> recoveryCandidates = new HashSet<>();

  public PassService(Store store, LongSupplier clock) throws IOException {
    this.store = store;
    this.clock = clock;
    state = store.load();
  }

  private PassService(Store store, LongSupplier clock, boolean deferred) {
    this.store = store;
    this.clock = clock;
  }

  /** Startup facade: unreadable state disables the pass, never the Minecraft server/login. */
  public static PassService open(Store store, LongSupplier clock) {
    PassService service = new PassService(store, clock, true);
    try {
      service.state = store.load();
      service.recover();
    } catch (IOException | RuntimeException e) {
      service.block(e);
    }
    return service;
  }

  public synchronized boolean available() {
    return !failed && state != null;
  }

  public synchronized Throwable failure() {
    return failure;
  }

  public java.nio.file.Path storagePath() {
    return store.path();
  }

  private void readable() {
    if (state == null) throw unavailable();
  }

  public synchronized void requireWritable() {
    if (!available()) throw unavailable();
  }

  private IllegalStateException unavailable() {
    return new IllegalStateException(
        "Persistência do passe indisponível; repare o caminho/permissões e use /passeadmin storage"
            + " recover. Login continua liberado.",
        failure);
  }

  private void block(Throwable error) {
    failed = true;
    failure = error;
    if (state != null) recoveryCandidates.add(Store.JSON.toJsonTree(state));
  }

  /** Reload only a known committed/attempted snapshot, then prove that it is writable. */
  public synchronized void resume() throws IOException {
    if (available()) return;
    try {
      State candidate = store.load();
      if (state != null && !recoveryCandidates.contains(Store.JSON.toJsonTree(candidate)))
        throw new IOException(
            "Dados do disco diferem dos estados confirmados/tentados. Nada será sobrescrito: "
                + store.path());
      markInterrupted(candidate);
      recoveryCandidates.add(Store.JSON.toJsonTree(candidate));
      store.save(candidate); // A real durable write, not an exists()/isWritable() guess.
      state = candidate;
      failed = false;
      failure = null;
      recoveryCandidates.clear();
    } catch (IOException | RuntimeException e) {
      block(e);
      if (e instanceof IOException io) throw io;
      throw new IOException("Não foi possível recuperar armazenamento", e);
    }
  }

  public synchronized State snapshot() {
    readable();
    return copy(state);
  }

  private static State copy(State s) {
    return Store.JSON.fromJson(Store.JSON.toJson(s), State.class);
  }

  private <T> T change(Function<State, T> change) {
    requireWritable();
    State next = copy(state);
    T result = change.apply(next);
    try {
      store.save(next);
      state = next;
    } catch (IOException e) {
      recoveryCandidates.add(Store.JSON.toJsonTree(next));
      block(e);
      throw new IllegalStateException(
          "Não foi possível persistir "
              + store.path()
              + "; operação bloqueada. Login não é afetado.",
          e);
    }
    return result;
  }

  public synchronized void edit(Consumer<State> change) {
    change(
        s -> {
          change.accept(s);
          return null;
        });
  }

  public synchronized Season active() {
    readable();
    Season s = state.seasons.get(state.active);
    if (s == null || !s.earning(clock.getAsLong()))
      throw new IllegalArgumentException("Não há temporada ativa para XP");
    return Store.JSON.fromJson(Store.JSON.toJson(s), Season.class);
  }

  public synchronized void create(String id, long duration, long grace, Config config) {
    edit(
        s -> {
          if (s.seasons.containsKey(id)) throw new IllegalArgumentException("ID já utilizado");
          Season old = s.seasons.get(s.active);
          if (old != null && old.earning(clock.getAsLong()))
            throw new IllegalArgumentException("Encerre a temporada atual primeiro");
          Season season =
              new Season(id, clock.getAsLong(), duration, grace, config.baseXp, config.incrementXp);
          s.seasons.put(id, season);
          s.active = id;
        });
  }

  public synchronized void end(String id) {
    edit(
        s -> {
          Season season = s.seasons.get(id);
          long now = clock.getAsLong();
          if (season == null || !season.earning(now))
            throw new IllegalArgumentException("Temporada já encerrada ou inexistente");
          long grace = season.graceEnd - season.end;
          season.end = now;
          season.graceEnd = Math.addExact(now, grace);
        });
  }

  public synchronized void remember(UUID id, String name) {
    readable();
    if (!id.equals(state.names.get(name.toLowerCase(Locale.ROOT))))
      edit(s -> s.names.put(name.toLowerCase(Locale.ROOT), id));
  }

  public synchronized UUID known(String name) {
    readable();
    return state.names.get(name.toLowerCase(Locale.ROOT));
  }

  public synchronized Progress progress(String season, UUID id) {
    readable();
    Progress p = state.players.get(playerKey(season, id));
    return p == null ? new Progress() : Store.JSON.fromJson(Store.JSON.toJson(p), Progress.class);
  }

  private static Progress player(State s, String season, UUID id) {
    return s.players.computeIfAbsent(playerKey(season, id), k -> new Progress());
  }

  public synchronized double multiplier() {
    readable();
    return state.events.stream()
        .filter(e -> clock.getAsLong() >= e.start && clock.getAsLong() < e.end)
        .mapToDouble(e -> e.multiplier)
        .findFirst()
        .orElse(1);
  }

  public synchronized XpResult add(UUID id, long amount, String transaction, boolean bypass) {
    requireWritable();
    Objects.requireNonNull(id);
    if (amount <= 0) throw new IllegalArgumentException("XP deve ser positiva");
    if (transaction != null && !transaction.matches("[A-Za-z0-9_.:/-]{1,128}"))
      throw new IllegalArgumentException("Transação inválida");
    // Retry lookup precedes active-season lookup so a retry across season boundaries never awards
    // again.
    if (transaction != null && state.transactions.containsKey(transaction)) {
      Receipt r = state.transactions.get(transaction);
      if (!r.player.equals(id.toString()) || r.amount != amount || r.bypass != bypass)
        throw new IllegalArgumentException("Transação já usada com outros parâmetros");
      Season season = state.seasons.get(r.season);
      Progress p = progress(r.season, id);
      int lv = level(season, p.xp);
      return new XpResult(p.xp, p.xp, amount, 1, true, lv, lv);
    }
    Season season = active();
    double factor = bypass ? 1 : multiplier();
    long finalAmount =
        BigDecimal.valueOf(amount)
            .multiply(BigDecimal.valueOf(factor))
            .setScale(0, RoundingMode.DOWN)
            .longValueExact();
    return change(
        s -> {
          Progress p = player(s, season.id, id);
          long before = p.xp;
          long remaining = season.cap() - before;
          p.xp = before + Math.min(remaining, finalAmount);
          if (transaction != null) {
            Receipt r = new Receipt();
            r.player = id.toString();
            r.season = season.id;
            r.amount = amount;
            r.applied = p.xp - before;
            r.bypass = bypass;
            s.transactions.put(transaction, r);
          }
          return new XpResult(
              before, p.xp, amount, factor, false, level(season, before), level(season, p.xp));
        });
  }

  public synchronized long adjust(UUID id, String operation, long amount) {
    Objects.requireNonNull(id);
    if (amount < 0 || operation.equals("remove") && amount == 0)
      throw new IllegalArgumentException("Quantidade inválida");
    Season season = active();
    return change(
        s -> {
          Progress p = player(s, season.id, id);
          p.xp =
              switch (operation) {
                case "set" -> Math.min(amount, season.cap());
                case "remove" -> Math.max(0, p.xp - Math.min(p.xp, amount));
                case "reset" -> 0;
                default -> throw new IllegalArgumentException("Operação inválida");
              };
          return p.xp;
        });
  }

  public synchronized void premium(String season, UUID id, boolean value) {
    edit(
        s -> {
          if (!s.seasons.containsKey(season))
            throw new IllegalArgumentException("Temporada desconhecida");
          player(s, season, id).premium = value;
        });
  }

  private boolean hasPremium(State state, String season, UUID id) {
    Progress p = state.players.get(playerKey(season, id));
    return (p != null && p.premium)
        || state.premiumUntil.getOrDefault(id.toString(), 0L) > clock.getAsLong();
  }

  public synchronized boolean hasPremium(String season, UUID id) {
    readable();
    return hasPremium(state, season, id);
  }

  public synchronized boolean hasPremium(UUID id) {
    readable();
    return hasPremium(state, state.active, id);
  }

  public synchronized long premiumExpiresAt(UUID id) {
    readable();
    return state.premiumUntil.getOrDefault(id.toString(), 0L);
  }

  /**
   * Account subscription, renewed from its remaining expiry, independent of the selected season.
   */
  public synchronized long grantPremium(UUID id, long duration, String operation) {
    requireWritable();
    Objects.requireNonNull(id);
    if (duration <= 0) throw new IllegalArgumentException("Duração deve ser positiva");
    if (operation != null && !operation.matches("[A-Za-z0-9_.:/-]{1,128}"))
      throw new IllegalArgumentException("Transação inválida");
    PremiumReceipt existing = operation == null ? null : state.premiumTransactions.get(operation);
    if (existing != null) {
      if (!existing.player.equals(id.toString()) || existing.duration != duration)
        throw new IllegalArgumentException("Transação Premium já usada com outros parâmetros");
      return existing.expires;
    }
    long until = Math.addExact(Math.max(clock.getAsLong(), premiumExpiresAt(id)), duration);
    return change(
        s -> {
          s.premiumUntil.put(id.toString(), until);
          if (s.active != null) player(s, s.active, id).premium = false;
          if (operation != null) {
            PremiumReceipt receipt = new PremiumReceipt();
            receipt.player = id.toString();
            receipt.duration = duration;
            receipt.expires = until;
            s.premiumTransactions.put(operation, receipt);
          }
          return until;
        });
  }

  public synchronized void revokePremium(UUID id) {
    edit(
        s -> {
          s.premiumUntil.remove(id.toString());
          for (var entry : s.players.entrySet())
            if (entry.getKey().endsWith("/" + id)) entry.getValue().premium = false;
        });
  }

  public synchronized void startEvent(double multiplier, long duration) {
    if (!Double.isFinite(multiplier) || multiplier < 1 || multiplier > 100 || duration <= 0)
      throw new IllegalArgumentException("Evento inválido");
    edit(
        s -> {
          long now = clock.getAsLong();
          for (XpEvent e : s.events) if (e.end > now) e.end = now;
          XpEvent e = new XpEvent();
          e.multiplier = multiplier;
          e.start = now;
          e.end = Math.addExact(now, duration);
          s.events.add(e);
        });
  }

  public synchronized void stopEvent() {
    edit(
        s -> {
          long now = clock.getAsLong();
          for (XpEvent e : s.events) if (e.end > now) e.end = now;
        });
  }

  public synchronized List<Delivery> claim(
      UUID id, String seasonId, Collection<String> keys, Random random) {
    return change(
        s -> {
          Season season = s.seasons.get(seasonId);
          if (season == null || !season.claiming(clock.getAsLong()))
            throw new IllegalArgumentException("Prazo de resgate encerrado");
          Progress p = player(s, seasonId, id);
          List<Delivery> result = new ArrayList<>();
          for (String key : keys) {
            Reward r = season.rewards.get(key);
            if (r == null
                || !r.enabled
                || p.claimed.contains(key)
                || level(season, p.xp) < r.level
                || r.premium && !hasPremium(s, seasonId, id)) continue;
            r.validate();
            Delivery d = new Delivery();
            d.id = UUID.randomUUID().toString();
            d.player = id.toString();
            d.season = seasonId;
            d.reward = key;
            if (r.mode.equals("fixed")) d.prizes.addAll(r.choices);
            else {
              long total = 0;
              for (Choice c : r.choices) total = Math.addExact(total, c.weight);
              long roll = random.nextLong(total);
              for (Choice c : r.choices) {
                if (roll < c.weight) {
                  d.prizes.add(c);
                  break;
                }
                roll -= c.weight;
              }
            }
            p.claimed.add(key);
            s.deliveries.put(d.id, d);
            result.add(Store.JSON.fromJson(Store.JSON.toJson(d), Delivery.class));
          }
          return result;
        });
  }

  public synchronized void status(String id, String expected, String next, String error) {
    edit(
        s -> {
          Delivery d = s.deliveries.get(id);
          if (d == null || !d.status.equals(expected))
            throw new IllegalArgumentException("Estado da entrega mudou");
          d.status = next;
          d.error = error;
        });
  }

  private static void markInterrupted(State state) {
    state.deliveries.values().stream()
        .filter(d -> d.status.equals("DELIVERING"))
        .forEach(
            d -> {
              d.status = "REVIEW";
              d.error =
                  "Servidor/armazenamento interrompido durante entrega. Conferência manual"
                      + " necessária.";
            });
  }

  public synchronized void recover() {
    readable();
    if (state.deliveries.values().stream().anyMatch(d -> d.status.equals("DELIVERING")))
      edit(PassService::markInterrupted);
  }
}
