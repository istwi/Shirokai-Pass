package br.com.shirokai.pass;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A provider failure denies the operation; it never silently switches to an OP bypass. */
final class PermissionGuard {
  private PermissionGuard() {}

  static boolean check(BooleanSupplier backend, Consumer<Throwable> report) {
    try {
      return backend.getAsBoolean();
    } catch (RuntimeException | LinkageError error) {
      report.accept(error);
      return false;
    }
  }
}
