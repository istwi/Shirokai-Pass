package br.com.shirokai.pass;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;

/** Optional Cobblemon integration: keep vanilla-only servers free of Cobblemon linkage. */
final class PokemonRewards {
  private static final String API = "com.cobblemon.mod.common.";

  private PokemonRewards() {}

  static Object properties(String definition) {
    if (!FabricLoader.getInstance().isModLoaded("cobblemon"))
      throw new IllegalArgumentException("Cobblemon não instalado");
    try {
      Class<?> type = Class.forName(API + "api.pokemon.PokemonProperties");
      Object companion = type.getField("Companion").get(null);
      Object properties = companion.getClass().getMethod("parse", String.class)
          .invoke(companion, definition);
      if (type.getMethod("getSpecies").invoke(properties) == null)
        throw new IllegalArgumentException("Espécie de Pokémon inválida: " + definition);
      return properties;
    } catch (ReflectiveOperationException e) {
      throw failure(e);
    }
  }

  static Object prepare(String definition) {
    Object properties = properties(definition);
    try {
      return properties.getClass().getMethod("create").invoke(properties);
    } catch (ReflectiveOperationException e) {
      throw failure(e);
    }
  }

  static void deliver(ServerPlayerEntity player, Object pokemon) {
    try {
      Class<?> cobblemon = Class.forName(API + "Cobblemon");
      Object instance = cobblemon.getField("INSTANCE").get(null);
      Object storage = cobblemon.getMethod("getStorage").invoke(instance);
      // Parameter class names are remapped by Fabric; match the actual player instead.
      Method getParty = java.util.Arrays.stream(storage.getClass().getMethods())
          .filter(m -> m.getName().equals("getParty") && m.getParameterCount() == 1
              && m.getParameterTypes()[0].isInstance(player))
          .findFirst().orElseThrow(() -> new NoSuchMethodException("Cobblemon.getParty(player)"));
      Object party = getParty.invoke(storage, player);
      Object added = party.getClass().getMethod("add", pokemon.getClass()).invoke(party, pokemon);
      if (!Boolean.TRUE.equals(added))
        throw new IllegalStateException("Cobblemon não confirmou a entrega à equipe ou ao PC");
    } catch (ReflectiveOperationException e) {
      throw failure(e);
    }
  }

  static ItemStack icon(String definition) {
    Object properties = properties(definition);
    try {
      return (ItemStack) Class.forName(API + "item.PokemonItem")
          .getMethod("from", properties.getClass()).invoke(null, properties);
    } catch (ReflectiveOperationException e) {
      throw failure(e);
    }
  }

  private static IllegalArgumentException failure(ReflectiveOperationException e) {
    Throwable cause = e instanceof InvocationTargetException ? e.getCause() : e;
    return new IllegalArgumentException("Falha na integração Cobblemon: " + cause.getMessage(), cause);
  }
}
