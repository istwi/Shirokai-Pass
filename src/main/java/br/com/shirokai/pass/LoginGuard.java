package br.com.shirokai.pass;

import java.util.function.Consumer;

/** Persistence faults must never escape the Fabric JOIN callback into player placement. */
final class LoginGuard {
  private LoginGuard() {}

  static boolean run(Runnable passAction, Consumer<RuntimeException> report) {
    try {
      passAction.run();
      return true;
    } catch (RuntimeException error) {
      report.accept(error);
      return false;
    }
  }
}
