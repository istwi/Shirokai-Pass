package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;

import net.minecraft.server.MinecraftServer;

/** Validate before a reward becomes available, rather than after reserving a player's claim. */
final class RewardValidation {
  static void check(MinecraftServer server, Reward reward) {
    reward.validate();
    int totalSlots = 0;
    for (Choice choice : reward.choices) {
      if (choice.type.equals("item")) {
        int stackSize = RewardItems.stack(server, choice.value, 1).getMaxCount();
        int slots = (choice.count + stackSize - 1) / stackSize;
        if (slots > 36)
          throw new IllegalArgumentException(
              "Divida a quantidade de itens em outras recompensas (limite: 36 slots)");
        totalSlots += slots;
      }
      if (choice.type.equals("pokemon")) PokemonRewards.properties(choice.value);
      if (choice.type.equals("command")) {
        String command =
            choice
                .value
                .replace("{player}", "ShirokaiPreview")
                .replace("{uuid}", "11111111-1111-4111-8111-111111111111")
                .replace("{level}", "1")
                .replace("{xp}", "100")
                .replace("{season}", "preview")
                .replace("{premium}", "Ativo");
        if (command.startsWith("/")) command = command.substring(1);
        var parsed =
            server.getCommandManager().getDispatcher().parse(command, server.getCommandSource());
        if (parsed.getReader().canRead()
            || !parsed.getExceptions().isEmpty()
            || parsed.getContext().getNodes().isEmpty())
          throw new IllegalArgumentException("Comando de recompensa inválido: " + choice.value);
      }
    }
    if (reward.mode.equals("fixed") && totalSlots > 36)
      throw new IllegalArgumentException(
          "O conjunto de itens excede 36 slots. Divida os prêmios em outros níveis.");
  }
}
