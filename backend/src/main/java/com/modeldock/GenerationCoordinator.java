package com.modeldock;

import static com.modeldock.Models.*;

import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class GenerationCoordinator {
  final BudgetService budgets;
  final DockRepository repo;
  final FusionGateway gateway;
  final ExecutorService executor;
  final Clock clock;
  final ConcurrentMap<String, FusionGateway.Flight> active = new ConcurrentHashMap<>();

  public GenerationCoordinator(
      BudgetService budgets,
      DockRepository repo,
      FusionGateway gateway,
      ExecutorService executor,
      Clock clock) {
    this.budgets = budgets;
    this.repo = repo;
    this.gateway = gateway;
    this.executor = executor;
    this.clock = clock;
  }

  public void launch(BudgetService.Reservation reservation) {
    var g = reservation.generation();
    try {
      executor.submit(
          () -> {
            var flight =
                new FusionGateway.Flight(
                    clock.instant().plusSeconds(reservation.settings().timeoutSeconds()));
            active.put(g.id(), flight);
            try {
              if (!budgets.running(g.id())) {
                budgets.settle(g.id(), "CANCELLED", null, null, "CANCELLED");
                return;
              }
              var result = gateway.complete(reservation.history(), reservation.settings(), flight);
              if (!clock.instant().isBefore(flight.deadline)) throw new TimeoutException();
              budgets.settle(g.id(), "COMPLETED", result.answer(), result.reportedTokens(), null);
            } catch (Exception e) {
              boolean timedOut =
                  !clock.instant().isBefore(flight.deadline)
                      || e instanceof TimeoutException
                      || e instanceof java.net.http.HttpTimeoutException
                      || e.getCause() instanceof java.net.http.HttpTimeoutException;
              String code =
                  timedOut
                      ? "GENERATION_TIMEOUT"
                      : flight.cancelled
                          ? "CANCELLED"
                          : (e instanceof ApiException a ? a.code : "FUSION_UPSTREAM_FAILED");
              budgets.settle(
                  g.id(), code.equals("CANCELLED") ? "CANCELLED" : "FAILED", null, null, code);
            } finally {
              active.remove(g.id());
              flight.close();
            }
          });
    } catch (RejectedExecutionException e) {
      budgets.settle(g.id(), "FAILED", null, null, "SERVICE_UNAVAILABLE");
    }
  }

  @Scheduled(fixedDelay = 1000)
  public void supervise() {
    for (var entry : active.entrySet()) {
      var g = repo.generation(entry.getKey());
      if (g.cancelRequested()
          || !clock.instant().isBefore(entry.getValue().deadline)
          || !List.of("RESERVED", "RUNNING").contains(g.status())
          || !repo.user(g.ownerId()).map(u -> u.status().equals("APPROVED")).orElse(false))
        entry.getValue().close();
    }
    budgets.recoverExpired();
  }

  public SseEmitter events(String owner, String conversation, String id) {
    repo.ownGeneration(owner, conversation, id);
    SseEmitter emitter = new SseEmitter(300000L);
    var closed = new java.util.concurrent.atomic.AtomicBoolean();
    emitter.onCompletion(() -> closed.set(true));
    emitter.onTimeout(() -> closed.set(true));
    emitter.onError(e -> closed.set(true));
    executor.submit(
        () -> {
          String last = "";
          int ticks = 0;
          try {
            while (!closed.get()) {
              repo.requireApproved(owner);
              Generation g = repo.ownGeneration(owner, conversation, id);
              if (!g.status().equals(last)) {
                String event =
                    switch (g.status()) {
                      case "RESERVED" -> "accepted";
                      case "RUNNING" -> "generating";
                      case "COMPLETED" -> "completed";
                      default -> "failed";
                    };
                emitter.send(
                    SseEmitter.event()
                        .id(g.status())
                        .name(event)
                        .data(
                            Map.of(
                                "id",
                                id,
                                "status",
                                g.status(),
                                "code",
                                g.errorCode() == null ? "" : g.errorCode())));
                last = g.status();
              }
              if (!List.of("RESERVED", "RUNNING").contains(g.status())) {
                emitter.complete();
                return;
              }
              if (++ticks % 15 == 0) emitter.send(SseEmitter.event().comment("keepalive"));
              Thread.sleep(1000);
            }
          } catch (Exception e) {
            emitter.complete();
          }
        });
    return emitter;
  }
}
