package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;

import java.util.*;
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
    if (page < 0) {
      page = 0;
      if (!view.equals("history") && !view.equals("pending")) {
        try {
          Season s = current(mod, season);
          page =
              Math.min(
                  11, (Math.max(1, level(s, mod.service.progress(s.id, p.getUuid()).xp)) - 1) / 9);
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
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
    return decorate(new ItemStack(item), name, color, Arrays.asList(lore));
  }

  private static ItemStack decorate(
      ItemStack stack, String name, Formatting color, List<String> lore) {
    stack.set(
        DataComponentTypes.CUSTOM_NAME,
        Text.literal(name).formatted(color).styled(s -> s.withItalic(false)));
    List<Text> lines = new ArrayList<>();
    // Preserve configured item lore/components in the display copy, never in the stored reward.
    LoreComponent original = stack.get(DataComponentTypes.LORE);
    if (original != null) lines.addAll(original.lines());
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
      this(sync, inventory, mod, owner, view, page, season, preview, new SimpleInventory(36));
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
      super(ScreenHandlerType.GENERIC_9X4, sync, inventory, contents, 4);
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
      } catch (Exception e) {
        owner.sendMessage(
            Text.literal(e.getMessage() == null ? "Operação indisponível" : e.getMessage())
                .formatted(Formatting.RED),
            true);
      } finally {
        if (owner.currentScreenHandler == this) {
          render();
          syncState();
        }
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

    private void button(int slot, String label, Runnable action) {
      put(slot, icon("minecraft:arrow", label, Formatting.AQUA), action);
    }

    private static String remaining(long deadline) {
      long sec = Math.max(0, (deadline - System.currentTimeMillis()) / 1000);
      return sec / 86400 + "d " + sec % 86400 / 3600 + "h " + sec % 3600 / 60 + "m";
    }

    private ItemStack information(Season s, Progress p, boolean premiumActive) {
      int lv = s == null ? 0 : level(s, p.xp);
      long floor = s == null ? 0 : threshold(lv, s.baseXp, s.incrementXp);
      long needed = s == null || lv == 100 ? 0 : threshold(lv + 1, s.baseXp, s.incrementXp) - floor;
      List<String> lore = new ArrayList<>();
      lore.add("Temporada: " + (s == null ? "Aguardando início" : s.name));
      lore.add("Nível: " + lv + " / 100");
      lore.add(
          lv == 100 ? "XP: " + p.xp + " • Nível máximo" : "XP: " + (p.xp - floor) + " / " + needed);
      lore.add("Premium: " + (premiumActive ? "Ativo" : "Inativo"));
      if (premiumActive && !p.premium)
        lore.add("Premium restante: " + remaining(mod.service.premiumExpiresAt(owner.getUuid())));
      if (s != null) {
        long now = System.currentTimeMillis();
        lore.add(
            now < s.end
                ? "Temporada restante: " + remaining(s.end)
                : now < s.graceEnd
                    ? "Resgate extra: " + remaining(s.graceEnd)
                    : "Temporada encerrada");
      }
      lore.add("Página " + (page + 1) + " / 12");
      if (preview)
        lore.add(view.equals("editor") ? "Edição de recompensas" : "Prévia • sem resgates");
      if (view.equals("premium")) lore.add(mod.config.premiumInfo);
      if (view.equals("info")) lore.add(mod.config.rules);
      return icon(
          "minecraft:book", "Informações do passe", Formatting.AQUA, lore.toArray(String[]::new));
    }

    private boolean available(Season s, Progress p, Reward r, boolean premiumActive) {
      return r != null
          && r.enabled
          && !p.claimed.contains(key(r.level, r.premium))
          && level(s, p.xp) >= r.level
          && (!r.premium || premiumActive)
          && s.claiming(System.currentTimeMillis());
    }

    private ItemStack rewardIcon(Season s, Progress p, Reward r, boolean premiumActive) {
      boolean claimed = p.claimed.contains(key(r.level, r.premium));
      boolean eligible = available(s, p, r, premiumActive);
      List<String> lore = new ArrayList<>();
      lore.add(
          !r.enabled
              ? "Não configurada"
              : claimed ? "Resgatada" : eligible ? "Disponível" : "Bloqueada");
      if (r.premium && !premiumActive) lore.add("Requer passe Premium");
      if (!s.claiming(System.currentTimeMillis())) lore.add("Prazo de resgate encerrado");
      long total = r.choices.stream().mapToLong(c -> c.weight).sum();
      ItemStack display = ItemStack.EMPTY;
      for (int i = 0; i < r.choices.size(); i++) {
        if (i >= 4 && !display.isEmpty()) break;
        Choice c = r.choices.get(i);
        String label;
        if (c.type.equals("item")) {
          try {
            ItemStack item = RewardItems.stack(mod.server(), c.value, 1);
            label = shortText(item.getName().getString()) + " ×" + c.count;
            if (display.isEmpty()) {
              item.setCount(Math.min(c.count, item.getMaxCount()));
              display = item;
            }
          } catch (IllegalArgumentException e) {
            label = "Item indisponível";
          }
        } else
          label =
              c.type.equals("pokemon") ? "Pokémon: " + shortText(c.value) : "Comando do servidor";
        if (i < 4)
          lore.add(
              label
                  + (r.mode.equals("random") && total > 0
                      ? String.format(Locale.ROOT, " (%.1f%%)", 100.0 * c.weight / total)
                      : ""));
      }
      if (r.choices.size() > 4) lore.add("+" + (r.choices.size() - 4) + " prêmios");
      if (display.isEmpty()) {
        boolean pokemon = r.choices.stream().anyMatch(c -> c.type.equals("pokemon"));
        display = icon(pokemon ? "cobblemon:poke_ball" : "minecraft:paper", "", Formatting.AQUA);
      }
      if (view.equals("editor")) lore.add("Clique para editar");
      else if (preview) lore.add("Prévia • sem resgates");
      else if (eligible) lore.add("Clique para resgatar");
      return decorate(
          display,
          (r.premium ? "Premium" : "Grátis") + " • " + r.level,
          claimed
              ? Formatting.GREEN
              : eligible ? Formatting.YELLOW : r.premium ? Formatting.GOLD : Formatting.AQUA,
          lore);
    }

    private static String shortText(String text) {
      return text.length() <= 48 ? text : text.substring(0, 45) + "…";
    }

    void render() {
      actions.clear();
      contents.clear();
      State state = mod.service.snapshot();
      Season s = state.seasons.get(season == null ? state.active : season);
      Progress p = s == null ? new Progress() : mod.service.progress(s.id, owner.getUuid());
      boolean premiumActive = mod.service.hasPremium(s == null ? null : s.id, owner.getUuid());
      if (view.equals("history")) {
        history(state);
        return;
      }
      if (view.equals("pending")) {
        pending(state);
        return;
      }
      page = Math.min(11, Math.max(0, page));
      put(
          31,
          information(s, p, premiumActive),
          view.equals("editor") ? () -> AdminGui.dashboard(mod, owner) : null);
      navigation(s == null ? 0 : 11);
      if (s == null) return;
      for (int col = 0; col < 9; col++) {
        int lv = page * 9 + col + 1;
        if (lv > 100) continue;
        boolean claimable = false;
        for (boolean premium : new boolean[] {false, true}) {
          String key = key(lv, premium);
          Reward r = s.rewards.get(key);
          if (r == null) continue;
          claimable |= available(s, p, r, premiumActive);
          put(
              (premium ? 9 : 0) + col,
              rewardIcon(s, p, r, premiumActive),
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
        int current = level(s, p.xp);
        String color =
            claimable ? "yellow" : lv <= current ? "lime" : lv == current + 1 ? "orange" : "gray";
        String status =
            claimable
                ? "Recompensa disponível"
                : lv <= current ? "Concluído" : lv == current + 1 ? "Em progresso" : "Bloqueado";
        put(
            18 + col,
            icon(
                "minecraft:" + color + "_stained_glass_pane",
                "Nível " + lv,
                Formatting.WHITE,
                status),
            null);
      }
    }

    private void history(State state) {
      List<Season> past = new ArrayList<>(state.seasons.values());
      Collections.reverse(past);
      int max = Math.max(0, (past.size() - 1) / 27);
      page = Math.min(max, Math.max(0, page));
      for (int i = 0; i < 27 && page * 27 + i < past.size(); i++) {
        Season previous = past.get(page * 27 + i);
        Progress old = mod.service.progress(previous.id, owner.getUuid());
        put(
            i,
            icon(
                "minecraft:book",
                previous.name,
                Formatting.AQUA,
                "Nível: " + level(previous, old.xp),
                "Resgate: "
                    + (previous.claiming(System.currentTimeMillis())
                        ? remaining(previous.graceEnd)
                        : "Encerrado")),
            () ->
                go(
                    "rewards",
                    Math.min(11, (Math.max(1, level(previous, old.xp)) - 1) / 9),
                    previous.id));
      }
      put(
          31,
          icon("minecraft:book", "Histórico", Formatting.AQUA, "Clique para voltar ao passe"),
          () -> go("home", 0, null));
      navigation(max);
    }

    private void pending(State state) {
      List<Delivery> list =
          state.deliveries.values().stream()
              .filter(d -> d.player.equals(owner.getUuidAsString()) && !d.status.equals("DONE"))
              .toList();
      int max = Math.max(0, (list.size() - 1) / 27);
      page = Math.min(max, Math.max(0, page));
      for (int i = 0; i < 27 && page * 27 + i < list.size(); i++) {
        Delivery d = list.get(page * 27 + i);
        put(
            i,
            icon(
                "minecraft:hopper",
                d.reward,
                Formatting.GOLD,
                d.season,
                "Estado: " + d.status,
                d.status.equals("REVIEW")
                    ? "Peça revisão à equipe"
                    : "Libere espaço no inventário"),
            null);
      }
      put(
          31,
          icon(
              "minecraft:book",
              "Entregas pendentes",
              Formatting.AQUA,
              "Clique para tentar entregas prontas",
              "Entregas em revisão exigem a equipe"),
          () -> mod.deliver(owner));
      navigation(max);
    }

    private void navigation(int max) {
      button(27, "Página anterior", () -> page = Math.max(0, page - 1));
      button(35, "Próxima página", () -> page = Math.min(max, page + 1));
    }
  }
}
