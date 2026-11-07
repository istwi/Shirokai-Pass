package br.com.shirokai.pass;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class PermissionGuardTest {
  @Test
  void preservesAllowAndDenyDecisions() {
    assertTrue(PermissionGuard.check(() -> true, error -> fail(error)));
    assertFalse(PermissionGuard.check(() -> false, error -> fail(error)));
  }

  @Test
  void missingMethodIsLoggedAndDeniedWithoutEscaping() {
    AtomicReference<Throwable> logged = new AtomicReference<>();
    assertFalse(
        PermissionGuard.check(
            () -> {
              throw new NoSuchMethodError("incompatible provider");
            },
            logged::set));
    assertInstanceOf(NoSuchMethodError.class, logged.get());
  }

  @Test
  void runtimeProviderFailureNeverGrantsDefaultOpAccess() {
    AtomicReference<Throwable> logged = new AtomicReference<>();
    assertFalse(
        PermissionGuard.check(
            () -> {
              throw new IllegalStateException("provider failed");
            },
            logged::set));
    assertInstanceOf(IllegalStateException.class, logged.get());
  }
}
