package com.modeldock;

import static com.modeldock.Models.*;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService implements ApplicationRunner {
  private final DockRepository repo;
  private final PasswordEncoder passwords;
  private final DockProperties properties;
  private final Clock clock;

  public AccountService(
      DockRepository repo, PasswordEncoder passwords, DockProperties properties, Clock clock) {
    this.repo = repo;
    this.passwords = passwords;
    this.properties = properties;
    this.clock = clock;
  }

  @Transactional
  public UserView register(Register r) {
    validatePassword(r.password());
    User u =
        new User(
            UUID.randomUUID().toString(),
            r.email().strip().toLowerCase(Locale.ROOT),
            r.displayName().strip(),
            passwords.encode(r.password()),
            "USER",
            "PENDING",
            properties.userDailyLimit());
    repo.insertUser(u, clock.instant());
    return u.view();
  }

  public User login(Login r) {
    User u = repo.email(r.email().strip().toLowerCase(Locale.ROOT)).orElse(null);
    // Always run bcrypt, including unknown accounts, to reduce enumeration timing differences.
    String hash =
        u == null
            ? "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW"
            : u.passwordHash();
    if (!passwords.matches(r.password(), hash) || u == null)
      throw new ApiException(401, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해 주세요.");
    return u;
  }

  static void validatePassword(String value) {
    if (value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new ApiException(400, "INVALID_PASSWORD", "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
  }

  @Override
  @Transactional
  public void run(ApplicationArguments ignored) {
    repo.lock();
    if (repo.jdbc().queryForObject("SELECT COUNT(*) FROM app_users WHERE role='ADMIN'", Long.class)
        > 0) return;
    if (properties.bootstrapEmail().isBlank() || properties.bootstrapPassword().isBlank()) return;
    validatePassword(properties.bootstrapPassword());
    if (properties.bootstrapPassword().length() < 12)
      throw new IllegalStateException(
          "Bootstrap administrator password must contain at least 12 characters");
    repo.insertUser(
        new User(
            UUID.randomUUID().toString(),
            properties.bootstrapEmail().strip().toLowerCase(Locale.ROOT),
            "관리자",
            passwords.encode(properties.bootstrapPassword()),
            "ADMIN",
            "APPROVED",
            properties.userDailyLimit()),
        clock.instant());
    repo.jdbc()
        .update(
            "UPDATE runtime_settings SET service_daily_limit=? WHERE id=1",
            properties.serviceDailyLimit());
  }
}
