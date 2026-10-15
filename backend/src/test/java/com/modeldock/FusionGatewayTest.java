package com.modeldock;

import static com.modeldock.Models.*;
import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;

class FusionGatewayTest {
  final ObjectMapper json = new ObjectMapper();
  final FusionSettings config = new FusionSettings(List.of("a", "b", "c"), "judge", 1024, 10, 4);

  FusionGateway gateway(String url) {
    return new FusionGateway(
        HttpClient.newHttpClient(),
        json,
        new DockProperties(url, "test-key", 1000000, 10000000, "", ""));
  }

  String response(boolean synthesis, String panel, String usage) {
    return "{\"model\":\"fusion\",\"choices\":[{\"message\":{\"content\":\"합성"
        + " 답변\"},\"finish_reason\":\"stop\"}],\"_fusion\":{\"synthesized\":"
        + synthesis
        + ",\"judge\":{\"model\":\"judge\"},\"panel\":"
        + panel
        + "}"
        + usage
        + "}";
  }

  @Test
  void partialPanelSuccessCanSynthesizeAndUsageIsNotAddedTwice() throws Exception {
    var result =
        gateway("http://localhost")
            .validate(
                json.readTree(
                    response(
                        true,
                        "[{\"model\":\"a\"},{\"model\":\"b\"}]",
                        ",\"usage\":{\"total_tokens\":123}")),
                config);
    assertThat(result.answer()).isEqualTo("합성 답변");
    assertThat(result.reportedTokens()).isEqualTo(123);
  }

  @Test
  void canonicalCatalogAliasesCanResolveToProviderModelIds() throws Exception {
    var aliases =
        new FusionSettings(
            List.of("gemini-3.5-flash-lite", "nemotron-3.5-lightning-30b-a3b"),
            "gemini-3.5-flash-lite",
            1024,
            120,
            2);
    String body =
        """
        {"model":"fusion","choices":[{"message":{"role":"assistant","content":"검증용 답변"},"finish_reason":"stop"}],
         "_fusion":{"synthesized":true,
           "panel":[{"platform":"google","model":"gemini-3.5-flash-lite"},
                    {"platform":"nvidia","model":"nvidia/nemotron-3.5-lightning-30b-a3b"}],
           "judge":{"platform":"google","model":"gemini-3.5-flash-lite"}},
         "usage":{"prompt_tokens":400,"completion_tokens":494,"total_tokens":894}}
        """;
    assertThat(gateway("http://localhost").validate(json.readTree(body), aliases).reportedTokens())
        .isEqualTo(894);
  }

  @Test
  void canonicalJudgeAliasCanResolveToProviderModelId() throws Exception {
    var aliases =
        new FusionSettings(List.of("gemini", "gpt-oss-120b"), "gpt-oss-120b", 1024, 120, 2);
    String body =
        """
        {"model":"fusion","choices":[{"message":{"content":"검증용 답변"},"finish_reason":"stop"}],
         "_fusion":{"synthesized":true,
           "panel":[{"platform":"google","model":"gemini"},
                    {"platform":"groq","model":"openai/gpt-oss-120b"}],
           "judge":{"platform":"groq","model":"openai/gpt-oss-120b"}}}
        """;
    assertThat(gateway("http://localhost").validate(json.readTree(body), aliases).answer())
        .isEqualTo("검증용 답변");
  }

  @Test
  void failedQuorumJudgeAndIncompleteAnswerHaveDistinctErrors() throws Exception {
    var gateway = gateway("http://localhost");
    ApiException quorum =
        catchThrowableOfType(
            () ->
                gateway.validate(json.readTree(response(false, "[{\"model\":\"a\"}]", "")), config),
            ApiException.class);
    assertThat(quorum.code).isEqualTo("FUSION_QUORUM_NOT_MET");
    ApiException judge =
        catchThrowableOfType(
            () ->
                gateway.validate(
                    json.readTree(response(false, "[{\"model\":\"a\"},{\"model\":\"b\"}]", "")),
                    config),
            ApiException.class);
    assertThat(judge.code).isEqualTo("FUSION_JUDGE_FAILED");
    var incomplete = json.readTree(response(true, "[{\"model\":\"a\"},{\"model\":\"b\"}]", ""));
    ((com.fasterxml.jackson.databind.node.ObjectNode) incomplete.path("choices").path(0))
        .put("finish_reason", "length");
    ApiException truncated =
        catchThrowableOfType(() -> gateway.validate(incomplete, config), ApiException.class);
    assertThat(truncated.code).isEqualTo("FUSION_RESPONSE_INCOMPLETE");
    var missing = json.readTree("{\"model\":\"fusion\",\"choices\":[]}");
    assertThat(
            catchThrowableOfType(() -> gateway.validate(missing, config), ApiException.class).code)
        .isEqualTo("FUSION_METADATA_MISSING");
  }

  @Test
  void duplicatePanelEntriesDoNotSatisfyQuorum() throws Exception {
    var duplicated =
        json.readTree(
            response(
                true,
                "[{\"platform\":\"google\",\"model\":\"a\"},{\"platform\":\"google\",\"model\":\"a\"}]",
                ""));
    assertThat(
            catchThrowableOfType(
                    () -> gateway("http://localhost").validate(duplicated, config),
                    ApiException.class)
                .code)
        .isEqualTo("FUSION_METADATA_INVALID");
  }

  @Test
  void catalogExcludesVirtualSlotsAndUnavailableModels() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/models",
        exchange -> {
          byte[] body =
              """
              {"data":[
                {"id":"fusion","available":true},
                {"id":"auto","available":true},
                {"id":"claude-opus-4-5","name":"Opus slot","available":true},
                {"id":"ready-model","name":"실제 모델","available":true,"execution_status":"ready"},
                {"id":"exhausted-model","available":true,"execution_status":"exhausted"},
                {"id":"no-key-model","available":false,"execution_status":"needsKey"}]}
              """
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    try {
      assertThat(gateway("http://127.0.0.1:" + server.getAddress().getPort() + "/v1").catalog())
          .containsExactly(Map.of("id", "ready-model", "name", "실제 모델"));
    } finally {
      server.stop(0);
    }
  }

  @Test
  void singleModelJudgeFailureAndUnknownMetadataAreNeverSuccess() throws Exception {
    for (String body :
        List.of(
            response(false, "[{\"model\":\"a\"}]", ""),
            response(false, "[{\"model\":\"a\"},{\"model\":\"b\"}]", ""),
            response(true, "[]", ""),
            "{\"model\":\"single\"}"))
      assertThatThrownBy(() -> gateway("http://localhost").validate(json.readTree(body), config))
          .isInstanceOf(ApiException.class);
  }

  @Test
  void missingUsageRemainsUnknown() throws Exception {
    assertThat(
            gateway("http://localhost")
                .validate(
                    json.readTree(response(true, "[{\"model\":\"a\"},{\"model\":\"b\"}]", "")),
                    config)
                .reportedTokens())
        .isNull();
  }

  @Test
  void wireRequestUsesVerifiedFusionFieldsAndBufferedCompletion() throws Exception {
    AtomicReference<JsonNode> sent = new AtomicReference<>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          try {
            sent.set(json.readTree(exchange.getRequestBody()));
            byte[] body =
                response(
                        true,
                        "[{\"model\":\"a\"},{\"model\":\"b\"}]",
                        ",\"usage\":{\"total_tokens\":100}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
          } finally {
            exchange.close();
          }
        });
    server.start();
    try (var flight = new FusionGateway.Flight(Instant.now().plusSeconds(10))) {
      var result =
          gateway("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
              .complete(List.of(new History("user", "hello")), config, flight);
      assertThat(result.answer()).isNotEmpty();
      assertThat(sent.get().path("model").asText()).isEqualTo("fusion");
      assertThat(sent.get().path("stream").asBoolean()).isFalse();
      assertThat(sent.get().path("fusion").path("expose_panel").asBoolean()).isFalse();
      assertThat(sent.get().path("fusion").path("strategy").asText()).isEqualTo("synthesize");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void cancellationClosesInFlightHttpRequest() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var received = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          received.countDown();
          try {
            release.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    try (var flight = new FusionGateway.Flight(Instant.now().plusSeconds(10));
        var pool = Executors.newSingleThreadExecutor()) {
      var result =
          pool.submit(
              () ->
                  gateway("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                      .complete(List.of(new History("user", "hi")), config, flight));
      assertThat(received.await(2, TimeUnit.SECONDS)).isTrue();
      flight.close();
      assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS))
          .isInstanceOf(ExecutionException.class);
    } finally {
      release.countDown();
      server.stop(0);
    }
  }

  @Test
  void overallRequestDeadlineIsEnforced() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var release = new CountDownLatch(1);
    server.createContext(
        "/v1/chat/completions",
        exchange -> {
          try {
            release.await(3, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            exchange.close();
          }
        });
    server.start();
    var shortConfig = new FusionSettings(config.models(), config.judge(), 1024, 1, 1);
    try (var flight = new FusionGateway.Flight(Instant.now().plusSeconds(1))) {
      long started = System.nanoTime();
      assertThatThrownBy(
              () ->
                  gateway("http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                      .complete(List.of(new History("user", "hello")), shortConfig, flight))
          .isInstanceOfAny(ExecutionException.class, TimeoutException.class);
      assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(2));
    } finally {
      release.countDown();
      server.stop(0);
    }
  }
}
