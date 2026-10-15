package com.modeldock;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class Models {
  private Models() {}

  public record User(
      String id,
      String email,
      String displayName,
      String passwordHash,
      String role,
      String status,
      long dailyLimit) {
    public UserView view() {
      return new UserView(id, email, displayName, role, status, dailyLimit);
    }
  }

  public record UserView(
      String id, String email, String displayName, String role, String status, long dailyLimit) {}

  public record Register(
      @Email @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(max = 60) String displayName,
      @NotBlank @Size(min = 12, max = 72) String password) {}

  public record Login(
      @Email @NotBlank @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {}

  public record Conversation(String id, String title, Instant updatedAt) {}

  public record Title(@NotBlank @Size(max = 120) String title) {}

  public record Message(
      String id,
      String role,
      String content,
      String generationId,
      String status,
      String errorCode) {}

  public record Generate(
      @NotNull java.util.UUID requestId, @NotBlank @Size(max = 16000) String content) {}

  public record FusionSettings(
      @NotNull @Size(max = 8) List<@NotBlank @Size(max = 160) String> models,
      @NotNull @Size(max = 160) String judge,
      @Min(128) @Max(8192) int outputTokens,
      @Min(10) @Max(240) int timeoutSeconds,
      @Min(1) @Max(32) int concurrency) {
    public void requireReady() {
      if (models.size() < 2
          || models.stream().distinct().count() != models.size()
          || judge.isBlank()
          || models.stream().anyMatch(Models::virtualModel)
          || virtualModel(judge))
        throw new ApiException(503, "FUSION_NOT_CONFIGURED", "관리자가 융합 참여 모델과 합성 모델을 설정해야 합니다.");
    }
  }

  static boolean virtualModel(String id) {
    return id.equalsIgnoreCase("auto")
        || id.toLowerCase().startsWith("auto:")
        || id.toLowerCase().startsWith("fusion");
  }

  public record AdminSettings(
      @NotNull @jakarta.validation.Valid FusionSettings fusion,
      @Min(0) @Max(1000000000000L) long serviceDailyLimit,
      boolean shareServiceBudget) {}

  public record UserAction(
      @NotBlank @Pattern(regexp = "APPROVED|REJECTED|SUSPENDED") String status,
      @Min(0) @Max(1000000000000L) long dailyLimit) {}

  public record Budget(
      long remaining, long limit, long used, long reserved, String resetsAt, double usagePercent) {}

  public record Usage(
      Budget personal, Budget service, String quality, String providerStatus, Instant fetchedAt) {}

  public record Generation(
      String id,
      String conversationId,
      String ownerId,
      String status,
      long reserved,
      Long reported,
      String errorCode,
      Instant expiresAt,
      boolean cancelRequested) {}

  public record History(String role, String content) {}
}
