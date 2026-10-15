package com.modeldock;

import static com.modeldock.Models.*;

import com.fasterxml.jackson.databind.*;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class FusionGateway {
  private static final Logger log = LoggerFactory.getLogger(FusionGateway.class);
  static final int RESPONSE_LIMIT = 2 * 1024 * 1024;
  final HttpClient http;
  final ObjectMapper json;
  final DockProperties properties;

  public FusionGateway(HttpClient http, ObjectMapper json, DockProperties properties) {
    this.http = http;
    this.json = json;
    this.properties = properties;
  }

  public static class Flight implements AutoCloseable {
    volatile CompletableFuture<?> future;
    volatile InputStream body;
    volatile boolean cancelled;
    final Instant deadline;

    public Flight(Instant deadline) {
      this.deadline = deadline;
    }

    synchronized void future(CompletableFuture<?> value) {
      future = value;
      if (cancelled) value.cancel(true);
    }

    synchronized void body(InputStream value) throws IOException {
      body = value;
      if (cancelled) value.close();
    }

    @Override
    public synchronized void close() {
      cancelled = true;
      if (future != null) future.cancel(true);
      if (body != null)
        try {
          body.close();
        } catch (IOException ignored) {
        }
    }
  }

  public record Result(String answer, Long reportedTokens) {}

  public void assertConfigured() {
    if (properties.apiKey().isBlank())
      throw new ApiException(503, "UPSTREAM_NOT_CONFIGURED", "FreeLLMAPI 연결을 관리자가 설정해야 합니다.");
  }

  HttpRequest.Builder request(String path) {
    assertConfigured();
    return HttpRequest.newBuilder(URI.create(properties.apiUrl().replaceAll("/$", "") + path))
        .header("Authorization", "Bearer " + properties.apiKey())
        .header("Accept", "application/json")
        .header("X-FreeLLM-Cache", "off");
  }

  Result complete(List<History> history, FusionSettings config, Flight flight) throws Exception {
    var payload =
        Map.of(
            "model",
            "fusion",
            "messages",
            history,
            "stream",
            false,
            "max_tokens",
            config.outputTokens(),
            "fusion",
            Map.of(
                "models",
                config.models(),
                "judge",
                config.judge(),
                "strategy",
                "synthesize",
                "expose_panel",
                false));
    var req =
        request("/chat/completions")
            .timeout(Duration.ofSeconds(config.timeoutSeconds()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)))
            .build();
    CompletableFuture<HttpResponse<InputStream>> future =
        http.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream());
    flight.future(future);
    HttpResponse<InputStream> res = future.get(config.timeoutSeconds(), TimeUnit.SECONDS);
    flight.body(res.body());
    try (InputStream body = res.body()) {
      if (res.statusCode() != 200) throw upstreamStatus(res.statusCode());
      byte[] bytes = body.readNBytes(RESPONSE_LIMIT + 1);
      if (bytes.length > RESPONSE_LIMIT)
        throw new ApiException(502, "UPSTREAM_RESPONSE_TOO_LARGE", "융합 응답의 크기 제한을 초과했습니다.");
      if (flight.cancelled) throw new CancellationException();
      try {
        return validate(json.readTree(new String(bytes, StandardCharsets.UTF_8)), config);
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        throw new ApiException(
            502,
            "FUSION_UNEXPECTED_RESPONSE",
            "FreeLLMAPI가 올바른 JSON 응답을 반환하지 않았습니다. 관리자에게 연결 경로 확인을 요청해 주세요.");
      }
    }
  }

  Result validate(JsonNode root, FusionSettings config) {
    if (root == null || !root.path("model").asText().equals("fusion"))
      throw rejected(
          "FUSION_UNEXPECTED_RESPONSE", "융합 API의 응답 형식을 확인하지 못했습니다. 관리자에게 연결 설정 확인을 요청해 주세요.", 0);
    var fusion = root.path("_fusion");
    var panel = fusion.path("panel");
    if (!fusion.isObject() || !fusion.path("synthesized").isBoolean() || !panel.isArray())
      throw rejected(
          "FUSION_METADATA_MISSING",
          "상위 API 응답에 융합 성공을 확인할 정보가 없습니다. 관리자에게 FreeLLMAPI 버전 확인을 요청해 주세요.",
          0);
    Set<String> members = new HashSet<>();
    for (JsonNode p : panel) {
      String model = p.path("model").asText();
      if (model.isBlank())
        throw rejected("FUSION_METADATA_MISSING", "참여 모델의 응답 메타데이터가 올바르지 않습니다.", panel.size());
      members.add(p.path("platform").asText() + "\u0000" + model);
    }
    if (members.size() != panel.size() || members.size() > config.models().size())
      throw rejected("FUSION_METADATA_INVALID", "참여 모델의 응답 개수를 검증하지 못했습니다.", panel.size());
    if (members.size() < 2)
      throw rejected(
          "FUSION_QUORUM_NOT_MET",
          "참여 모델의 성공 응답이 부족해 융합하지 못했습니다. 잠시 후 다시 시도해 주세요.",
          members.size());
    if (!fusion.path("synthesized").booleanValue())
      throw rejected(
          "FUSION_JUDGE_FAILED", "참여 답변을 최종 합성하지 못했습니다. 잠시 후 다시 시도해 주세요.", members.size());
    if (fusion.path("judge").path("model").asText().isBlank())
      throw rejected("FUSION_METADATA_MISSING", "최종 합성 모델의 응답 정보를 확인하지 못했습니다.", members.size());
    // /v1/models advertises canonical aliases; FreeLLMAPI resolves those to
    // provider IDs (e.g. nemotron-* -> nvidia/nemotron-*). The request fixes the
    // administrator's panel/judge. Validate the gateway's synthesis evidence,
    // never compare unresolved aliases to the provider IDs in its response.
    String answer = root.path("choices").path(0).path("message").path("content").asText("");
    if (!root.path("choices").path(0).path("finish_reason").asText().equals("stop"))
      throw rejected(
          "FUSION_RESPONSE_INCOMPLETE", "최종 합성 답변이 정상적으로 끝나지 않았습니다. 다시 시도해 주세요.", members.size());
    if (answer.isBlank())
      throw rejected("FUSION_EMPTY_ANSWER", "최종 합성 답변이 비어 있습니다. 다시 시도해 주세요.", members.size());
    JsonNode usage = root.path("usage").path("total_tokens");
    Long reported = usage.canConvertToLong() && usage.longValue() > 0 ? usage.longValue() : null;
    // This is the upstream aggregate. Adding panel or judge values again would double count.
    return new Result(answer, reported);
  }

  private ApiException rejected(String code, String message, int panelSize) {
    // Only fixed error codes and counts are logged, never prompts or answers.
    log.warn("fusion.response.rejected code={} panelSize={}", code, panelSize);
    return new ApiException(502, code, message);
  }

  private ApiException upstreamStatus(int status) {
    log.warn("fusion.upstream.rejected httpStatus={}", status);
    return switch (status) {
      case 401, 403 ->
          new ApiException(
              502, "FUSION_AUTH_FAILED", "FreeLLMAPI 인증에 실패했습니다. 관리자에게 통합 API 키 확인을 요청해 주세요.");
      case 404 ->
          new ApiException(
              502,
              "FUSION_ENDPOINT_NOT_FOUND",
              "FreeLLMAPI의 요청 경로를 찾지 못했습니다. 관리자에게 API 주소 확인을 요청해 주세요.");
      case 429 ->
          new ApiException(429, "FUSION_RATE_LIMITED", "상위 모델의 호출 한도에 도달했습니다. 잠시 후 다시 시도해 주세요.");
      default ->
          new ApiException(502, "FUSION_UPSTREAM_FAILED", "융합 요청에 실패했습니다. 잠시 후 직접 재시도해 주세요.");
    };
  }

  public List<Map<String, String>> catalog() {
    try {
      var res =
          http.send(
              request("/models?execution_status=ready")
                  .timeout(Duration.ofSeconds(10))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofInputStream());
      try (var body = res.body()) {
        if (res.statusCode() != 200) throw upstreamStatus(res.statusCode());
        byte[] bytes = body.readNBytes(RESPONSE_LIMIT + 1);
        if (bytes.length > RESPONSE_LIMIT) throw new IOException();
        List<Map<String, String>> models = new ArrayList<>();
        for (JsonNode item : json.readTree(bytes).path("data")) {
          String id = item.path("id").asText();
          if (!id.isBlank()
              && !virtualModel(id)
              && item.path("execution_status").asText().equals("ready")
              && item.path("available").asBoolean(false))
            models.add(Map.of("id", id, "name", item.path("name").asText(id)));
        }
        return models;
      }
    } catch (ApiException e) {
      throw e;
    } catch (Exception e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      throw new ApiException(503, "CATALOG_UNAVAILABLE", "사용 가능한 모델 목록을 조회하지 못했습니다.");
    }
  }
}
