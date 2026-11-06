package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;
import static java.nio.file.StandardOpenOption.*;

import com.google.gson.*;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/**
 * A whole-state atomic snapshot; unknown/corrupt state fails closed. Never silently resets
 * progress.
 */
public final class Store {
  public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
  private final Path path;

  public Store(Path path) {
    this.path = path;
  }

  public State load() throws IOException {
    if (!Files.exists(path)) return new State();
    try {
      JsonObject document = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
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
          || state.events == null) throw new IllegalStateException("Estado inválido");
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
      return state;
    } catch (RuntimeException e) {
      throw new IOException("Estado inválido: restaure um backup; não foi redefinido", e);
    }
  }

  public void save(State state) throws IOException {
    write(path, JSON.toJson(state));
  }

  public static void write(Path path, String json) throws IOException {
    Files.createDirectories(path.toAbsolutePath().getParent());
    Path temp = Files.createTempFile(path.toAbsolutePath().getParent(), ".shirokai-", ".tmp");
    try {
      byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
      try (FileChannel ch = FileChannel.open(temp, WRITE)) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.wrap(bytes);
        while (buffer.hasRemaining()) ch.write(buffer);
        ch.force(true);
      }
      Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      try (FileChannel directory = FileChannel.open(path.toAbsolutePath().getParent(), READ)) {
        directory.force(true);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }
}
