package com.modeldock;

import static com.modeldock.Models.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ModelDockIntegrationTest {
  static final String A = "10000000-0000-0000-0000-000000000001",
      B = "10000000-0000-0000-0000-000000000002",
      ADMIN = "10000000-0000-0000-0000-000000000003";
  @Autowired DockRepository repo;
  @Autowired BudgetService budgets;
  @Autowired ConversationService conversations;
  @Autowired SettingsService settings;
  @Autowired MockMvc mvc;
  @Autowired AdminService admins;
  @Autowired AuthRateService rates;
  String ca, cb;

  @BeforeEach
  void setup() {
    repo.jdbc().update("DELETE FROM messages");
    repo.jdbc().update("DELETE FROM generations");
    repo.jdbc().update("DELETE FROM conversations");
    repo.jdbc().update("DELETE FROM daily_budgets");
    repo.jdbc().update("DELETE FROM app_users");
    repo.jdbc().update("DELETE FROM auth_rate_windows");
    for (String id : List.of(A, B, ADMIN))
      repo.insertUser(
          new User(
              id,
              id + "@example.test",
              "테스트",
              "unused",
              id.equals(ADMIN) ? "ADMIN" : "USER",
              "APPROVED",
              10000000),
          Instant.now());
    settings.save(
        new AdminSettings(
            new FusionSettings(List.of("model-a", "model-b", "model-c"), "judge", 1024, 30, 4),
            100000000,
            false));
    ca = conversations.create(A).id();
    cb = conversations.create(B).id();
  }

  Generate request() {
    return new Generate(UUID.randomUUID(), "안녕하세요");
  }

  @Test
  void everyConversationPathRejectsOtherOwnerAndAdmin() throws Exception {
    var reservation = budgets.reserve(B, cb, request());
    String g = reservation.generation().id();
    for (String actor : List.of(A, ADMIN)) {
      var auth = user(actor).roles(actor.equals(ADMIN) ? "ADMIN" : "USER");
      mvc.perform(get("/api/conversations/" + cb + "/messages").with(auth))
          .andExpect(status().isNotFound());
      mvc.perform(
              patch("/api/conversations/" + cb)
                  .with(auth)
                  .with(csrf())
                  .contentType("application/json")
                  .content("{\"title\":\"stolen\"}"))
          .andExpect(status().isNotFound());
      mvc.perform(delete("/api/conversations/" + cb).with(auth).with(csrf()))
          .andExpect(status().isNotFound());
      mvc.perform(
              post("/api/conversations/" + cb + "/generations")
                  .with(auth)
                  .with(csrf())
                  .contentType("application/json")
                  .content("{\"requestId\":\"" + UUID.randomUUID() + "\",\"content\":\"attack\"}"))
          .andExpect(status().isNotFound());
      mvc.perform(
              post("/api/conversations/" + cb + "/generations/" + g + "/cancel")
                  .with(auth)
                  .with(csrf()))
          .andExpect(status().isNotFound());
      mvc.perform(get("/api/conversations/" + cb + "/generations/" + g).with(auth))
          .andExpect(status().isNotFound());
      mvc.perform(get("/api/conversations/" + cb + "/generations/" + g + "/events").with(auth))
          .andExpect(status().isNotFound());
      mvc.perform(get("/api/conversations?q=새").with(auth))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[?(@.id == '" + cb + "')]").isEmpty());
    }
  }

  @Test
  void clientCannotOverrideModelOrOwner() throws Exception {
    for (String extra :
        List.of("\"model\":\"single-model\"", "\"userId\":\"" + B + "\"", "\"fusion\":{}"))
      mvc.perform(
              post("/api/conversations/" + ca + "/generations")
                  .with(user(A))
                  .with(csrf())
                  .contentType("application/json")
                  .content(
                      "{\"requestId\":\""
                          + UUID.randomUUID()
                          + "\",\"content\":\"hello\","
                          + extra
                          + "}"))
          .andExpect(status().isBadRequest());
    mvc.perform(
            post("/api/conversations")
                .with(user(A))
                .with(csrf())
                .contentType("application/json")
                .content("{\"userId\":\"" + B + "\"}"))
        .andExpect(status().isOk());
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM conversations WHERE owner_id=?", Long.class, B))
        .isEqualTo(1L);
  }

  @Test
  void accountAndCsrfRulesAreEnforced() throws Exception {
    mvc.perform(post("/api/conversations").with(user(A))).andExpect(status().isForbidden());
    mvc.perform(get("/api/admin/users").with(user(A))).andExpect(status().isForbidden());
    repo.jdbc().update("UPDATE app_users SET status='SUSPENDED' WHERE id=?", A);
    mvc.perform(get("/api/conversations").with(user(A))).andExpect(status().isForbidden());
    mvc.perform(get("/api/auth/me").with(user(A)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
  }

  @Test
  void reservationAndMissingUsageAreChargedOnce() {
    var r = budgets.reserve(A, ca, request());
    long reserved = r.generation().reserved();
    assertThat(budgets.usage(A).personal().reserved()).isEqualTo(reserved);
    assertThat(budgets.usage(A).personal().remaining()).isEqualTo(10000000 - reserved);
    budgets.settle(r.generation().id(), "COMPLETED", "합성 답변", 123L, null);
    budgets.settle(r.generation().id(), "COMPLETED", "중복 답변", 123L, null);
    assertThat(budgets.usage(A).personal().reserved()).isZero();
    assertThat(budgets.usage(A).personal().used()).isEqualTo(reserved);
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT reported_tokens FROM generations WHERE id=?",
                    Long.class,
                    r.generation().id()))
        .isEqualTo(123L);
    assertThat(repo.messages(A, ca, null).stream().filter(m -> m.role().equals("assistant")))
        .hasSize(1);
  }

  @Test
  void overrunIsRecordedAndBlocksFurtherReservations() {
    var r = budgets.reserve(A, ca, request());
    budgets.settle(r.generation().id(), "COMPLETED", "합성", 20000000L, null);
    assertThat(budgets.usage(A).personal().used()).isEqualTo(20000000);
    assertThat(budgets.usage(A).personal().remaining()).isZero();
    assertThatThrownBy(() -> budgets.reserve(A, ca, request()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("일일 토큰");
  }

  @Test
  void cancelAndRestartDoNotLeaveReservations() {
    var r = budgets.reserve(A, ca, request());
    budgets.cancel(A, ca, r.generation().id());
    budgets.settle(r.generation().id(), "COMPLETED", "must not store", null, null);
    assertThat(repo.generation(r.generation().id()).status()).isEqualTo("CANCELLED");
    assertThat(repo.messages(A, ca, null)).allMatch(m -> !m.role().equals("assistant"));
    var next = budgets.reserve(A, ca, request());
    repo.jdbc()
        .update(
            "UPDATE generations SET expires_at=? WHERE id=?",
            Timestamp.from(Instant.now().minusSeconds(60)),
            next.generation().id());
    budgets.recoverExpired();
    budgets.recoverExpired();
    assertThat(repo.generation(next.generation().id()).status()).isEqualTo("INTERRUPTED");
    assertThat(budgets.usage(A).personal().reserved()).isZero();
  }

  @Test
  void personalAndGlobalBudgetsReserveAtomically() throws Exception {
    long amount =
        BudgetService.estimate(List.of(new History("user", "안녕하세요")), settings.get().fusion());
    settings.save(new AdminSettings(settings.get().fusion(), amount, false));
    try (var pool = Executors.newFixedThreadPool(2)) {
      CountDownLatch gate = new CountDownLatch(1);
      var fa =
          pool.submit(
              () -> {
                gate.await();
                try {
                  budgets.reserve(A, ca, request());
                  return true;
                } catch (ApiException e) {
                  return false;
                }
              });
      var fb =
          pool.submit(
              () -> {
                gate.await();
                try {
                  budgets.reserve(B, cb, request());
                  return true;
                } catch (ApiException e) {
                  return false;
                }
              });
      gate.countDown();
      assertThat(List.of(fa.get(), fb.get())).containsExactlyInAnyOrder(true, false);
    }
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT reserved_tokens FROM daily_budgets WHERE scope_id='SERVICE'",
                    Long.class))
        .isEqualTo(amount);
    var first = repo.jdbc().queryForObject("SELECT id FROM generations", String.class);
    budgets.settle(first, "FAILED", null, null, "UPSTREAM_FAILED");
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT used_tokens FROM daily_budgets WHERE scope_id='SERVICE'", Long.class))
        .isEqualTo(amount);
  }

  @Test
  void userConcurrentRequestsAndGlobalConcurrencyAreLimited() {
    budgets.reserve(A, ca, request());
    assertThatThrownBy(() -> budgets.reserve(A, ca, request())).isInstanceOf(ApiException.class);
    settings.save(
        new AdminSettings(
            new FusionSettings(List.of("model-a", "model-b"), "judge", 1024, 30, 1),
            100000000,
            false));
    assertThatThrownBy(() -> budgets.reserve(B, cb, request()))
        .isInstanceOf(ApiException.class)
        .hasMessageContaining("동시 생성");
  }

  @Test
  void resetUsesSeoulMidnightAndServiceBudgetIsPrivate() throws Exception {
    Instant before = Instant.parse("2026-10-15T14:59:59Z"),
        after = Instant.parse("2026-10-15T15:00:00Z");
    BudgetService one = new BudgetService(repo, settings, Clock.fixed(before, ZoneOffset.UTC));
    BudgetService two = new BudgetService(repo, settings, Clock.fixed(after, ZoneOffset.UTC));
    var r = one.reserve(A, ca, request());
    assertThat(one.usage(A).personal().reserved()).isPositive();
    assertThat(two.usage(A).personal().reserved()).isZero();
    two.settle(r.generation().id(), "FAILED", null, null, "UPSTREAM_FAILED");
    assertThat(one.usage(A).personal().reserved()).isZero();
    assertThat(one.usage(A).personal().used()).isPositive();
    assertThat(two.usage(A).personal().used()).isZero();
    mvc.perform(get("/api/usage").with(user(A))).andExpect(jsonPath("$.service").isEmpty());
    mvc.perform(get("/api/usage").with(user(ADMIN).roles("ADMIN")))
        .andExpect(jsonPath("$.service.limit").isNumber());
  }

  @Test
  void deletedMessagesAreRemovedWhileAccountingRemains() {
    var r = budgets.reserve(A, ca, request());
    budgets.settle(r.generation().id(), "COMPLETED", "private content", 50L, null);
    conversations.delete(A, ca);
    assertThat(
            repo.jdbc()
                .queryForObject(
                    "SELECT COUNT(*) FROM messages WHERE conversation_id=?", Long.class, ca))
        .isZero();
    assertThat(repo.generation(r.generation().id()).status()).isEqualTo("COMPLETED");
    assertThatThrownBy(() -> repo.messages(A, ca, null)).isInstanceOf(ApiException.class);
  }

  @Test
  void registrationLoginApprovalAndLogoutUseRealSession() throws Exception {
    String body =
        "{\"email\":\"new@example.test\",\"displayName\":\"신규"
            + " 사용자\",\"password\":\"long-password-123\"}";
    mvc.perform(
            post("/api/auth/register").with(csrf()).contentType("application/json").content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"))
        .andExpect(jsonPath("$.role").value("USER"))
        .andExpect(jsonPath("$.passwordHash").doesNotExist());
    var login =
        mvc.perform(
                post("/api/auth/login")
                    .with(csrf())
                    .contentType("application/json")
                    .content("{\"email\":\"new@example.test\",\"password\":\"long-password-123\"}"))
            .andExpect(status().isOk())
            .andReturn();
    var cookie = login.getResponse().getCookies();
    assertThat(cookie).isNotEmpty();
    mvc.perform(get("/api/auth/me").cookie(cookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PENDING"));
    mvc.perform(get("/api/conversations").cookie(cookie)).andExpect(status().isForbidden());
    admins.update(
        repo.email("new@example.test").orElseThrow().id(), new UserAction("APPROVED", 1000000));
    mvc.perform(get("/api/conversations").cookie(cookie)).andExpect(status().isOk());
    mvc.perform(post("/api/auth/logout").cookie(cookie).with(csrf())).andExpect(status().isOk());
    mvc.perform(get("/api/auth/me").cookie(cookie)).andExpect(status().isUnauthorized());
  }

  @Test
  void sharedQuotaIsUnknownAndNeverSummed() throws Exception {
    mvc.perform(get("/api/admin/provider-quotas").with(user(ADMIN).roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNKNOWN"))
        .andExpect(jsonPath("$.pools").isEmpty())
        .andExpect(jsonPath("$.totalRemainingTokens").doesNotExist());
  }

  @Test
  void authAttemptsAreLimitedAndSuspensionRequestsCancellation() {
    for (int i = 0; i < 10; i++) rates.check("login", "test@example.test", "127.0.0.1");
    assertThatThrownBy(() -> rates.check("login", "test@example.test", "127.0.0.1"))
        .isInstanceOf(ApiException.class);
    var r = budgets.reserve(A, ca, request());
    admins.update(A, new UserAction("SUSPENDED", 10000000));
    assertThat(repo.generation(r.generation().id()).cancelRequested()).isTrue();
  }
}
