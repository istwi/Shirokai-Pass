package br.com.shirokai.pass;

import java.util.*;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.*;
import net.minecraft.screen.*;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/** Native anvil input for short fields; private chat input for longer values. No client mod. */
final class TextInput {
  private record Pending(
      long expires, int limit, Consumer<String> save, Runnable back, StringBuilder buffer) {}

  private static final Map<UUID, Pending> PENDING = new HashMap<>();

  static void register() {
    ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(
        (message, sender, params) -> {
          Pending pending = PENDING.remove(sender.getUuid());
          if (pending == null) return true;
          String value = message.getContent().getString();
          sender
              .getServer()
              .execute(
                  () -> {
                    if (System.currentTimeMillis() > pending.expires) {
                      sender.sendMessage(Text.literal("Edição expirada."), false);
                      back(pending, sender);
                      return;
                    }
                    if (value.equalsIgnoreCase("cancelar")) {
                      back(pending, sender);
                      return;
                    }
                    try {
                      boolean more = value.endsWith("\\");
                      pending.buffer.append(more ? value.substring(0, value.length() - 1) : value);
                      validate(pending.buffer.toString(), pending.limit);
                      if (more) {
                        PENDING.put(sender.getUuid(), pending);
                        sender.sendMessage(
                            Text.literal("Trecho recebido. Continue o texto ou digite cancelar."),
                            false);
                        return;
                      }
                      pending.save.accept(pending.buffer.toString());
                    } catch (RuntimeException e) {
                      sender.sendMessage(
                          Text.literal(e.getMessage()).formatted(Formatting.RED), false);
                      back(pending, sender);
                    }
                  });
          return false;
        });
  }

  static void clear(UUID player) {
    PENDING.remove(player);
  }

  private static void back(Pending pending, ServerPlayerEntity player) {
    try {
      pending.back.run();
    } catch (RuntimeException e) {
      player.sendMessage(
          Text.literal(Objects.toString(e.getMessage(), "Edição encerrada"))
              .formatted(Formatting.RED),
          false);
    }
  }

  static void clear() {
    PENDING.clear();
  }

  private static void validate(String value, int limit) {
    if (value.length() > limit
        || value.indexOf('\n') >= 0
        || value.indexOf('\r') >= 0
        || value.indexOf('\0') >= 0)
      throw new IllegalArgumentException("Texto inválido ou muito longo (máximo " + limit + ").");
  }

  static void open(
      ServerPlayerEntity player,
      String title,
      String initial,
      int limit,
      Consumer<String> save,
      Runnable back) {
    clear(player.getUuid());
    if (limit > 50) {
      player.closeHandledScreen();
      PENDING.put(
          player.getUuid(),
          new Pending(System.currentTimeMillis() + 120000, limit, save, back, new StringBuilder()));
      player.sendMessage(
          Text.literal(
                  title
                      + ": digite o novo valor no chat, sem barra inicial. Digite cancelar para"
                      + " voltar. Termine um trecho com \\ para continuar na próxima mensagem. Esta"
                      + " resposta é privada.")
              .formatted(Formatting.AQUA),
          false);
      return;
    }
    player.openHandledScreen(
        new SimpleNamedScreenHandlerFactory(
            (sync, inventory, p) -> new Input(sync, inventory, player, initial, limit, save, back),
            Text.literal(title)));
  }

  private static final class Input extends AnvilScreenHandler {
    private final ServerPlayerEntity owner;
    private final Consumer<String> save;
    private final int limit;
    private String value;
    private final Runnable back;
    private boolean confirmed;

    Input(
        int sync,
        PlayerInventory inventory,
        ServerPlayerEntity owner,
        String initial,
        int limit,
        Consumer<String> save,
        Runnable back) {
      super(sync, inventory);
      this.owner = owner;
      this.save = save;
      this.limit = limit;
      this.back = back;
      value = initial;
      ItemStack paper = new ItemStack(Items.PAPER);
      paper.set(
          DataComponentTypes.CUSTOM_NAME,
          Text.literal(initial.isEmpty() ? "Digite aqui" : initial));
      input.setStack(0, paper);
      ItemStack confirm = new ItemStack(Items.LIME_DYE);
      confirm.set(
          DataComponentTypes.CUSTOM_NAME, Text.literal("Salvar valor").formatted(Formatting.GREEN));
      input.setStack(1, confirm);
      updateResult();
    }

    @Override
    public boolean canUse(PlayerEntity player) {
      return player == owner;
    }

    @Override
    public void updateResult() {
      if (output == null) return;
      ItemStack stack = new ItemStack(Items.PAPER);
      stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(value == null ? "Editar" : value));
      output.setStack(0, stack);
    }

    @Override
    public boolean setNewItemName(String name) {
      if (name.length() > limit || name.indexOf(0) >= 0) return false;
      value = name;
      updateResult();
      sendContentUpdates();
      return true;
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
      return ItemStack.EMPTY;
    }

    @Override
    public void onSlotClick(int slot, int button, SlotActionType type, PlayerEntity player) {
      if (player != owner) return;
      if (type != SlotActionType.PICKUP || button != 0 || slot != 1 && slot != 2) {
        syncState();
        return;
      }
      try {
        validate(value, limit);
        confirmed = true;
        save.accept(value);
      } catch (RuntimeException e) {
        confirmed = false;
        owner.sendMessage(Text.literal(e.getMessage()).formatted(Formatting.RED), true);
      }
    }

    @Override
    public void onClosed(PlayerEntity player) {
      input.clear();
      output.clear();
      super.onClosed(player);
      if (!confirmed && owner.getServer() != null) {
        var server = owner.getServer();
        server.send(
            new net.minecraft.server.ServerTask(
                server.getTicks(),
                () -> {
                  if (server.getPlayerManager().getPlayer(owner.getUuid()) == owner
                      && owner.currentScreenHandler == owner.playerScreenHandler) {
                    try {
                      back.run();
                    } catch (RuntimeException e) {
                      owner.sendMessage(
                          Text.literal(
                                  java.util.Objects.toString(e.getMessage(), "Edição encerrada"))
                              .formatted(Formatting.RED),
                          false);
                    }
                  }
                }));
      }
    }
  }
}
