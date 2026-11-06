package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;

import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Explicit opt-in harness for a disposable development world with an active 2x XP event. */
public final class IntegrationHarness implements ModInitializer {
  @Override
  public void onInitialize() {
    if (!Boolean.getBoolean("shirokai.integration")) return;
    ServerLifecycleEvents.SERVER_STARTED.register(
        server -> {
          try {
            var mod = ShirokaiPass.instance();
            UUID id = UUID.randomUUID();
            var dispatcher = server.getCommandManager().getDispatcher();
            var authorized = server.getCommandSource().withLevel(2);
            String command = "passeadmin xp add " + id + " 100 harness:" + id + "/event";
            check(
                dispatcher.execute(command, authorized) == 1,
                "Other-mod authorized command source");
            check(
                mod.service.progress(mod.service.active().id, id).xp == 200,
                "External command respects 2x");
            check(
                dispatcher.execute(command, authorized) == 1, "Duplicate command processed safely");
            check(
                mod.service.progress(mod.service.active().id, id).xp == 200,
                "Namespaced operation id deduplicated");
            long before = mod.service.progress(mod.service.active().id, id).xp;
            dispatcher.execute("passeadmin xp add " + id + " 100 bypass", authorized);
            check(
                mod.service.progress(mod.service.active().id, id).xp == before,
                "OP2 cannot bypass multiplier");
            try {
              dispatcher.execute(command, authorized.withLevel(0));
              throw new AssertionError("Unauthorized XP accepted");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
              check(true, "Unauthorized external source rejected");
            }
            check(
                dispatcher.execute(
                        "passeadmin xp add " + id + " 100 harness:" + id + "/bypass bypass",
                        server.getCommandSource())
                    == 1,
                "Authorized namespaced bypass");
            check(
                mod.service.progress(mod.service.active().id, id).xp == 300,
                "Bypass awards original amount");
            // A real separate Fabric mod invokes the public API from outside the server thread.
            new Thread(
                    () ->
                        PassApi.addXp(id, 100, "api/" + id)
                            .thenAccept(
                                result -> {
                                  check(
                                      result.after() == 500,
                                      "API shares multiplier and progression");
                                  PassApi.addXp(id, 100, "api/" + id)
                                      .thenAccept(
                                          retry -> {
                                            check(
                                                retry.duplicate(),
                                                "API duplicate is idempotent on server thread");
                                            ShirokaiPass.LOG.info("INTEGRATION_HARNESS_SUCCESS");
                                          });
                                })
                            .exceptionally(
                                error -> {
                                  ShirokaiPass.LOG.error("INTEGRATION_HARNESS_FAILED", error);
                                  return null;
                                }),
                    "pass-api-harness")
                .start();
          } catch (Exception e) {
            throw new IllegalStateException("Integration harness failed", e);
          }
        });
  }

  private static void check(boolean condition, String label) {
    if (!condition) throw new AssertionError(label);
    ShirokaiPass.LOG.info("HARNESS PASS: {}", label);
  }
}
