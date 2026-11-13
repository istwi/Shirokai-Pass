package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;

import java.util.*;
import java.util.function.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.*;

/**
 * All edits are server-side drafts. No reward configuration changes before explicit confirmation.
 */
final class AdminGui {
  static boolean allowed(ServerPlayerEntity p, String node) {
    return ShirokaiPass.permission(p.getCommandSource(), "shirokaipass.admin", 2)
        || ShirokaiPass.permission(p.getCommandSource(), "shirokaipass.admin." + node, 2);
  }

  static void require(ServerPlayerEntity p, String node) {
    if (!allowed(p, node)) throw new IllegalArgumentException("Sem permissão: " + node);
  }

  static void dashboard(ShirokaiPass mod, ServerPlayerEntity p) {
    if (List.of("rewards", "seasons", "events", "announcements", "preview", "premium").stream()
        .noneMatch(n -> allowed(p, n)))
      throw new IllegalArgumentException("Sem permissão administrativa");
    show(
        p,
        "✦ Administração",
        b -> {
          b.button(
              20,
              "minecraft:chest",
              "Editar recompensas",
              () -> {
                require(p, "rewards");
                Menus.open(mod, p, "editor", 0, null, true);
              });
          b.button(
              22,
              "minecraft:clock",
              "Temporadas",
              () -> {
                require(p, "seasons");
                seasons(mod, p, 0);
              });
          b.button(
              24,
              "minecraft:experience_bottle",
              "Eventos de XP",
              () -> {
                require(p, "events");
                event(mod, p, 2, 3600000);
              });
          b.button(
              30,
              "minecraft:bell",
              "Anúncios",
              () -> {
                require(p, "announcements");
                announcements(mod, p);
              });
          b.button(
              32,
              "minecraft:gold_ingot",
              "Informações Premium",
              () -> {
                require(p, "premium");
                text(
                    mod,
                    p,
                    "Informações Premium",
                    mod.config.premiumInfo,
                    1024,
                    "premium",
                    v -> config(mod, c -> c.premiumInfo = v),
                    () -> dashboard(mod, p));
              });
          b.button(
              40,
              "minecraft:spyglass",
              "Prévia do passe",
              () -> {
                require(p, "preview");
                Menus.open(mod, p, "rewards", -1, null, true);
              });
          b.button(
              49,
              "minecraft:arrow",
              "Voltar ao passe",
              () -> Menus.open(mod, p, "home", 0, null, false));
        });
  }

  private static void config(ShirokaiPass mod, Consumer<Config> edit) {
    Config next = Store.JSON.fromJson(Store.JSON.toJson(mod.config), Config.class);
    edit.accept(next);
    next.validate();
    try {
      Store.write(mod.configPath(), Store.JSON.toJson(next));
      mod.config = next;
    } catch (java.io.IOException e) {
      throw new IllegalStateException("Não foi possível salvar configuração", e);
    }
  }

  private static void text(
      ShirokaiPass mod,
      ServerPlayerEntity p,
      String title,
      String initial,
      int limit,
      String permission,
      Consumer<String> apply,
      Runnable back) {
    TextInput.open(
        p,
        title,
        initial,
        limit,
        v -> {
          require(p, permission);
          apply.accept(v);
          back.run();
        },
        back);
  }

  private static void announcements(ShirokaiPass mod, ServerPlayerEntity p) {
    show(
        p,
        "✦ Anúncios",
        b -> {
          require(p, "announcements");
          b.button(
              20,
              "minecraft:lever",
              "Anúncios: " + (mod.config.announcements ? "Ativos" : "Inativos"),
              () -> {
                require(p, "announcements");
                config(mod, c -> c.announcements = !c.announcements);
                announcements(mod, p);
              });
          b.button(
              24,
              "minecraft:name_tag",
              "Mensagem de nível",
              () ->
                  text(
                      mod,
                      p,
                      "Mensagem de nível",
                      mod.config.levelAnnouncement,
                      1024,
                      "announcements",
                      v -> config(mod, c -> c.levelAnnouncement = v),
                      () -> announcements(mod, p)),
              mod.config.levelAnnouncement);
          b.button(
              31,
              "minecraft:paper",
              "Regras do passe",
              () ->
                  text(
                      mod,
                      p,
                      "Regras do passe",
                      mod.config.rules,
                      2048,
                      "announcements",
                      v -> config(mod, c -> c.rules = v),
                      () -> announcements(mod, p)));
          b.button(49, "minecraft:arrow", "Voltar", () -> dashboard(mod, p));
        });
  }

  private static void event(ShirokaiPass mod, ServerPlayerEntity p, double factor, long duration) {
    show(
        p,
        "✦ Evento de XP",
        b -> {
          require(p, "events");
          b.button(
              13,
              "minecraft:clock",
              "Atualmente: " + mod.service.multiplier() + "x",
              () -> {},
              "Eventos anteriores: " + mod.service.snapshot().events.size());
          b.button(
              20,
              "minecraft:experience_bottle",
              "Multiplicador: " + factor + "x",
              () ->
                  TextInput.open(
                      p,
                      "Multiplicador",
                      Double.toString(factor),
                      16,
                      v -> {
                        require(p, "events");
                        double n = Double.parseDouble(v);
                        if (!Double.isFinite(n) || n < 1 || n > 100)
                          throw new IllegalArgumentException("Use 1 a 100");
                        event(mod, p, n, duration);
                      },
                      () -> event(mod, p, factor, duration)));
          b.button(
              24,
              "minecraft:clock",
              "Duração: " + duration / 60000 + " minutos",
              () ->
                  TextInput.open(
                      p,
                      "Duração (ex.: 2h)",
                      "1h",
                      16,
                      v -> {
                        require(p, "events");
                        event(mod, p, factor, Commands.duration(v));
                      },
                      () -> event(mod, p, factor, duration)));
          b.button(
              30,
              "minecraft:lime_dye",
              "Iniciar evento",
              () -> {
                require(p, "events");
                mod.service.startEvent(factor, duration);
                dashboard(mod, p);
              });
          b.button(
              32,
              "minecraft:red_dye",
              "Encerrar evento",
              () -> {
                require(p, "events");
                mod.service.stopEvent();
                dashboard(mod, p);
              });
          b.button(49, "minecraft:arrow", "Voltar", () -> dashboard(mod, p));
        });
  }

  private static void seasons(ShirokaiPass mod, ServerPlayerEntity p, int page) {
    show(
        p,
        "✦ Temporadas",
        b -> {
          require(p, "seasons");
          List<Season> list = new ArrayList<>(mod.service.snapshot().seasons.values());
          Collections.reverse(list);
          for (int i = 0; i < 28 && page * 28 + i < list.size(); i++) {
            Season s = list.get(page * 28 + i);
            b.button(
                (i / 7 + 1) * 9 + 1 + i % 7,
                "minecraft:book",
                s.name,
                () -> season(mod, p, s.id),
                s.id,
                "Fim: " + java.time.Instant.ofEpochMilli(s.end));
          }
          b.button(45, "minecraft:arrow", "Anterior", () -> seasons(mod, p, Math.max(0, page - 1)));
          b.button(
              48,
              "minecraft:emerald",
              "Nova temporada",
              () -> createSeason(mod, p, new NewSeason()));
          b.button(49, "minecraft:arrow", "Voltar", () -> dashboard(mod, p));
          b.button(
              53,
              "minecraft:arrow",
              "Próxima",
              () -> seasons(mod, p, Math.min(Math.max(0, (list.size() - 1) / 28), page + 1)));
        });
  }

  private static final class NewSeason {
    String id = "temporada_" + System.currentTimeMillis();
    long duration = 90L * 86400000, grace = 7L * 86400000;
  }

  private static void createSeason(ShirokaiPass mod, ServerPlayerEntity p, NewSeason d) {
    show(
        p,
        "✦ Nova temporada",
        b -> {
          require(p, "seasons");
          b.button(
              20,
              "minecraft:name_tag",
              "ID: " + d.id,
              () ->
                  text(
                      mod,
                      p,
                      "ID da temporada",
                      d.id,
                      50,
                      "seasons",
                      v -> {
                        if (!v.matches("[A-Za-z0-9_-]{1,50}"))
                          throw new IllegalArgumentException("Use letras, números, _ e -");
                        d.id = v;
                      },
                      () -> createSeason(mod, p, d)));
          b.button(
              22,
              "minecraft:clock",
              "Duração: " + d.duration / 86400000 + " dias",
              () ->
                  text(
                      mod,
                      p,
                      "Duração (ex.: 90d)",
                      "90d",
                      16,
                      "seasons",
                      v -> d.duration = Commands.duration(v),
                      () -> createSeason(mod, p, d)));
          b.button(
              24,
              "minecraft:hopper",
              "Resgate extra: " + d.grace / 86400000 + " dias",
              () ->
                  text(
                      mod,
                      p,
                      "Resgate extra (0 ou 7d)",
                      "7d",
                      16,
                      "seasons",
                      v -> d.grace = v.equals("0") ? 0 : Commands.duration(v),
                      () -> createSeason(mod, p, d)));
          b.button(
              40,
              "minecraft:lime_dye",
              "Confirmar criação",
              () -> {
                require(p, "seasons");
                mod.service.create(d.id, d.duration, d.grace, mod.config);
                season(mod, p, d.id);
              },
              "100 níveis; recompensas inicialmente desativadas.");
          b.button(49, "minecraft:arrow", "Cancelar", () -> seasons(mod, p, 0));
        });
  }

  private static void season(ShirokaiPass mod, ServerPlayerEntity p, String id) {
    show(
        p,
        "✦ Temporada",
        b -> {
          require(p, "seasons");
          Season season = Menus.current(mod, id);
          b.button(
              13,
              "minecraft:book",
              season.name,
              () -> {},
              "ID: " + id,
              "Fim: " + java.time.Instant.ofEpochMilli(season.end),
              "Resgate: " + java.time.Instant.ofEpochMilli(season.graceEnd));
          b.button(
              20,
              "minecraft:name_tag",
              "Alterar nome",
              () ->
                  text(
                      mod,
                      p,
                      "Nome da temporada",
                      season.name,
                      50,
                      "seasons",
                      v -> {
                        if (v.isBlank()) throw new IllegalArgumentException("Nome obrigatório");
                        mod.service.edit(s -> s.seasons.get(id).name = v);
                      },
                      () -> season(mod, p, id)));
          b.button(
              22,
              "minecraft:chest",
              "Recompensas",
              () -> {
                require(p, "rewards");
                Menus.open(mod, p, "editor", 0, id, true);
              });
          b.button(
              24,
              "minecraft:clock",
              "Alterar período extra",
              () ->
                  text(
                      mod,
                      p,
                      "Resgate extra (0 ou 7d)",
                      "7d",
                      16,
                      "seasons",
                      v -> {
                        long duration = v.equals("0") ? 0 : Commands.duration(v);
                        mod.service.edit(
                            s -> {
                              Season target = s.seasons.get(id);
                              target.graceEnd = Math.addExact(target.end, duration);
                            });
                      },
                      () -> season(mod, p, id)));
          b.button(
              40,
              "minecraft:red_dye",
              "Encerrar agora",
              () ->
                  show(
                      p,
                      "✦ Confirmar encerramento",
                      c -> {
                        c.button(
                            30,
                            "minecraft:lime_dye",
                            "Confirmar",
                            () -> {
                              require(p, "seasons");
                              mod.service.end(id);
                              seasons(mod, p, 0);
                            },
                            "Mantém o histórico e o período extra.");
                        c.button(32, "minecraft:arrow", "Cancelar", () -> season(mod, p, id));
                      }));
          b.button(49, "minecraft:arrow", "Voltar", () -> seasons(mod, p, 0));
        });
  }

  private static final class Draft {
    final String season;
    final int page;
    final Map<String, String> originals = new HashMap<>();
    final long expires = System.currentTimeMillis() + 900000;
    Reward reward;
    int from, to;
    boolean premium;

    Draft(Season season, String key, int page) {
      this.season = season.id;
      this.page = page;
      season.rewards.forEach((k, v) -> originals.put(k, Store.JSON.toJson(v)));
      reward = copy(season.rewards.get(key));
      from = to = reward.level;
      premium = reward.premium;
    }

    void check(ServerPlayerEntity p) {
      require(p, "rewards");
      if (System.currentTimeMillis() > expires)
        throw new IllegalArgumentException("Rascunho expirou. Reabra o editor.");
      if (from != to) require(p, "bulk");
    }
  }

  private static Reward copy(Reward r) {
    return Store.JSON.fromJson(Store.JSON.toJson(r), Reward.class);
  }

  static void editReward(
      ShirokaiPass mod, ServerPlayerEntity p, String season, String key, int page) {
    require(p, "rewards");
    edit(mod, p, new Draft(Menus.current(mod, season), key, page), 0);
  }

  private static void edit(ShirokaiPass mod, ServerPlayerEntity p, Draft d, int page) {
    show(
        p,
        "✦ Editar recompensa",
        b -> {
          d.check(p);
          b.button(
              4,
              "minecraft:comparator",
              "Modo: " + (d.reward.mode.equals("fixed") ? "Todos os prêmios" : "Sortear um prêmio"),
              () -> {
                d.check(p);
                d.reward.mode = d.reward.mode.equals("fixed") ? "random" : "fixed";
                edit(mod, p, d, page);
              });
          long total = 0;
          for (Choice c : d.reward.choices) total = Math.addExact(total, c.weight);
          final long sum = total;
          for (int i = 0; i < 28 && page * 28 + i < d.reward.choices.size(); i++) {
            int index = page * 28 + i;
            Choice c = d.reward.choices.get(index);
            String item =
                c.type.equals("item")
                    ? "minecraft:chest"
                    : c.type.equals("pokemon") ? "minecraft:dragon_egg" : "minecraft:command_block";
            ItemStack display = new ItemStack(Registries.ITEM.get(Identifier.of(item)));
            if (c.type.equals("pokemon")) {
              try {
                display = PokemonRewards.icon(c.value);
              } catch (IllegalArgumentException e) {
                // Invalid drafts remain editable, including without Cobblemon installed.
              }
            }
            b.button(
                (i / 7 + 1) * 9 + 1 + i % 7,
                display,
                "Prêmio " + (index + 1) + " • " + c.type,
                () -> choice(mod, p, d, index, page),
                c.value,
                "Quantidade: " + c.count,
                "Peso: " + c.weight,
                d.reward.mode.equals("random")
                    ? String.format(Locale.ROOT, "Chance: %.2f%%", 100.0 * c.weight / sum)
                    : "Entrega garantida");
          }
          b.button(
              45,
              "minecraft:arrow",
              "Página anterior",
              () -> edit(mod, p, d, Math.max(0, page - 1)));
          b.button(
              46,
              "minecraft:emerald",
              "Adicionar prêmio",
              () -> {
                d.check(p);
                if (d.reward.choices.size() >= 100)
                  throw new IllegalArgumentException("Limite de 100 prêmios por recompensa");
                d.reward.choices.add(new Choice());
                choice(mod, p, d, d.reward.choices.size() - 1, page);
              });
          b.button(
              47,
              "minecraft:bookshelf",
              "Intervalo: " + d.from + "–" + d.to,
              () -> {
                require(p, "bulk");
                range(mod, p, d);
              },
              "Trilha: " + (d.premium ? "Premium" : "Grátis"));
          b.button(
              48,
              "minecraft:lever",
              "Recompensa: " + (d.reward.enabled ? "Ativa" : "Desativada"),
              () -> {
                d.check(p);
                d.reward.enabled = !d.reward.enabled;
                edit(mod, p, d, page);
              });
          b.button(
              49,
              "minecraft:arrow",
              "Cancelar rascunho",
              () -> Menus.open(mod, p, "editor", d.page, d.season, true));
          b.button(50, "minecraft:lime_dye", "Revisar e salvar", () -> confirm(mod, p, d));
          b.button(
              53,
              "minecraft:arrow",
              "Próxima página",
              () ->
                  edit(
                      mod,
                      p,
                      d,
                      Math.min(Math.max(0, (d.reward.choices.size() - 1) / 28), page + 1)));
        });
  }

  private static void choice(ShirokaiPass mod, ServerPlayerEntity p, Draft d, int index, int page) {
    show(
        p,
        "✦ Prêmio " + (index + 1),
        b -> {
          d.check(p);
          Choice c = d.reward.choices.get(index);
          b.button(
              13,
              "minecraft:comparator",
              "Tipo: " + c.type,
              () -> {
                d.check(p);
                int next = (List.of("item", "command", "pokemon").indexOf(c.type) + 1) % 3;
                c.type = List.of("item", "command", "pokemon").get(next);
                c.count = 1;
                c.value =
                    switch (c.type) {
                      case "item" -> "minecraft:diamond";
                      case "pokemon" -> "pikachu level=10";
                      default -> "give {player} minecraft:diamond 1";
                    };
                choice(mod, p, d, index, page);
              });
          b.button(
              20,
              "minecraft:name_tag",
              "Conteúdo",
              () ->
                  TextInput.open(
                      p,
                      c.type.equals("item")
                          ? "Item e componentes"
                          : c.type.equals("pokemon")
                              ? "Propriedades do Pokémon"
                              : "Comando (sem barra)",
                      c.value,
                      c.value.length() > 50 ? 4096 : 50,
                      v -> {
                        d.check(p);
                        if (v.isBlank()) throw new IllegalArgumentException("Conteúdo obrigatório");
                        c.value = v;
                        choice(mod, p, d, index, page);
                      },
                      () -> choice(mod, p, d, index, page)),
              c.value);
          b.button(
              22,
              "minecraft:chest",
              "Quantidade: " + c.count,
              () ->
                  TextInput.open(
                      p,
                      "Quantidade",
                      Integer.toString(c.count),
                      8,
                      v -> {
                        d.check(p);
                        int n = Integer.parseInt(v);
                        if (n < 1 || n > 2304 || !c.type.equals("item") && n != 1)
                          throw new IllegalArgumentException("Itens: 1–2304. Comando/Pokémon: 1.");
                        c.count = n;
                        choice(mod, p, d, index, page);
                      },
                      () -> choice(mod, p, d, index, page)));
          b.button(
              24,
              "minecraft:gold_nugget",
              "Peso: " + c.weight,
              () ->
                  TextInput.open(
                      p,
                      "Peso positivo",
                      Long.toString(c.weight),
                      18,
                      v -> {
                        d.check(p);
                        long n = Long.parseLong(v);
                        if (n < 1 || n > 1_000_000_000L)
                          throw new IllegalArgumentException("Use 1 a 1000000000");
                        c.weight = n;
                        choice(mod, p, d, index, page);
                      },
                      () -> choice(mod, p, d, index, page)));
          b.button(
              29,
              "minecraft:writable_book",
              "Conteúdo longo",
              () ->
                  TextInput.open(
                      p,
                      "Novo conteúdo",
                      c.value,
                      4096,
                      v -> {
                        d.check(p);
                        if (v.isBlank()) throw new IllegalArgumentException("Conteúdo obrigatório");
                        c.value = v;
                        choice(mod, p, d, index, page);
                      },
                      () -> choice(mod, p, d, index, page)),
              "Para comandos e propriedades extensas.");
          b.button(
              30,
              "minecraft:paper",
              "Copiar item da mão",
              () -> {
                d.check(p);
                ItemStack stack = p.getMainHandStack();
                if (stack.isEmpty())
                  throw new IllegalArgumentException("Segure um item antes de abrir o editor");
                c.type = "item";
                c.value =
                    new net.minecraft.command.argument.ItemStackArgument(
                            stack.getRegistryEntry(), stack.getComponentChanges())
                        .asString(mod.server().getRegistryManager());
                c.count = stack.getCount();
                choice(mod, p, d, index, page);
              },
              "Preserva nome, encantamentos e componentes.");
          b.button(
              32,
              "minecraft:red_dye",
              "Remover prêmio",
              () -> {
                d.check(p);
                if (d.reward.choices.size() == 1)
                  throw new IllegalArgumentException("Mantenha um prêmio ou desative a recompensa");
                d.reward.choices.remove(index);
                edit(mod, p, d, page);
              });
          b.button(49, "minecraft:arrow", "Voltar ao rascunho", () -> edit(mod, p, d, page));
        });
  }

  private static void range(ShirokaiPass mod, ServerPlayerEntity p, Draft d) {
    show(
        p,
        "✦ Edição em lote",
        b -> {
          d.check(p);
          require(p, "bulk");
          b.button(
              20,
              "minecraft:arrow",
              "Do nível: " + d.from,
              () ->
                  TextInput.open(
                      p,
                      "Nível inicial",
                      Integer.toString(d.from),
                      3,
                      v -> {
                        d.check(p);
                        int n = Integer.parseInt(v);
                        if (n < 1 || n > 100) throw new IllegalArgumentException("Use 1–100");
                        d.from = n;
                        d.to = Math.max(n, d.to);
                        range(mod, p, d);
                      },
                      () -> range(mod, p, d)));
          b.button(
              24,
              "minecraft:arrow",
              "Até o nível: " + d.to,
              () ->
                  TextInput.open(
                      p,
                      "Nível final",
                      Integer.toString(d.to),
                      3,
                      v -> {
                        d.check(p);
                        int n = Integer.parseInt(v);
                        if (n < d.from || n > 100)
                          throw new IllegalArgumentException("Use " + d.from + "–100");
                        d.to = n;
                        range(mod, p, d);
                      },
                      () -> range(mod, p, d)));
          b.button(
              31,
              "minecraft:gold_ingot",
              "Trilha: " + (d.premium ? "Premium" : "Grátis"),
              () -> {
                d.check(p);
                d.premium = !d.premium;
                range(mod, p, d);
              });
          b.button(49, "minecraft:arrow", "Voltar", () -> edit(mod, p, d, 0));
        });
  }

  private static void confirm(ShirokaiPass mod, ServerPlayerEntity p, Draft d) {
    d.check(p);
    RewardValidation.check(mod.server(), d.reward);
    show(
        p,
        "✦ Confirmar recompensa",
        b -> {
          b.button(
              22,
              "minecraft:book",
              "Níveis " + d.from + "–" + d.to + " • " + (d.premium ? "Premium" : "Grátis"),
              () -> {},
              "Prêmios: " + d.reward.choices.size(),
              "Modo: " + d.reward.mode,
              "Ativa: " + d.reward.enabled,
              "Resgates anteriores serão preservados.");
          b.button(
              30,
              "minecraft:lime_dye",
              "Salvar alterações",
              () -> {
                d.check(p);
                RewardValidation.check(mod.server(), d.reward);
                mod.service.edit(
                    state -> {
                      Season target = state.seasons.get(d.season);
                      if (target == null) throw new IllegalArgumentException("Temporada removida");
                      for (int i = d.from; i <= d.to; i++) {
                        String key = key(i, d.premium);
                        if (!Objects.equals(
                            d.originals.get(key), Store.JSON.toJson(target.rewards.get(key))))
                          throw new IllegalArgumentException(
                              "Outro administrador alterou este intervalo. Reabra o editor.");
                      }
                      for (int i = d.from; i <= d.to; i++) {
                        Reward r = copy(d.reward);
                        r.level = i;
                        r.premium = d.premium;
                        r.validate();
                        target.rewards.put(key(i, d.premium), r);
                      }
                    });
                ShirokaiPass.LOG.info(
                    "Editor saved by={} season={} levels={}-{} premium={}",
                    p.getUuid(),
                    d.season,
                    d.from,
                    d.to,
                    d.premium);
                mod.server().getPlayerManager().getPlayerList().forEach(Menus::refresh);
                Menus.open(mod, p, "editor", d.page, d.season, true);
              });
          b.button(32, "minecraft:arrow", "Continuar editando", () -> edit(mod, p, d, 0));
        });
  }

  private static void show(ServerPlayerEntity p, String title, Consumer<Box> render) {
    TextInput.clear(p.getUuid());
    p.openHandledScreen(
        new SimpleNamedScreenHandlerFactory(
            (sync, inventory, player) -> new Box(sync, inventory, p, render),
            Text.literal(title).formatted(Formatting.AQUA)));
  }

  private static final class Box extends GenericContainerScreenHandler {
    private final SimpleInventory contents;
    private final ServerPlayerEntity owner;
    private final Map<Integer, Runnable> actions = new HashMap<>();

    Box(int sync, PlayerInventory inventory, ServerPlayerEntity p, Consumer<Box> render) {
      this(sync, inventory, p, render, new SimpleInventory(54));
    }

    Box(
        int sync,
        PlayerInventory inventory,
        ServerPlayerEntity p,
        Consumer<Box> render,
        SimpleInventory contents) {
      super(ScreenHandlerType.GENERIC_9X6, sync, inventory, contents, 6);
      this.contents = contents;
      owner = p;
      for (int i = 0; i < 54; i++) button(i, "minecraft:gray_stained_glass_pane", " ", () -> {});
      render.accept(this);
    }

    void button(int slot, String item, String label, Runnable action, String... lines) {
      Identifier id = Identifier.tryParse(item);
      ItemStack stack =
          new ItemStack(
              id != null && Registries.ITEM.containsId(id) ? Registries.ITEM.get(id) : Items.PAPER);
      button(slot, stack, label, action, lines);
    }

    void button(int slot, ItemStack stack, String label, Runnable action, String... lines) {
      stack = stack.copy();
      stack.set(
          DataComponentTypes.CUSTOM_NAME,
          Text.literal(label).formatted(Formatting.AQUA).styled(s -> s.withItalic(false)));
      List<Text> lore = new ArrayList<>();
      for (String line : lines)
        lore.add(Text.literal(line).formatted(Formatting.GRAY).styled(s -> s.withItalic(false)));
      stack.set(DataComponentTypes.LORE, new LoreComponent(lore));
      contents.setStack(slot, stack);
      actions.put(slot, action);
    }

    @Override
    public boolean canUse(PlayerEntity p) {
      return p == owner;
    }

    @Override
    public ItemStack quickMove(PlayerEntity p, int slot) {
      return ItemStack.EMPTY;
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType type, PlayerEntity p) {
      if (p != owner) return;
      try {
        if (type == SlotActionType.PICKUP && button == 0 && actions.containsKey(slot))
          actions.get(slot).run();
      } catch (RuntimeException e) {
        owner.sendMessage(
            Text.literal(e.getMessage() == null ? "Não foi possível editar" : e.getMessage())
                .formatted(Formatting.RED),
            true);
      }
      if (owner.currentScreenHandler == this) syncState();
    }
  }
}
