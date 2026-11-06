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
