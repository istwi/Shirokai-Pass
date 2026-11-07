package br.com.shirokai.pass;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.util.WorldSavePath;

/**
 * Explicit development-only checkpoint, allowing the controller to kill this server mid-delivery.
 */
public final class AuditHarness implements ModInitializer {
  @Override
  public void onInitialize() {
    if (!Boolean.getBoolean("shirokai.audit")) return;
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) -> {
          dispatcher.register(
              CommandManager.literal("auditstorage")
                  .requires(source -> source.hasPermissionLevel(4))
                  .then(
                      CommandManager.literal("fail")
                          .executes(
                              context -> {
                                try {
                                  var state =
                                      context
                                          .getSource()
                                          .getServer()
                                          .getSavePath(WorldSavePath.ROOT)
                                          .resolve("shirokaipass/state.json");
                                  var backup = state.resolveSibling("state.json.audit-saved");
                                  if (java.nio.file.Files.exists(backup)
                                      || !java.nio.file.Files.isRegularFile(state))
                                    throw new IllegalStateException("Unsafe fixture state");
                                  java.nio.file.Files.move(state, backup);
                                  java.nio.file.Files.createDirectory(state);
                                  return 1;
                                } catch (Exception e) {
                                  throw new IllegalStateException(
                                      "Cannot inject owned storage fixture", e);
                                }
                              }))
                  .then(
                      CommandManager.literal("repair")
                          .executes(
                              context -> {
                                try {
                                  var state =
                                      context
                                          .getSource()
                                          .getServer()
                                          .getSavePath(WorldSavePath.ROOT)
                                          .resolve("shirokaipass/state.json");
                                  var backup = state.resolveSibling("state.json.audit-saved");
                                  if (!java.nio.file.Files.isDirectory(state)
                                      || !java.nio.file.Files.isRegularFile(backup))
                                    throw new IllegalStateException("Unsafe fixture state");
                                  try (var files = java.nio.file.Files.list(state)) {
                                    if (files.findAny().isPresent())
                                      throw new IllegalStateException(
                                          "Unknown files: preserve and stop");
                                  }
                                  java.nio.file.Files.delete(state);
                                  java.nio.file.Files.move(backup, state);
                                  return 1;
                                } catch (Exception e) {
                                  throw new IllegalStateException(
                                      "Cannot repair owned storage fixture", e);
                                }
                              })));
        });
    CommandRegistrationCallback.EVENT.register(
        (dispatcher, access, environment) ->
            dispatcher.register(
                CommandManager.literal("auditcheckpoint")
                    .requires(source -> source.hasPermissionLevel(4))
                    .executes(
                        context -> {
                          try {
                            var server = context.getSource().getServer();
                            Store.write(
                                server
                                    .getSavePath(WorldSavePath.ROOT)
                                    .resolve("audit-checkpoint.json"),
                                Store.JSON.toJson(
                                    Map.of(
                                        "pid",
                                        ProcessHandle.current().pid(),
                                        "time",
                                        System.currentTimeMillis())));
                            ShirokaiPass.LOG.info(
                                "AUDIT_CHECKPOINT: controller may SIGKILL this isolated server"
                                    + " now");
                            new CountDownLatch(1).await(30, TimeUnit.SECONDS);
                            return 0; // A controller that did not kill the server must not report a
                            // successful crash test.
                          } catch (Exception e) {
                            throw new IllegalStateException("Audit checkpoint failed", e);
                          }
                        })));
  }
}
