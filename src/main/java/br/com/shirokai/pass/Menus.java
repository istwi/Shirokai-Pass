package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;

import java.util.*;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.component.type.ProfileComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.*;
import net.minecraft.registry.Registries;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.*;
import net.minecraft.util.*;

public final class Menus {
  private Menus() {}

  static Season current(ShirokaiPass mod, String id) {
    State s = mod.service.snapshot();
    Season season = s.seasons.get(id == null ? s.active : id);
    if (season == null)
      throw new IllegalArgumentException("A equipe ainda não iniciou uma temporada");
    return season;
  }

  public static void open(
      ShirokaiPass mod,
      ServerPlayerEntity p,
      String view,
      int page,
      String season,
      boolean preview) {
    TextInput.clear(p.getUuid());
    Season s = null;
    try {
      s = current(mod, season);
    } catch (IllegalArgumentException ignored) {
    }
    if (page < 0)
      page =
          s == null
              ? 0
              : Math.min(
                  14,
                  Math.max(
                      0,
                      (Math.max(1, level(s, mod.service.progress(s.id, p.getUuid()).xp)) - 1) / 7));
    final int initial = page;
    p.openHandledScreen(
        new SimpleNamedScreenHandlerFactory(
            (sync, inventory, player) ->
                new Menu(sync, inventory, mod, p, view, initial, season, preview),
            Text.literal("✦ SHIROKAI PASS").formatted(Formatting.AQUA)));
  }

  public static void refresh(ServerPlayerEntity p) {
    if (p.currentScreenHandler instanceof Menu m) {
      m.render();
      m.sendContentUpdates();
    }
  }

  private static ItemStack icon(String id, String name, Formatting color, String... lore) {
    Identifier identifier = Identifier.tryParse(id);
    Item item =
        identifier != null && Registries.ITEM.containsId(identifier)
            ? Registries.ITEM.get(identifier)
            : Items.PAPER;
    ItemStack stack = new ItemStack(item);
    stack.set(
        DataComponentTypes.CUSTOM_NAME,
        Text.literal(name).formatted(color).styled(s -> s.withItalic(false)));
    List<Text> lines = new ArrayList<>();
    for (String line : lore)
      lines.add(Text.literal(line).formatted(Formatting.GRAY).styled(s -> s.withItalic(false)));
    stack.set(DataComponentTypes.LORE, new LoreComponent(lines));
    return stack;
  }

  static final class Menu extends GenericContainerScreenHandler {
    private final ShirokaiPass mod;
    private final ServerPlayerEntity owner;
    private final SimpleInventory contents;
    private String view, season;
    private int page;
    private final boolean preview;
    private final Map<Integer, Runnable> actions = new HashMap<>();

    Menu(
        int sync,
        PlayerInventory inventory,
        ShirokaiPass mod,
        ServerPlayerEntity owner,
        String view,
        int page,
        String season,
        boolean preview) {
      this(sync, inventory, mod, owner, view, page, season, preview, new SimpleInventory(54));
    }

    private Menu(
        int sync,
        PlayerInventory inventory,
        ShirokaiPass mod,
        ServerPlayerEntity owner,
        String view,
        int page,
        String season,
        boolean preview,
        SimpleInventory contents) {
      super(ScreenHandlerType.GENERIC_9X6, sync, inventory, contents, 6);
      this.mod = mod;
      this.owner = owner;
      this.view = view;
      this.page = page;
      this.season = season;
      this.preview = preview;
      this.contents = contents;
      render();
    }

    @Override
    public boolean canUse(PlayerEntity player) {
      return player == owner;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
      return ItemStack.EMPTY;
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType type, PlayerEntity player) {
      if (player != owner) return;
      if (type != SlotActionType.PICKUP || button != 0) {
        syncState();
        return;
      }
      Runnable action = actions.get(slot);
      if (action == null) {
        syncState();
        return;
      }
      try {
        action.run();
        if (owner.currentScreenHandler == this) {
          render();
          syncState();
        }
      } catch (Exception e) {
        owner.sendMessage(
            Text.literal(e.getMessage() == null ? "Operação indisponível" : e.getMessage())
                .formatted(Formatting.RED),
            true);
      }
    }

    private void put(int slot, ItemStack stack, Runnable action) {
      contents.setStack(slot, stack);
      if (action != null) actions.put(slot, action);
    }

    private void go(String view, int page, String season) {
      this.view = view;
      this.page = page;
      this.season = season;
    }

    private void button(int slot, String item, String label, Runnable action, String... lore) {
      put(slot, icon(item, label, Formatting.AQUA, lore), action);
    }

    private void mainButton(int slot, String name, String label, String target) {
      put(
          slot,
          icon(
              mod.config.icons.getOrDefault(name, "minecraft:paper"),
              label,
              name.equals("premium") ? Formatting.GOLD : Formatting.AQUA),
          () -> go(target, 0, null));
    }

    private ItemStack head(Season s, Progress p) {
      boolean premiumActive = mod.service.hasPremium(s == null ? null : s.id, owner.getUuid());
      int lv = s == null ? 0 : level(s, p.xp);
      long floor = s == null ? 0 : threshold(lv, s.baseXp, s.incrementXp);
      long needed = s == null || lv == 100 ? 0 : threshold(lv + 1, s.baseXp, s.incrementXp) - floor;
      String percent =
          needed == 0
              ? (lv == 100 ? "100" : "0")
              : String.format(Locale.ROOT, "%.1f", 100.0 * (p.xp - floor) / needed);
      ItemStack h =
          icon(
              "minecraft:player_head",
              owner.getGameProfile().getName(),
              Formatting.AQUA,
              "Nível: " + lv + " / 100",
              "XP total: " + p.xp,
              "XP do nível: " + (p.xp - floor) + " / " + needed,
              "Próximo nível: faltam " + Math.max(0, needed - (p.xp - floor)) + " XP",
              "Progresso: " + percent + "%",
              "Premium: " + (premiumActive ? "Ativo" : "Inativo"),
              "Validade: "
                  + (p.premium
                      ? "Permanente nesta temporada"
                      : premiumActive
                          ? remaining(mod.service.premiumExpiresAt(owner.getUuid()))
                          : "—"),
              "Temporada: " + (s == null ? "—" : s.name),
              "Restante: " + (s == null ? "—" : remaining(s.end)));
      h.set(DataComponentTypes.PROFILE, new ProfileComponent(owner.getGameProfile()));
      return h;
    }

    private static String remaining(long deadline) {
      long sec = Math.max(0, (deadline - System.currentTimeMillis()) / 1000);
      return sec / 86400 + "d " + sec % 86400 / 3600 + "h " + sec % 3600 / 60 + "m";
    }

    void render() {
      actions.clear();
      contents.clear();
      for (int i = 0; i < 54; i++)
        contents.setStack(i, icon("minecraft:gray_stained_glass_pane", " ", Formatting.DARK_GRAY));
      State state = mod.service.snapshot();
      Season s = state.seasons.get(season == null ? state.active : season);
      Progress p = s == null ? new Progress() : mod.service.progress(s.id, owner.getUuid());
      boolean premiumActive = mod.service.hasPremium(s == null ? null : s.id, owner.getUuid());
      if (!view.equals("home"))
        button(49, "minecraft:arrow", "VOLTAR AO MENU", () -> go("home", 0, null));
      switch (view) {
        case "home" -> {
          put(13, head(s, p), null);
          mainButton(28, "rewards", "✦ Recompensas", "rewards");
          actions.put(
              28,
              () -> {
                int current =
                    s == null
                        ? 0
                        : Math.min(14, Math.max(0, (Math.max(1, level(s, p.xp)) - 1) / 7));
                go("rewards", current, null);
              });
          mainButton(30, "profile", "✦ Perfil", "profile");
          mainButton(32, "premium", "✦ Premium", "premium");
          mainButton(34, "history", "✦ Histórico", "history");
          button(
              39,
              mod.config.icons.getOrDefault("claim", "minecraft:lime_dye"),
              "✦ Resgatar tudo",
              () -> {
                if (s != null && !preview) mod.claim(owner, s.id, s.rewards.keySet());
              });
          mainButton(41, "info", "✦ Informações", "info");
          mainButton(43, "pending", "✦ Entregas pendentes", "pending");
          if (java.util.List.of(
                  "rewards", "seasons", "events", "announcements", "premium", "preview")
              .stream()
              .anyMatch(n -> AdminGui.allowed(owner, n)))
            button(
                48, "minecraft:comparator", "Administração", () -> AdminGui.dashboard(mod, owner));
          if (s == null)
            put(22, icon("minecraft:clock", "Aguardando temporada", Formatting.GRAY), null);
        }
        case "profile" -> {
          put(13, head(s, p), null);
          long free = p.claimed.stream().filter(k -> k.startsWith("free:")).count(),
              premium = p.claimed.size() - free;
          long available =
              s == null
                  ? 0
                  : s.rewards.entrySet().stream()
                      .filter(
                          e ->
                              e.getValue().enabled
                                  && s.claiming(System.currentTimeMillis())
                                  && !p.claimed.contains(e.getKey())
                                  && level(s, p.xp) >= e.getValue().level
                                  && (!e.getValue().premium || premiumActive))
                      .count();
          long pending =
              state.deliveries.values().stream()
                  .filter(d -> d.player.equals(owner.getUuidAsString()) && !d.status.equals("DONE"))
                  .count();
          int best = 0, participation = 0;
          for (Season past : state.seasons.values()) {
            Progress previous = state.players.get(playerKey(past.id, owner.getUuid()));
            if (previous != null) {
              participation++;
              if (s == null || !past.id.equals(s.id))
                best = Math.max(best, level(past, previous.xp));
            }
          }
          put(
              30,
              icon(
                  "minecraft:chest",
                  "Recompensas",
                  Formatting.GREEN,
                  "Grátis resgatadas: " + free,
                  "Premium resgatadas: " + premium,
                  "Disponíveis: " + available,
                  "Pendentes: " + pending),
              null);
          put(
              32,
              icon(
                  "minecraft:book",
                  "Histórico",
                  Formatting.AQUA,
                  "Temporadas participadas: " + participation,
                  "Maior nível anterior: " + best),
              null);
        }
        case "premium" ->
            put(
                22,
                icon(
                    "minecraft:gold_ingot",
                    "✦ Premium",
                    Formatting.GOLD,
                    "Status: " + (premiumActive ? "Ativo" : "Inativo"),
                    "Validade: "
                        + (p.premium
                            ? "Permanente nesta temporada"
                            : premiumActive
                                ? remaining(mod.service.premiumExpiresAt(owner.getUuid()))
                                : "—"),
                    mod.config.premiumInfo),
                null);
        case "info" ->
            put(
                22,
                icon(
                    "minecraft:paper",
                    "Como funciona",
                    Formatting.AQUA,
                    mod.config.rules,
                    "100 níveis • XP progressiva",
                    "Bônus de XP atual: " + mod.service.multiplier() + "x",
                    "Resgate extra: " + (s == null ? "—" : remaining(s.graceEnd))),
                null);
        case "history" -> {
          List<Season> past = new ArrayList<>(state.seasons.values());
          Collections.reverse(past);
          int max = Math.max(0, (past.size() - 1) / 28);
          page = Math.min(max, Math.max(0, page));
          for (int i = 0; i < 28 && page * 28 + i < past.size(); i++) {
            Season previous = past.get(page * 28 + i);
            Progress old = mod.service.progress(previous.id, owner.getUuid());
            int slot = (i / 7 + 1) * 9 + 1 + i % 7;
            button(
                slot,
                "minecraft:book",
                previous.name,
                () ->
                    go(
                        "rewards",
                        Math.min(14, Math.max(0, (Math.max(1, level(previous, old.xp)) - 1) / 7)),
                        previous.id),
                "Nível: " + level(previous, old.xp),
                "XP: " + old.xp,
                "Resgate: "
                    + (previous.claiming(System.currentTimeMillis())
                        ? remaining(previous.graceEnd)
                        : "Encerrado"));
          }
          nav(max);
        }
        case "pending" -> {
          List<Delivery> list =
              state.deliveries.values().stream()
                  .filter(d -> d.player.equals(owner.getUuidAsString()) && !d.status.equals("DONE"))
                  .toList();
          int max = Math.max(0, (list.size() - 1) / 28);
          page = Math.min(max, Math.max(0, page));
          for (int i = 0; i < 28 && page * 28 + i < list.size(); i++) {
            Delivery delivery = list.get(page * 28 + i);
            put(
                (i / 7 + 1) * 9 + 1 + i % 7,
                icon(
                    "minecraft:hopper",
                    delivery.reward,
                    Formatting.GOLD,
                    delivery.season,
                    "Estado: " + delivery.status,
                    "ID: " + delivery.id,
                    delivery.error),
                null);
          }
          button(
              48,
              "minecraft:lime_dye",
              "Tentar entregas prontas",
              () -> mod.deliver(owner),
              "Libere espaço no inventário.",
              "Entregas em revisão exigem a equipe.");
          nav(max);
        }
        case "rewards", "editor" -> {
          if (s == null) {
            put(22, icon("minecraft:clock", "Aguardando temporada", Formatting.GRAY), null);
            break;
          }
          page = Math.min(14, Math.max(0, page));
          put(
              4,
              icon(
                  "minecraft:experience_bottle",
                  s.name + " • " + (page + 1) + " / 15",
                  Formatting.AQUA,
                  preview ? "Prévia: resgates desativados" : "Nível atual: " + level(s, p.xp)),
              null);
          for (int col = 0; col < 7; col++) {
            int lv = page * 7 + col + 1;
            if (lv > 100) continue;
            put(
                19 + col,
                icon("minecraft:cyan_stained_glass_pane", "Nível " + lv, Formatting.AQUA),
                null);
            for (boolean premium : new boolean[] {false, true}) {
              String key = key(lv, premium);
              Reward r = s.rewards.get(key);
              if (r == null) continue;
              boolean claimed = p.claimed.contains(key),
                  eligible =
                      r.enabled
                          && level(s, p.xp) >= lv
                          && (!premium || premiumActive)
                          && s.claiming(System.currentTimeMillis());
              List<String> lore = new ArrayList<>();
              lore.add(
                  !r.enabled
                      ? "Não configurada"
                      : claimed ? "Resgatada" : eligible ? "Disponível" : "Bloqueada");
              long total = r.choices.stream().mapToLong(c -> c.weight).sum();
              for (Choice c : r.choices)
                lore.add(
                    c.type
                        + ": "
                        + c.value
                        + " ×"
                        + c.count
                        + (r.mode.equals("random")
                            ? String.format(Locale.ROOT, " (%.2f%%)", 100.0 * c.weight / total)
                            : ""));
              if (view.equals("editor")) lore.add("Clique para editar");
              int slot = (premium ? 28 : 10) + col;
              Formatting color =
                  claimed
                      ? Formatting.GRAY
                      : eligible ? Formatting.GREEN : premium ? Formatting.GOLD : Formatting.AQUA;
              put(
                  slot,
                  icon(
                      claimed
                          ? "minecraft:gray_dye"
                          : eligible
                              ? "minecraft:lime_dye"
                              : premium ? "minecraft:gold_ingot" : "minecraft:chest",
                      (premium ? "Premium" : "Grátis") + " • " + lv,
                      color,
                      lore.toArray(String[]::new)),
                  () -> {
                    if (view.equals("editor")) {
                      if (!ShirokaiPass.permission(
                              owner.getCommandSource(), "shirokaipass.admin.rewards", 2)
                          && !ShirokaiPass.permission(
                              owner.getCommandSource(), "shirokaipass.admin", 2))
                        throw new IllegalArgumentException("Sem permissão");
                      AdminGui.editReward(mod, owner, s.id, key, page);
                    } else if (!preview) mod.claim(owner, s.id, List.of(key));
                  });
            }
          }
          nav(14);
          button(
              47,
              "minecraft:compass",
              "Ir ao nível atual",
              () -> page = Math.min(14, Math.max(0, (Math.max(1, level(s, p.xp)) - 1) / 7)));
          if (!preview)
            button(
                51,
                "minecraft:lime_dye",
                "Resgatar tudo",
                () -> mod.claim(owner, s.id, s.rewards.keySet()));
        }
        default -> throw new IllegalArgumentException("Menu desconhecido");
      }
    }

    private void nav(int max) {
      button(45, "minecraft:spectral_arrow", "Primeira página", () -> page = 0);
      button(46, "minecraft:arrow", "Página anterior", () -> page = Math.max(0, page - 1));
      button(52, "minecraft:arrow", "Próxima página", () -> page = Math.min(max, page + 1));
      button(53, "minecraft:spectral_arrow", "Última página", () -> page = max);
    }
  }
}
