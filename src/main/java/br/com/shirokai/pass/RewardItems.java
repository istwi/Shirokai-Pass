package br.com.shirokai.pass;

import com.mojang.brigadier.StringReader;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.ItemStackArgumentType;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;

/** Minecraft's own item/component grammar; respects registries and enabled features. */
final class RewardItems {
  private RewardItems() {}

  static ItemStack stack(MinecraftServer server, String definition, int count) {
    try {
      var access =
          CommandRegistryAccess.of(
              server.getRegistryManager(), server.getOverworld().getEnabledFeatures());
      StringReader reader = new StringReader(definition);
      var parsed = ItemStackArgumentType.itemStack(access).parse(reader);
      if (reader.canRead()) throw new IllegalArgumentException("Dados adicionais no item");
      ItemStack stack = parsed.createStack(count, false);
      if (stack.isEmpty()) throw new IllegalArgumentException("O prêmio não pode ser ar");
      return stack;
    } catch (Exception e) {
      throw new IllegalArgumentException("Item inválido: " + e.getMessage(), e);
    }
  }
}
