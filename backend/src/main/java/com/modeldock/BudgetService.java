package com.modeldock;

import static com.modeldock.Models.*;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {
  static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  static final String SERVICE = "SERVICE";
  final DockRepository repo;
  final SettingsService settings;
  final Clock clock;

  public BudgetService(DockRepository repo, SettingsService settings, Clock clock) {
    this.repo = repo;
    this.settings = settings;
    this.clock = clock;
  }

  LocalDate today() {
    return clock.instant().atZone(SEOUL).toLocalDate();
  }

  Budget budget(String scope, long limit, LocalDate day) {
    long[] v =
        repo
            .jdbc()
            .query(
                "SELECT used_tokens,reserved_tokens FROM daily_budgets WHERE scope_id=? AND"
                    + " budget_day=?",
                (r, n) -> new long[] {r.getLong(1), r.getLong(2)},
                scope,
                day)
            .stream()
            .findFirst()
            .orElse(new long[] {0, 0});
    return new Budget(
        Math.max(0, limit - v[0] - v[1]),
        limit,
        v[0],
        v[1],
        day.plusDays(1).atStartOfDay(SEOUL).toOffsetDateTime().toString(),
        limit == 0 ? 100 : Math.min(100, (v[0] + v[1]) * 100.0 / limit));
  }

  @Transactional(readOnly = true)
  public Usage usage(String user) {
    User u = repo.requireApproved(user);
    var s = settings.get();
    LocalDate day = today();
    return new Usage(
        budget(user, u.dailyLimit(), day),
        (u.role().equals("ADMIN") || s.shareServiceBudget())
            ? budget(SERVICE, s.serviceDailyLimit(), day)
            : null,
        "PARTIAL",
        "UNKNOWN",
        clock.instant());
  }

  // Covers pinned panel key rotation (4 attempts) and judge retries (6 attempts).
  static long estimate(List<History> history, FusionSettings config) {
    long input =
        history.stream()
                .mapToLong(m -> m.content().getBytes(StandardCharsets.UTF_8).length + 64L)
                .sum()
            + 1024;
    long panel = config.models().size(), output = config.outputTokens();
    return Math.addExact(
        Math.multiplyExact(panel * 4, input + output),
        Math.multiplyExact(6, input + panel * output * 4 + 4096 + output));
  }

  public record Reservation(
      Generation generation, FusionSettings settings, List<History> history) {}

  @Transactional
  public Reservation reserve(String user, String conversation, Generate request) {
    repo.lock();
    User u = repo.requireApproved(user);
    repo.own(user, conversation);
    FusionSettings config = settings.get().fusion();
    config.requireReady();
    long total =
        repo.jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM generations WHERE status IN ('RESERVED','RUNNING')",
                Long.class);
    if (total >= config.concurrency())
      throw new ApiException(429, "CONCURRENCY_LIMIT", "동시 생성 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.");
    if (repo.jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM generations WHERE owner_id=? AND status IN"
                    + " ('RESERVED','RUNNING')",
                Long.class,
                user)
        > 0) throw new ApiException(409, "GENERATION_ACTIVE", "계정에서 진행 중인 답변을 먼저 완료하거나 중지해 주세요.");
    List<History> history = new ArrayList<>(repo.history(conversation));
    history.add(new History("user", request.content()));
    long amount = estimate(history, config);
    LocalDate day = today();
    if (budget(user, u.dailyLimit(), day).remaining() < amount)
      throw new ApiException(
          429, "USER_BUDGET_EXHAUSTED", "이 요청을 예약할 일일 토큰이 부족합니다. 한국 시간 자정에 초기화됩니다.");
    if (budget(SERVICE, settings.get().serviceDailyLimit(), day).remaining() < amount)
      throw new ApiException(
          429, "SERVICE_BUDGET_EXHAUSTED", "서비스의 일일 이용 예산이 부족합니다. 한국 시간 자정에 초기화됩니다.");
    for (String scope : List.of(SERVICE, user)) {
      repo.jdbc()
          .update(
              "INSERT INTO daily_budgets(scope_id,budget_day) SELECT ?,? WHERE NOT EXISTS (SELECT 1"
                  + " FROM daily_budgets WHERE scope_id=? AND budget_day=?)",
              scope,
              day,
              scope,
              day);
      repo.jdbc()
          .update(
              "UPDATE daily_budgets SET reserved_tokens=reserved_tokens+? WHERE scope_id=? AND"
                  + " budget_day=?",
              amount,
              scope,
              day);
    }
    String id = request.requestId().toString();
    Instant now = clock.instant();
    repo.jdbc()
        .update(
            "INSERT INTO"
                + " generations(id,conversation_id,owner_id,budget_day,status,reserved_tokens,usage_quality,created_at,expires_at)"
                + " VALUES (?,?,?,?,'RESERVED',?,'UNKNOWN',?,?)",
            id,
            conversation,
            user,
            day,
            amount,
            Timestamp.from(now),
            Timestamp.from(now.plusSeconds(config.timeoutSeconds() + 15L)));
    repo.message(conversation, id, "user", request.content(), now);
    repo.jdbc()
        .update(
            "UPDATE conversations SET updated_at=? WHERE id=?", Timestamp.from(now), conversation);
    return new Reservation(repo.generation(id), config, List.copyOf(history));
  }

  @Transactional
  public boolean running(String id) {
    repo.lock();
    Generation g = repo.generation(id);
    if (!g.status().equals("RESERVED") || g.cancelRequested()) return false;
    repo.jdbc().update("UPDATE generations SET status='RUNNING' WHERE id=?", id);
    return true;
  }

  @Transactional
  public void settle(String id, String status, String answer, Long reported, String code) {
    repo.lock();
    Generation g = repo.generation(id);
    if (!List.of("RESERVED", "RUNNING").contains(g.status())) return;
    if (g.cancelRequested()) {
      status = "CANCELLED";
      answer = null;
      code = "CANCELLED";
    }
    // The upstream aggregate omits failed attempts. Retain unknown consumption.
    long charged = Math.max(g.reserved(), reported == null ? 0 : reported);
    var day =
        repo.jdbc()
            .queryForObject(
                "SELECT budget_day FROM generations WHERE id=?", java.sql.Date.class, id)
            .toLocalDate();
    for (String scope : List.of(SERVICE, g.ownerId()))
      repo.jdbc()
          .update(
              "UPDATE daily_budgets SET reserved_tokens=reserved_tokens-?,used_tokens=used_tokens+?"
                  + " WHERE scope_id=? AND budget_day=?",
              g.reserved(),
              charged,
              scope,
              day);
    repo.jdbc()
        .update(
            "UPDATE generations SET"
                + " status=?,reported_tokens=?,charged_tokens=?,usage_quality=?,error_code=? WHERE"
                + " id=?",
            status,
            reported,
            charged,
            reported == null ? "UNKNOWN" : "PARTIAL",
            code,
            id);
    if (status.equals("COMPLETED") && answer != null)
      repo.message(g.conversationId(), id, "assistant", answer, clock.instant());
  }

  @Transactional
  public void cancel(String owner, String conversation, String id) {
    repo.lock();
    repo.ownGeneration(owner, conversation, id);
    repo.jdbc()
        .update(
            "UPDATE generations SET cancel_requested=TRUE WHERE id=? AND status IN"
                + " ('RESERVED','RUNNING')",
            id);
  }

  @Transactional
  public void recoverExpired() {
    repo.lock();
    var ids =
        repo.jdbc()
            .queryForList(
                "SELECT id FROM generations WHERE status IN ('RESERVED','RUNNING') AND"
                    + " expires_at<?",
                String.class,
                Timestamp.from(clock.instant()));
    for (String id : ids) settle(id, "INTERRUPTED", null, null, "GENERATION_INTERRUPTED");
  }
}
