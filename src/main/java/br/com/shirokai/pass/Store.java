package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;
import static java.nio.file.StandardOpenOption.*;

import com.google.gson.*;
import java.io.IOException;
import java.nio.file.*;

/**
 * A whole-state atomic snapshot; unknown/corrupt state fails closed. Never silently resets
 * progress.
 */
public final class Store {
  public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
  private final Path path;
  private final AtomicFiles.IO io;
  private boolean observed;
  private boolean initiallyMissing;

  public Store(Path path) {
    this(path, AtomicFiles.NIO);
  }

  Store(Path path, AtomicFiles.IO io) {
    this.path = AtomicFiles.absolute(path);
    this.io = io;
  }

  public Path path() {
    return path;
  }

  private Path markerPath() {
    return path.resolveSibling(path.getFileName() + ".created");
  }

  private boolean markerExists() throws IOException {
    AtomicFiles.Document marker = AtomicFiles.read(markerPath());
    if (marker == null) return false;
    try {
      JsonObject object = JsonParser.parseString(marker.text()).getAsJsonObject();
      if (object.get("shirokaipassStore").getAsInt() != 1
          || !path.getFileName().toString().equals(object.get("file").getAsString()))
        throw new IllegalArgumentException();
      return true;
    } catch (RuntimeException error) {
      throw new IOException(
          "Marcador de armazenamento desconhecido: " + markerPath() + ". Preservado para reparo.",
          error);
    }
  }

  private void ensureMarker() throws IOException {
    if (markerExists()) {
      AtomicFiles.complete(markerPath(), io);
      return;
    }
    JsonObject marker = new JsonObject();
    marker.addProperty("shirokaipassStore", 1);
    marker.addProperty("file", path.getFileName().toString());
    AtomicFiles.write(markerPath(), JSON.toJson(marker), io);
  }

  public State load() throws IOException {
    boolean marked = markerExists();
    AtomicFiles.Document candidate = AtomicFiles.read(path);
    if (candidate == null) {
      if (observed || marked && !initiallyMissing)
        throw new IOException(
            "O arquivo de dados existente desapareceu: "
                + path
                + ". Nenhum estado vazio foi criado.");
      initiallyMissing = true;
      return new State();
    }
    observed = true;
    try {
      JsonObject document = JsonParser.parseString(candidate.text()).getAsJsonObject();
      for (String field :
          java.util.List.of(
              "schema", "seasons", "players", "deliveries", "transactions", "names", "events"))
        if (!document.has(field) || document.get(field).isJsonNull())
          throw new IllegalStateException("Campo ausente: " + field);
      State state = JSON.fromJson(document, State.class);
      if (state == null
          || state.schema != 1
          || state.seasons == null
          || state.players == null
          || state.deliveries == null
          || state.transactions == null
          || state.names == null
          || state.events == null
          || state.premiumUntil == null
          || state.premiumTransactions == null) throw new IllegalStateException("Estado inválido");
      for (var entry : state.premiumUntil.entrySet()) {
        java.util.UUID.fromString(entry.getKey());
        if (entry.getValue() == null || entry.getValue() < 0)
          throw new IllegalStateException("Validade Premium inválida");
      }
      for (PremiumReceipt receipt : state.premiumTransactions.values()) {
        java.util.UUID.fromString(receipt.player);
        if (receipt.duration <= 0 || receipt.expires < 0)
          throw new IllegalStateException("Transação Premium inválida");
      }
      for (Season s : state.seasons.values()) {
        s.cap();
        for (Reward r : s.rewards.values()) r.validate();
      }
      if (state.active != null && !state.seasons.containsKey(state.active))
        throw new IllegalStateException("Temporada ativa desconhecida");
      for (var entry : state.players.entrySet()) {
        int separator = entry.getKey().lastIndexOf('/');
        if (separator <= 0) throw new IllegalStateException("Chave de jogador inválida");
        java.util.UUID.fromString(entry.getKey().substring(separator + 1));
        Season season = state.seasons.get(entry.getKey().substring(0, separator));
        Progress p = entry.getValue();
        if (season == null || p == null || p.claimed == null || p.xp < 0 || p.xp > season.cap())
          throw new IllegalStateException("Progresso inválido");
      }
      for (Delivery d : state.deliveries.values()) {
        if (d == null
            || d.id == null
            || d.prizes == null
            || d.prizes.isEmpty()
            || !java.util.Set.of("READY", "DELIVERING", "REVIEW", "DONE").contains(d.status))
          throw new IllegalStateException("Entrega inválida");
        java.util.UUID.fromString(d.player);
        if (!state.seasons.containsKey(d.season))
          throw new IllegalStateException("Entrega sem temporada");
        for (Choice c : d.prizes) c.validate();
      }
      for (XpEvent e : state.events)
        if (!Double.isFinite(e.multiplier)
            || e.multiplier < 1
            || e.multiplier > 100
            || e.end < e.start) throw new IllegalStateException("Evento inválido");
      if (candidate.journal()) AtomicFiles.complete(path, io);
      return state;
    } catch (RuntimeException e) {
      throw new IOException("Estado inválido: restaure um backup; não foi redefinido", e);
    }
  }

  public void save(State state) throws IOException {
    ensureMarker();
    AtomicFiles.write(path, JSON.toJson(state), io);
    observed = true;
  }

  public static String readText(Path path) throws IOException {
    AtomicFiles.Document document = AtomicFiles.read(path);
    return document == null ? null : document.text();
  }

  public static void finishPending(Path path) throws IOException {
    AtomicFiles.complete(path, AtomicFiles.NIO);
  }

  public static void write(Path path, String json) throws IOException {
    AtomicFiles.write(path, json, AtomicFiles.NIO);
  }
}
