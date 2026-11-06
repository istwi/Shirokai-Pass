package br.com.shirokai.pass;

import static br.com.shirokai.pass.Model.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Calls are scheduled onto the server thread, including notifications and menu refresh. */
public final class PassApi {
  private PassApi() {}

  public static CompletableFuture<XpResult> addXp(UUID player, long amount) {
    return addXp(player, amount, null);
  }

  public static CompletableFuture<XpResult> addXp(UUID player, long amount, String operationId) {
    CompletableFuture<XpResult> result = new CompletableFuture<>();
    ShirokaiPass mod = ShirokaiPass.instance();
    mod.server()
        .execute(
            () -> {
              try {
                result.complete(mod.award(player, amount, operationId, false));
              } catch (Exception e) {
                result.completeExceptionally(e);
              }
            });
    return result;
  }

  public static String expand(UUID player, String text) {
    return ShirokaiPass.instance().expand(player, text);
  }
}
