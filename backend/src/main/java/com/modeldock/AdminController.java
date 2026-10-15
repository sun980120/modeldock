package com.modeldock;

import static com.modeldock.Models.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
@Validated
public class AdminController {
  final SettingsService settings;
  final DockRepository repo;
  final BudgetService budgets;
  final FusionGateway gateway;
  final AdminService admins;

  public AdminController(
      SettingsService settings,
      DockRepository repo,
      BudgetService budgets,
      FusionGateway gateway,
      AdminService admins) {
    this.settings = settings;
    this.repo = repo;
    this.budgets = budgets;
    this.gateway = gateway;
    this.admins = admins;
  }

  @GetMapping("/settings")
  AdminSettings settings() {
    return settings.get();
  }

  @PutMapping("/settings")
  AdminSettings save(@Valid @RequestBody AdminSettings request) {
    return settings.save(request);
  }

  @GetMapping("/models")
  List<Map<String, String>> models() {
    return gateway.catalog();
  }

  @GetMapping("/users")
  List<Map<String, Object>> users(
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int offset) {
    return repo.jdbc()
        .queryForList(
            "SELECT u.id,u.email,u.display_name AS displayName,u.role,u.status,u.daily_limit AS"
                + " dailyLimit,COALESCE(b.used_tokens,0) AS used,COALESCE(b.reserved_tokens,0) AS"
                + " reserved FROM app_users u LEFT JOIN daily_budgets b ON b.scope_id=u.id AND"
                + " b.budget_day=? ORDER BY u.created_at,u.id LIMIT 50 OFFSET ?",
            budgets.today(),
            offset);
  }

  @PatchMapping("/users/{id}")
  Map<String, Boolean> user(@PathVariable UUID id, @Valid @RequestBody UserAction action) {
    admins.update(id.toString(), action);
    return Map.of("ok", true);
  }

  @GetMapping("/provider-quotas")
  Map<String, Object> quotas() {
    return Map.of(
        "status",
        "UNKNOWN",
        "reason",
        "통합 키의 공개 API는 계정별 잔여 할당량을 제공하지 않습니다. FreeLLMAPI 관리자 화면에서 확인해 주세요.",
        "pools",
        List.of());
  }
}
