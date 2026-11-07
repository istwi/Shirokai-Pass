package br.com.shirokai.pass;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.*;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.WorldSavePath;

/** Development-only: exercise the entity ABI as well as Shirokai's command-source checks. */
public final class PermissionAuditHarness implements ModInitializer {
  @Override
  public void onInitialize() {
    if (!Boolean.getBoolean("shirokai.audit")) return;
    ServerPlayConnectionEvents.JOIN.register(
        (handler, sender, server) -> probe(handler.player, "shirokaipass.use", "join"));
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) -> {
          var request =
              CommandManager.argument("request", StringArgumentType.word())
                  .executes(
                      c -> {
                        probe(
                            EntityArgumentType.getPlayer(c, "player"),
                            StringArgumentType.getString(c, "node"),
                            StringArgumentType.getString(c, "request"));
                        return 1;
                      });
          var node = CommandManager.argument("node", StringArgumentType.word()).then(request);
          dispatcher.register(
              CommandManager.literal("auditpermissions")
                  .requires(source -> source.hasPermissionLevel(4))
                  .then(CommandManager.argument("player", EntityArgumentType.player()).then(node)));
        });
  }

  private static void probe(ServerPlayerEntity player, String node, String request) {
    var server = player.getServer();
    Map<String, Object> result = new LinkedHashMap<>();
    result.put("request", request);
    result.put("node", node);
    // No linkage guard here: the test must fail if any real API overload is incompatible.
    result.put("source", Permissions.check(player.getCommandSource(), node, 2));
    result.put("entity", Permissions.check((Entity) player, node, 2));
    result.put("shirokai", ShirokaiPass.permission(player.getCommandSource(), node, 2));
    result.put(
        "apiCount",
        FabricLoader.getInstance().getAllMods().stream()
            .filter(m -> m.getMetadata().getId().equals("fabric-permissions-api-v0"))
            .count());
    result.put(
        "apiVersion",
        FabricLoader.getInstance()
            .getModContainer("fabric-permissions-api-v0")
            .orElseThrow()
            .getMetadata()
            .getVersion()
            .getFriendlyString());
    result.put(
        "lpVersion",
        FabricLoader.getInstance()
            .getModContainer("luckperms")
            .map(m -> m.getMetadata().getVersion().getFriendlyString())
            .orElse("absent"));
    Permissions.check(player.getUuid(), node, false)
        .thenCombine(
            Permissions.check(player.getGameProfile(), node, false),
            (uuid, profile) -> {
              result.put("uuid", uuid);
              result.put("profile", profile);
              return result;
            })
        .whenComplete(
            (complete, error) ->
                server.execute(
                    () -> {
                      try {
                        if (error != null)
                          throw new IllegalStateException("Async permission lookup failed", error);
                        Store.write(
                            server
                                .getSavePath(WorldSavePath.ROOT)
                                .resolve(
                                    "permission-" + player.getGameProfile().getName() + ".json"),
                            Store.JSON.toJson(complete));
                      } catch (Exception failure) {
                        throw new IllegalStateException("Permission audit failed", failure);
                      }
                    }));
  }
}
