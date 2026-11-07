package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;

import java.nio.file.*;
import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.slf4j.*;

public final class ShirokaiPass implements ModInitializer {
  public static final Logger LOG = LoggerFactory.getLogger("shirokaipass");
  private static ShirokaiPass INSTANCE;
  public Config config = new Config();
  public PassService service;
  private MinecraftServer server;
  private final Map<UUID, Notice> notices = new HashMap<>();
  private int ticks;
  private long nextStorageRetry, lastStorageLog;
  private final NavigableMap<Long, Set<UUID>> premiumDeadlines = new TreeMap<>();

  private record Notice(long xp, double factor, int before, int after) {}

  public static ShirokaiPass instance() {
    if (INSTANCE == null || INSTANCE.server == null)
      throw new IllegalStateException("Servidor ainda não iniciou");
    return INSTANCE;
  }

  public MinecraftServer server() {
    if (server == null) throw new IllegalStateException("Servidor indisponível");
    return server;
  }

  public Path configPath() {
    return FabricLoader.getInstance().getConfigDir().resolve("shirokaipass.json");
  }

  public void reload() throws Exception {
    String document = Store.readText(configPath());
    Config candidate =
        document == null ? new Config() : Store.JSON.fromJson(document, Config.class);
    if (candidate == null) throw new IllegalArgumentException("Config vazia");
    candidate.validate();
    for (String icon : candidate.icons.values())
      if (!Registries.ITEM.containsId(Identifier.of(icon)))
        throw new IllegalArgumentException("Ícone desconhecido: " + icon);
    if (document == null) Store.write(configPath(), Store.JSON.toJson(candidate));
    else Store.finishPending(configPath());
    config = candidate;
  }

  @Override
  public void onInitialize() {
    INSTANCE = this;
    TextInput.register();
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) -> Commands.register(dispatcher, this));
    ServerLifecycleEvents.SERVER_STARTED.register(
        s -> {
          server = s;
          try {
            reload();
          } catch (Exception e) {
            LOG.error(
                "Configuração do passe indisponível; arquivo existente preservado. Use reload após"
                    + " reparar.",
                e);
          }
          service =
              PassService.open(
                  new Store(
                      s.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                          .resolve("shirokaipass/state.json")),
                  System::currentTimeMillis);
          if (service.available()) {
            rebuildPremiumDeadlines();
            LOG.info("Shirokai Pass iniciado: {} temporadas", service.snapshot().seasons.size());
          } else {
            nextStorageRetry = System.currentTimeMillis() + 30000;
            logStorageFailure(service.failure());
            LOG.warn(
                "Shirokai Pass iniciado em modo indisponível; entrada de jogadores permanece"
                    + " liberada.");
          }
        });
    ServerLifecycleEvents.SERVER_STOPPED.register(
        s -> {
          notices.clear();
          premiumDeadlines.clear();
          TextInput.clear();
          server = null;
        });
    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, s) -> {
          LoginGuard.run(
              () -> {
                if (service == null)
                  throw new IllegalStateException("Passe ainda não inicializado");
                service.requireWritable();
                service.remember(
                    handler.player.getUuid(), handler.player.getGameProfile().getName());
                deliver(handler.player);
              },
              error -> {
                logStorageFailure(error);
                handler.player.sendMessage(
                    Text.literal(
                        "✦ Passe temporariamente indisponível. Você pode continuar jogando."),
                    true);
              });
        });
    ServerPlayConnectionEvents.DISCONNECT.register(
        (handler, s) -> TextInput.clear(handler.player.getUuid()));
    ServerTickEvents.END_SERVER_TICK.register(
        s -> {
          if (++ticks % 20 == 0) {
            long now = System.currentTimeMillis();
            if (service == null) return;
            if (!service.available()) {
              if (now >= nextStorageRetry) recoverStorage();
              if (!service.available()) return;
            }
            for (ServerPlayerEntity player : s.getPlayerManager().getPlayerList())
              if (player.currentScreenHandler instanceof Menus.Menu)
                LoginGuard.run(() -> Menus.refresh(player), this::logStorageFailure);
            while (!premiumDeadlines.isEmpty() && premiumDeadlines.firstKey() <= now) {
              var expired = premiumDeadlines.pollFirstEntry();
              for (UUID id : expired.getValue())
                if (service.premiumExpiresAt(id) == expired.getKey()) {
                  ServerPlayerEntity online = s.getPlayerManager().getPlayer(id);
                  if (online != null) {
                    Menus.refresh(online);
                    if (!service.hasPremium(id))
                      online.sendMessage(
                          Text.literal("✦ A validade do seu Premium terminou."), true);
                  }
                }
            }
            var batch = new HashMap<>(notices);
            notices.clear();
            batch.forEach(
                (id, n) -> {
                  ServerPlayerEntity p = s.getPlayerManager().getPlayer(id);
                  if (p == null) return;
                  p.sendMessage(
                      Text.literal(
                          n.after > n.before
                              ? "✦ Você alcançou o nível " + n.after + " do passe!"
                              : "✦ +"
                                  + n.xp
                                  + " XP do passe"
                                  + (n.factor > 1 ? " (" + n.factor + "x)" : "")),
                      true);
                  if (n.after > n.before && config.announcements)
                    s.getPlayerManager()
                        .broadcast(Text.literal(expand(id, config.levelAnnouncement)), false);
                });
          }
        });
  }

  private void rebuildPremiumDeadlines() {
    premiumDeadlines.clear();
    service
        .snapshot()
        .premiumUntil
        .forEach(
            (id, until) -> {
              if (until > System.currentTimeMillis())
                premiumDeadlines
                    .computeIfAbsent(until, k -> new HashSet<>())
                    .add(UUID.fromString(id));
            });
  }

  private void logStorageFailure(Throwable error) {
    long now = System.currentTimeMillis();
    if (lastStorageLog == 0 || now - lastStorageLog >= 30000) {
      lastStorageLog = now;
      LOG.error(
          "Falha do Shirokai Pass em {}. Login preservado; alterações e entregas bloqueadas. Repare"
              + " permissões/caminhos/bloqueios e use /passeadmin storage recover.",
          service == null ? "inicialização" : service.storagePath(),
          error);
    }
  }

  public boolean recoverStorage() {
    nextStorageRetry = System.currentTimeMillis() + 30000;
    if (service == null) return false;
    try {
      service.resume();
      rebuildPremiumDeadlines();
      for (ServerPlayerEntity player : server().getPlayerManager().getPlayerList()) {
        LoginGuard.run(
            () -> {
              service.remember(player.getUuid(), player.getGameProfile().getName());
              deliver(player);
              Menus.refresh(player);
            },
            this::logStorageFailure);
      }
      if (!service.available()) return false;
      LOG.info("Armazenamento do passe recuperado com dados validados: {}", service.storagePath());
      return true;
    } catch (Exception error) {
      logStorageFailure(error);
      return false;
    }
  }

  public XpResult award(UUID id, long amount, String transaction, boolean bypass) {
    XpResult r = service.add(id, amount, transaction, bypass);
    if (!r.duplicate()) {
      LOG.info(
          "XP player={} original={} multiplier={} applied={} transaction={}",
          id,
          amount,
          r.multiplier(),
          r.after() - r.before(),
          transaction);
      ServerPlayerEntity p = server().getPlayerManager().getPlayer(id);
      if (p != null && r.after() > r.before()) {
        Notice old = notices.get(id);
        notices.put(
            id,
            new Notice(
                (old == null ? 0 : old.xp) + r.after() - r.before(),
                old == null ? r.multiplier() : old.factor == r.multiplier() ? old.factor : 1,
                old == null ? r.levelBefore() : old.before,
                r.levelAfter()));
        Menus.refresh(p);
      }
    }
    return r;
  }

  public String expand(UUID id, String text) {
    return expand(id, text, null);
  }

  private String expand(UUID id, String text, String seasonId) {
    State state = service.snapshot();
    Season s = state.seasons.get(seasonId == null ? state.active : seasonId);
    Progress p = s == null ? new Progress() : service.progress(s.id, id);
    ServerPlayerEntity online = server().getPlayerManager().getPlayer(id);
    return text.replace(
            "{player}", online == null ? id.toString() : online.getGameProfile().getName())
        .replace("{uuid}", id.toString())
        .replace("{level}", s == null ? "0" : Integer.toString(level(s, p.xp)))
        .replace("{xp}", Long.toString(p.xp))
        .replace("{season}", s == null ? "—" : s.name)
        .replace(
            "{premium}", service.hasPremium(s == null ? null : s.id, id) ? "Ativo" : "Inativo");
  }

  public void trackPremium(UUID id) {
    long until = service.premiumExpiresAt(id);
    if (until > System.currentTimeMillis())
      premiumDeadlines.computeIfAbsent(until, k -> new HashSet<>()).add(id);
  }

  public static boolean permission(ServerCommandSource source, String node, int fallback) {
    return PermissionGuard.check(
        () -> me.lucko.fabric.api.permissions.v0.Permissions.check(source, node, fallback),
        error -> LOG.error("Falha no provedor de permissões; acesso negado para {}", node, error));
  }

  public void claim(ServerPlayerEntity p, String season, Collection<String> keys) {
    service.claim(p.getUuid(), season, keys, new java.security.SecureRandom());
    deliver(p);
    Menus.refresh(p);
  }

  public void deliver(ServerPlayerEntity p) {
    service.requireWritable();
    for (Delivery d : service.snapshot().deliveries.values()) {
      if (!d.player.equals(p.getUuidAsString()) || !d.status.equals("READY")) continue;
      try {
        // Preflight all prizes before committing external side effects.
        int slots = 0;
        Map<Choice, String> prepared = new IdentityHashMap<>();
        for (Choice c : d.prizes) {
          c.validate();
          if (c.type.equals("item")) {
            ItemStack sample = RewardItems.stack(server(), c.value, 1);
            slots += (c.count + sample.getMaxCount() - 1) / sample.getMaxCount();
          }
          if (c.type.equals("pokemon") && !FabricLoader.getInstance().isModLoaded("cobblemon"))
            throw new IllegalArgumentException("Cobblemon não instalado");
          if (!c.type.equals("item")) {
            String command =
                c.type.equals("pokemon")
                    ? "pokegive " + p.getGameProfile().getName() + " " + c.value
                    : expand(p.getUuid(), c.value, d.season);
            if (command.startsWith("/")) command = command.substring(1);
            var parsed =
                server()
                    .getCommandManager()
                    .getDispatcher()
                    .parse(command, server().getCommandSource());
            if (parsed.getReader().canRead()
                || !parsed.getExceptions().isEmpty()
                || parsed.getContext().getNodes().isEmpty())
              throw new IllegalArgumentException("Comando de recompensa indisponível ou inválido");
            prepared.put(c, command);
          }
        }
        int empty = 0;
        for (int i = 0; i < 36; i++) if (p.getInventory().getStack(i).isEmpty()) empty++;
        if (empty < slots) {
          service.status(
              d.id, "READY", "READY", "Libere pelo menos " + slots + " slots no inventário");
          continue; // Conservative inventory check; keep READY with no partial item insertion.
        }
        service.status(d.id, "READY", "DELIVERING", "");
        for (Choice c : d.prizes) {
          switch (c.type) {
            case "item" -> {
              int remaining = c.count;
              while (remaining > 0) {
                ItemStack sample = RewardItems.stack(server(), c.value, 1);
                ItemStack stack = sample.copyWithCount(Math.min(remaining, sample.getMaxCount()));
                remaining -= stack.getCount();
                if (!p.getInventory().insertStack(stack) || !stack.isEmpty())
                  throw new IllegalStateException("Inventário mudou durante entrega");
              }
            }
            case "command", "pokemon" -> {
              String cmd = prepared.get(c);
              int outcome =
                  server()
                      .getCommandManager()
                      .getDispatcher()
                      .execute(
                          cmd.startsWith("/") ? cmd.substring(1) : cmd,
                          server().getCommandSource().withSilent());
              if (outcome <= 0) throw new IllegalStateException("Comando não confirmou sucesso");
            }
            default -> throw new IllegalArgumentException("Tipo desconhecido");
          }
        }
        p.getInventory().markDirty();
        server().getPlayerManager().savePlayerData(p);
        service.status(d.id, "DELIVERING", "DONE", "");
      } catch (Exception e) {
        if (!service.available()) {
          logStorageFailure(e);
          return;
        }
        Delivery current = service.snapshot().deliveries.get(d.id);
        if (current.status.equals("DELIVERING"))
          service.status(
              d.id,
              "DELIVERING",
              "REVIEW",
              e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        else if (current.status.equals("READY"))
          service.status(
              d.id,
              "READY",
              "READY",
              e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        LOG.warn("Entrega {} pendente: {}", d.id, e.toString());
      }
    }
  }
}
