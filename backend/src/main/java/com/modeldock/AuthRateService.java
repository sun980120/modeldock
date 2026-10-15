package com.modeldock;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthRateService {
  private final DockRepository repo;
  private final Clock clock;

  public AuthRateService(DockRepository repo, Clock clock) {
    this.repo = repo;
    this.clock = clock;
  }

  @Transactional
  public void check(String operation, String email, String address) {
    repo.lock();
    long window = Math.floorDiv(clock.instant().getEpochSecond(), 900) * 900;
    for (String scope :
        new String[] {
          operation + ":email:" + email.strip().toLowerCase(Locale.ROOT),
          operation + ":address:" + address
        }) {
      String hash = hash(scope);
      int maximum = scope.contains(":address:") ? 30 : 10;
      int count =
          repo
              .jdbc()
              .query(
                  "SELECT attempts FROM auth_rate_windows WHERE scope_hash=? AND window_start=?",
                  (r, n) -> r.getInt(1),
                  hash,
                  window)
              .stream()
              .findFirst()
              .orElse(0);
      if (count >= maximum)
        throw new ApiException(429, "AUTH_RATE_LIMITED", "시도 횟수가 너무 많습니다. 15분 후 다시 시도해 주세요.");
      if (count == 0)
        repo.jdbc().update("INSERT INTO auth_rate_windows VALUES (?,?,1)", hash, window);
      else
        repo.jdbc()
            .update(
                "UPDATE auth_rate_windows SET attempts=attempts+1 WHERE scope_hash=? AND"
                    + " window_start=?",
                hash,
                window);
    }
    repo.jdbc().update("DELETE FROM auth_rate_windows WHERE window_start<?", window - 86400);
  }

  static String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
