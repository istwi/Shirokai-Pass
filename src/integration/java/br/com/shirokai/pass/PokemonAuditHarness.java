package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;

import java.util.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/** Explicit opt-in, disposable world only: checks the installed Cobblemon, not a mock. */
public final class PokemonAuditHarness implements ModInitializer {
  public void onInitialize() {
    if (!Boolean.getBoolean("shirokai.audit")
        || !FabricLoader.getInstance().isModLoaded("cobblemon")) return;
    CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) ->
        dispatcher.register(CommandManager.literal("auditpokemon")
            .requires(s -> s.hasPermissionLevel(4))
            .then(CommandManager.argument("player", EntityArgumentType.player()).executes(c -> {
              try {
                run(EntityArgumentType.getPlayer(c, "player"));
                c.getSource().sendFeedback(() -> Text.literal("POKEMON_AUDIT_SUCCESS"), false);
                return 1;
              } catch (Exception e) { throw new IllegalStateException("Pokémon audit failed", e); }
            }))));
  }

  private static void run(ServerPlayerEntity player) throws Exception {
    var mod = ShirokaiPass.instance();
    try {
      player.getServer().getCommandManager().getDispatcher().execute(
          "pokegive " + player.getGameProfile().getName() + " pikachu level=10",
          player.getServer().getCommandSource());
      throw new AssertionError("Old console command unexpectedly succeeded");
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
      check(expected.getMessage().equals("A player is required to run this command here"),
          "original console failure reproduced");
    }
    mod.service.create("pokemon-audit", 86400000, 86400000, new Config());
    mod.service.add(player.getUuid(), 1000, null, false);
    var model = PokemonRewards.icon("pikachu level=10 shiny");
    check(net.minecraft.registry.Registries.ITEM.getId(model.getItem()).toString()
        .equals("cobblemon:pokemon_model"), "Pokémon model item");
    try {
      PokemonRewards.properties("species=definitely_invalid_pokemon");
      throw new AssertionError("Invalid species accepted");
    } catch (IllegalArgumentException expected) { }
    int before = count(player, "getParty") + count(player, "getPC");
    mod.service.edit(s -> {
      Reward reward = s.seasons.get("pokemon-audit").rewards.get("free:1");
      reward.enabled = true;
      reward.choices = new ArrayList<>(List.of(new Choice("pokemon", "pikachu level=10 shiny", 1, 1)));
    });
    mod.claim(player, "pokemon-audit", List.of("free:1"));
    check(count(player, "getParty") + count(player, "getPC") == before + 1, "one Pokémon added");
    Delivery d = mod.service.snapshot().deliveries.values().stream()
        .filter(x -> x.season.equals("pokemon-audit")).findFirst().orElseThrow();
    check(d.status.equals("DONE"), "delivery confirmed");
    mod.claim(player, "pokemon-audit", List.of("free:1"));
    check(count(player, "getParty") + count(player, "getPC") == before + 1, "no duplicate");
    while (count(player, "getParty") < 6)
      PokemonRewards.deliver(player, PokemonRewards.prepare("eevee level=5"));
    int pcBefore = count(player, "getPC");
    PokemonRewards.deliver(player, PokemonRewards.prepare("bulbasaur level=12"));
    check(count(player, "getParty") == 6 && count(player, "getPC") == pcBefore + 1, "full party overflows to PC");
    ShirokaiPass.LOG.info("POKEMON_AUDIT_SUCCESS: model, species, claim, receipt, duplicate, PC overflow");
  }

  private static int count(ServerPlayerEntity player, String method) throws Exception {
    Class<?> type = Class.forName("com.cobblemon.mod.common.Cobblemon");
    Object storage = type.getMethod("getStorage").invoke(type.getField("INSTANCE").get(null));
    var getter = Arrays.stream(storage.getClass().getMethods()).filter(m ->
        m.getName().equals(method) && m.getParameterCount() == 1
            && m.getParameterTypes()[0].isInstance(player)).findFirst().orElseThrow();
    int count = 0;
    for (Object pokemon : (Iterable<?>) getter.invoke(storage, player)) if (pokemon != null) count++;
    return count;
  }

  private static void check(boolean condition, String label) {
    if (!condition) throw new AssertionError(label);
  }
}
