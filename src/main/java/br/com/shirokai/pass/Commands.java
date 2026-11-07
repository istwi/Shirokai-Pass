package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;
import static net.minecraft.server.command.CommandManager.*;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.*;
import net.minecraft.server.command.*;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class Commands {
  private Commands() {}

  @FunctionalInterface
  interface Action {
    void run(CommandContext<ServerCommandSource> c) throws Exception;
  }

  private static int run(CommandContext<ServerCommandSource> c, Action a) {
    try {
      a.run(c);
      return 1;
    } catch (Exception e) {
      c.getSource()
          .sendError(Text.literal(e.getMessage() == null ? "Operação falhou" : e.getMessage()));
      return 0;
    }
  }

  private static void say(CommandContext<ServerCommandSource> c, String s) {
    c.getSource().sendFeedback(() -> Text.literal(s), false);
  }

  private static String str(CommandContext<ServerCommandSource> c, String n) {
    return StringArgumentType.getString(c, n);
  }

  private static UUID player(CommandContext<ServerCommandSource> c, ShirokaiPass mod) {
    String input = str(c, "player");
    try {
      return UUID.fromString(input);
    } catch (IllegalArgumentException ignored) {
    }
    ServerPlayerEntity online = mod.server().getPlayerManager().getPlayer(input);
    if (online != null) {
      mod.service.remember(online.getUuid(), online.getGameProfile().getName());
      return online.getUuid();
    }
    UUID id = mod.service.known(input);
    if (id == null)
      throw new IllegalArgumentException(
          "Nome offline desconhecido; use o UUID verificado ou aguarde o primeiro login");
    return id;
  }

  private static LiteralArgumentBuilder<ServerCommandSource> admin(String literal, String node) {
    return literal(literal)
        .requires(
            s ->
                ShirokaiPass.permission(s, "shirokaipass.admin", 2)
                    || ShirokaiPass.permission(s, "shirokaipass.admin." + node, 2));
  }

  static long duration(String input) {
    if (!input.matches("[1-9][0-9]*[smhd]"))
      throw new IllegalArgumentException("Duração: 30m, 2h, 7d ou 60s");
    long unit =
        switch (input.charAt(input.length() - 1)) {
          case 's' -> 1000;
          case 'm' -> 60000;
          case 'h' -> 3600000;
          default -> 86400000;
        };
    return Math.multiplyExact(Long.parseLong(input.substring(0, input.length() - 1)), unit);
  }

  public static void register(CommandDispatcher<ServerCommandSource> d, ShirokaiPass mod) {
    var pass =
        literal("passe")
            .requires(s -> ShirokaiPass.permission(s, "shirokaipass.use", 0))
            .executes(
                c ->
                    run(
                        c,
                        x ->
                            Menus.open(
                                mod, c.getSource().getPlayerOrThrow(), "home", -1, null, false)));
    for (String[] page :
        new String[][] {
          {"recompensas", "rewards"},
          {"perfil", "profile"},
          {"historico", "history"},
          {"premium", "premium"},
          {"pendentes", "pending"},
          {"info", "info"}
        })
      pass.then(
          literal(page[0])
              .executes(
                  c ->
                      run(
                          c,
                          x ->
                              Menus.open(
                                  mod,
                                  c.getSource().getPlayerOrThrow(),
                                  page[1],
                                  -1,
                                  null,
                                  false))));
    pass.then(
        literal("resgatartudo")
            .executes(
                c ->
                    run(
                        c,
                        x -> {
                          Season s = Menus.current(mod, null);
                          mod.claim(c.getSource().getPlayerOrThrow(), s.id, s.rewards.keySet());
                        })));
    d.register(pass);
    var root =
        literal("passeadmin")
            .executes(c -> run(c, x -> AdminGui.dashboard(mod, c.getSource().getPlayerOrThrow())));
    var xp = admin("xp", "xp");
    var add =
        literal("add")
            .then(
                argument("player", StringArgumentType.word())
                    .then(
                        argument("amount", LongArgumentType.longArg(1))
                            .executes(c -> award(c, mod, null, false))
                            .then(
                                argument("transaction", StringArgumentType.greedyString())
                                    .executes(c -> award(c, mod, str(c, "transaction"), false)))));
    xp.then(add);
    for (String operation : new String[] {"set", "remove"})
      xp.then(
          literal(operation)
              .then(
                  argument("player", StringArgumentType.word())
                      .then(
                          argument(
                                  "amount",
                                  LongArgumentType.longArg(operation.equals("set") ? 0 : 1))
                              .executes(
                                  c ->
                                      run(
                                          c,
                                          x -> {
                                            UUID id = player(c, mod);
                                            long value =
                                                mod.service.adjust(
                                                    id,
                                                    operation,
                                                    LongArgumentType.getLong(c, "amount"));
                                            refresh(mod, id);
                                            say(c, "XP: " + value);
                                          })))));
    xp.then(
        literal("reset")
            .then(
                argument("player", StringArgumentType.word())
                    .executes(
                        c ->
                            run(
                                c,
                                x -> {
                                  UUID id = player(c, mod);
                                  mod.service.adjust(id, "reset", 0);
                                  refresh(mod, id);
                                  say(c, "XP redefinida");
                                }))));
    xp.then(
        literal("get")
            .then(
                argument("player", StringArgumentType.word())
                    .executes(
                        c ->
                            run(
                                c,
                                x ->
                                    say(
                                        c,
                                        "XP: "
                                            + mod.service.progress(
                                                    mod.service.active().id, player(c, mod))
                                                .xp)))));
    root.then(xp);
    root.then(
        admin("level", "xp")
            .then(
                literal("get")
                    .then(
                        argument("player", StringArgumentType.word())
                            .executes(
                                c ->
                                    run(
                                        c,
                                        x -> {
                                          Season s = mod.service.active();
                                          say(
                                              c,
                                              "Nível: "
                                                  + level(
                                                      s,
                                                      mod.service.progress(s.id, player(c, mod))
                                                          .xp));
                                        }))))
            .then(
                literal("set")
                    .then(
                        argument("player", StringArgumentType.word())
                            .then(
                                argument("level", IntegerArgumentType.integer(0, 100))
                                    .executes(
                                        c ->
                                            run(
                                                c,
                                                x -> {
                                                  Season s = mod.service.active();
                                                  UUID id = player(c, mod);
                                                  mod.service.adjust(
                                                      id,
                                                      "set",
                                                      threshold(
                                                          IntegerArgumentType.getInteger(
                                                              c, "level"),
                                                          s.baseXp,
                                                          s.incrementXp));
                                                  refresh(mod, id);
                                                  say(c, "Nível atualizado");
                                                }))))));
    root.then(
        admin("premium", "premium")
            .then(
                argument("player", StringArgumentType.word())
                    .then(
                        argument("enabled", BoolArgumentType.bool())
                            .executes(
                                c ->
                                    run(
                                        c,
                                        x -> {
                                          UUID id = player(c, mod);
                                          boolean enabled = BoolArgumentType.getBool(c, "enabled");
                                          if (enabled)
                                            mod.service.premium(
                                                Menus.current(mod, null).id, id, true);
                                          else mod.service.revokePremium(id);
                                          mod.trackPremium(id);
                                          refresh(mod, id);
                                          say(
                                              c,
                                              enabled
                                                  ? "Premium ativo nesta temporada"
                                                  : "Premium removido");
                                        })))
                    .then(
                        literal("status")
                            .executes(
                                c ->
                                    run(
                                        c,
                                        x -> {
                                          UUID id = player(c, mod);
                                          State state = mod.service.snapshot();
                                          long until = mod.service.premiumExpiresAt(id);
                                          boolean permanent =
                                              state.active != null
                                                  && mod.service.progress(state.active, id).premium;
                                          say(
                                              c,
                                              permanent
                                                  ? "Premium permanente nesta temporada"
                                                  : mod.service.hasPremium(state.active, id)
                                                      ? "Premium até " + premiumDate(until)
                                                      : "Premium inativo");
                                        })))
                    .then(
                        argument("duration", IntegerArgumentType.integer(1, 36500))
                            .executes(c -> premiumTimed(c, mod, "dias", null))
                            .then(
                                argument("unit", StringArgumentType.word())
                                    .suggests(
                                        (c, builder) -> {
                                          for (String unit :
                                              List.of("dias", "horas", "minutos", "meses"))
                                            builder.suggest(unit);
                                          return builder.buildFuture();
                                        })
                                    .executes(c -> premiumTimed(c, mod, str(c, "unit"), null))
                                    .then(
                                        argument("transaction", StringArgumentType.greedyString())
                                            .executes(
                                                c ->
                                                    premiumTimed(
                                                        c,
                                                        mod,
                                                        str(c, "unit"),
                                                        str(c, "transaction"))))))));
    root.then(
        literal("event")
            .then(
                admin("xp", "events")
                    .then(
                        literal("start")
                            .then(
                                argument("multiplier", DoubleArgumentType.doubleArg(1, 100))
                                    .then(
                                        argument("duration", StringArgumentType.word())
                                            .executes(
                                                c ->
                                                    run(
                                                        c,
                                                        x -> {
                                                          mod.service.startEvent(
                                                              DoubleArgumentType.getDouble(
                                                                  c, "multiplier"),
                                                              duration(str(c, "duration")));
                                                          say(c, "Evento iniciado");
                                                        })))))
                    .then(
                        literal("stop")
                            .executes(
                                c ->
                                    run(
                                        c,
                                        x -> {
                                          mod.service.stopEvent();
                                          say(c, "Evento encerrado");
                                        })))
                    .then(
                        literal("status")
                            .executes(
                                c ->
                                    run(
                                        c,
                                        x ->
                                            say(
                                                c,
                                                "Multiplicador: "
                                                    + mod.service.multiplier()
                                                    + "x"))))
                    .then(
                        literal("list")
                            .executes(
                                c ->
                                    run(
                                        c,
                                        x -> {
                                          for (XpEvent e : mod.service.snapshot().events)
                                            say(
                                                c,
                                                e.multiplier
                                                    + "x: "
                                                    + java.time.Instant.ofEpochMilli(e.start)
                                                    + " → "
                                                    + java.time.Instant.ofEpochMilli(e.end));
                                        })))));
    root.then(
        admin("season", "seasons")
            .then(
                literal("create")
                    .then(
                        argument("id", StringArgumentType.word())
                            .then(
                                argument("duration", StringArgumentType.word())
                                    .then(
                                        argument("grace", StringArgumentType.word())
                                            .executes(
                                                c ->
                                                    run(
                                                        c,
                                                        x -> {
                                                          mod.service.create(
                                                              str(c, "id"),
                                                              duration(str(c, "duration")),
                                                              duration(str(c, "grace")),
                                                              mod.config);
                                                          say(
                                                              c,
                                                              "Temporada criada com 100 níveis;"
                                                                  + " recompensas desativadas;"
                                                                  + " revise os prêmios padrão"
                                                                  + " antes de liberar acesso");
                                                        }))))))
            .then(
                literal("end")
                    .executes(
                        c ->
                            run(
                                c,
                                x -> {
                                  Season current = mod.service.active();
                                  mod.service.end(current.id);
                                  say(c, "Temporada encerrada; período extra preservado");
                                })))
            .then(
                literal("list")
                    .executes(
                        c ->
                            run(
                                c,
                                x -> {
                                  for (Season s : mod.service.snapshot().seasons.values())
                                    say(
                                        c,
                                        s.id
                                            + " até "
                                            + java.time.Instant.ofEpochMilli(s.end)
                                            + ", resgate até "
                                            + java.time.Instant.ofEpochMilli(s.graceEnd));
                                }))));
    root.then(
        admin("preview", "preview")
            .executes(
                c ->
                    run(
                        c,
                        x ->
                            Menus.open(
                                mod,
                                c.getSource().getPlayerOrThrow(),
                                "rewards",
                                -1,
                                null,
                                true))));
    root.then(
        admin("editor", "rewards")
            .executes(
                c ->
                    run(
                        c,
                        x ->
                            Menus.open(
                                mod, c.getSource().getPlayerOrThrow(), "editor", 0, null, true))));
    root.then(
        admin("rewards", "rewards")
            .then(
                literal("set")
                    .then(
                        argument("season", StringArgumentType.word())
                            .then(
                                argument("level", IntegerArgumentType.integer(1, 100))
                                    .then(
                                        argument("premium", BoolArgumentType.bool())
                                            .then(
                                                argument("json", StringArgumentType.greedyString())
                                                    .executes(
                                                        c ->
                                                            run(
                                                                c,
                                                                x ->
                                                                    editRewards(
                                                                        c, mod, false)))))))));
    root.then(
        admin("bulk", "bulk")
            .then(
                argument("season", StringArgumentType.word())
                    .then(
                        argument("from", IntegerArgumentType.integer(1, 100))
                            .then(
                                argument("to", IntegerArgumentType.integer(1, 100))
                                    .then(
                                        argument("premium", BoolArgumentType.bool())
                                            .then(
                                                argument("json", StringArgumentType.greedyString())
                                                    .executes(
                                                        c ->
                                                            run(
                                                                c,
                                                                x ->
                                                                    editRewards(
                                                                        c, mod, true)))))))));
    root.then(
        admin("announcements", "announcements")
            .then(
                argument("enabled", BoolArgumentType.bool())
                    .executes(
                        c ->
                            run(
                                c,
                                x -> {
                                  Config next =
                                      Store.JSON.fromJson(
                                          Store.JSON.toJson(mod.config), Config.class);
                                  next.announcements = BoolArgumentType.getBool(c, "enabled");
                                  Store.write(mod.configPath(), Store.JSON.toJson(next));
                                  mod.config = next;
                                  say(c, "Anúncios atualizados");
                                }))));
    root.then(
        literal("reload")
            .requires(s -> ShirokaiPass.permission(s, "shirokaipass.admin", 2))
            .executes(
                c ->
                    run(
                        c,
                        x -> {
                          mod.reload();
                          if (!mod.service.available() && !mod.recoverStorage())
                            throw new IllegalStateException(
                                "Configuração recarregada; armazenamento ainda indisponível");
                          say(c, "Configuração recarregada");
                        })));
    // Resolving an uncertain command requires checking external side effects, never automatic
    // retry.
    root.then(
        admin("delivery", "rewards")
            .then(
                argument("id", StringArgumentType.word())
                    .then(
                        literal("acknowledge")
                            .executes(
                                c ->
                                    run(
                                        c,
                                        x -> {
                                          mod.service.status(
                                              str(c, "id"),
                                              "REVIEW",
                                              "DONE",
                                              "Conferida manualmente por "
                                                  + c.getSource().getName());
                                          say(c, "Entrega conferida e encerrada");
                                        })))));
    root.then(
        admin("gui", "rewards")
            .executes(c -> run(c, x -> AdminGui.dashboard(mod, c.getSource().getPlayerOrThrow()))));
    root.then(
        literal("storage")
            .requires(source -> ShirokaiPass.permission(source, "shirokaipass.admin", 2))
            .then(
                literal("status")
                    .executes(
                        c ->
                            run(
                                c,
                                x ->
                                    say(
                                        c,
                                        mod.service.available()
                                            ? "Armazenamento disponível: "
                                                + mod.service.storagePath()
                                            : "Armazenamento indisponível; login liberado: "
                                                + mod.service.storagePath()))))
            .then(
                literal("recover")
                    .executes(
                        c ->
                            run(
                                c,
                                x -> {
                                  if (!mod.recoverStorage())
                                    throw new IllegalStateException(
                                        "Armazenamento ainda indisponível. Consulte o console;"
                                            + " nenhum dado foi redefinido.");
                                  say(
                                      c,
                                      "Armazenamento recuperado; dados validados e gravação"
                                          + " confirmada");
                                }))));
    d.register(root);
  }

  private static int premiumTimed(
      CommandContext<ServerCommandSource> c, ShirokaiPass mod, String unit, String transaction) {
    return run(
        c,
        x -> {
          long duration = premiumDuration(IntegerArgumentType.getInteger(c, "duration"), unit);
          UUID id = player(c, mod);
          boolean replay =
              transaction != null
                  && mod.service.snapshot().premiumTransactions.containsKey(transaction);
          long until = mod.service.grantPremium(id, duration, transaction);
          mod.trackPremium(id);
          refresh(mod, id);
          say(
              c,
              replay
                  ? "Pedido já processado; validade não alterada."
                  : "Premium válido até " + premiumDate(until));
          ShirokaiPass.LOG.info(
              "Premium player={} duration={} expires={} transaction={}",
              id,
              duration,
              until,
              transaction);
        });
  }

  static long premiumDuration(int amount, String unit) {
    if (amount <= 0) throw new IllegalArgumentException("Duração deve ser positiva");
    long factor =
        switch (unit.toLowerCase(java.util.Locale.ROOT)) {
          case "dia", "dias", "d", "day", "days" -> 86400000L;
          case "hora", "horas", "h" -> 3600000L;
          case "minuto", "minutos", "m" -> 60000L;
          case "mes", "meses" -> 30L * 86400000;
          default ->
              throw new IllegalArgumentException(
                  "Unidade: dias, horas, minutos ou meses (30 dias)");
        };
    return Math.multiplyExact(amount, factor);
  }

  private static int award(
      CommandContext<ServerCommandSource> c, ShirokaiPass mod, String transaction, boolean bypass) {
    return run(
        c,
        x -> {
          String id = transaction;
          boolean ignore = bypass;
          if (id != null && (id.equals("bypass") || id.endsWith(" bypass"))) {
            ignore = true;
            id = id.equals("bypass") ? null : id.substring(0, id.length() - 7);
          }
          if (ignore && !ShirokaiPass.permission(c.getSource(), "shirokaipass.admin.xp.bypass", 4))
            throw new IllegalArgumentException("Sem permissão para ignorar multiplicador");
          XpResult r = mod.award(player(c, mod), LongArgumentType.getLong(c, "amount"), id, ignore);
          say(
              c,
              r.duplicate()
                  ? "Transação já processada"
                  : "XP aplicada: " + (r.after() - r.before()) + " (total " + r.after() + ")");
        });
  }

  private static void refresh(ShirokaiPass mod, UUID id) {
    ServerPlayerEntity p = mod.server().getPlayerManager().getPlayer(id);
    if (p != null) Menus.refresh(p);
  }

  private static void editRewards(
      CommandContext<ServerCommandSource> c, ShirokaiPass mod, boolean bulk) {
    String season = str(c, "season");
    int from = IntegerArgumentType.getInteger(c, bulk ? "from" : "level"),
        to = bulk ? IntegerArgumentType.getInteger(c, "to") : from;
    boolean premium = BoolArgumentType.getBool(c, "premium");
    if (from > to) throw new IllegalArgumentException("Intervalo invertido");
    String json = str(c, "json");
    mod.service.edit(
        s -> {
          Season ss = s.seasons.get(season);
          if (ss == null) throw new IllegalArgumentException("Temporada desconhecida");
          for (int i = from; i <= to; i++) {
            Reward r = Store.JSON.fromJson(json, Reward.class);
            if (r == null) throw new IllegalArgumentException("JSON vazio");
            r.level = i;
            r.premium = premium;
            RewardValidation.check(mod.server(), r);
            ss.rewards.put(key(i, premium), r);
          }
        });
    say(c, "Recompensas atualizadas; resgates anteriores preservados");
  }
}
