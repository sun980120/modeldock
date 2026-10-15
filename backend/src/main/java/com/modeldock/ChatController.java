package com.modeldock;

import static com.modeldock.Models.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.Principal;
import java.util.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@Validated
public class ChatController {
  final DockRepository repo;
  final ConversationService conversations;
  final BudgetService budgets;
  final GenerationCoordinator coordinator;
  final FusionGateway gateway;

  public ChatController(
      DockRepository repo,
      ConversationService conversations,
      BudgetService budgets,
      GenerationCoordinator coordinator,
      FusionGateway gateway) {
    this.repo = repo;
    this.conversations = conversations;
    this.budgets = budgets;
    this.coordinator = coordinator;
    this.gateway = gateway;
  }

  @GetMapping("/conversations")
  List<Conversation> list(
      Principal p,
      @RequestParam(defaultValue = "") @Size(max = 120) String q,
      @RequestParam(defaultValue = "0") @Min(0) @Max(100000) int offset) {
    return repo.conversations(p.getName(), q, offset);
  }

  @PostMapping("/conversations")
  Conversation create(Principal p) {
    return conversations.create(p.getName());
  }

  @PatchMapping("/conversations/{id}")
  Map<String, Boolean> title(
      Principal p, @PathVariable UUID id, @Valid @RequestBody Title request) {
    conversations.title(p.getName(), id.toString(), request.title());
    return Map.of("ok", true);
  }

  @DeleteMapping("/conversations/{id}")
  Map<String, Boolean> delete(Principal p, @PathVariable UUID id) {
    conversations.delete(p.getName(), id.toString());
    return Map.of("ok", true);
  }

  @GetMapping("/conversations/{id}/messages")
  List<Message> messages(
      Principal p, @PathVariable UUID id, @RequestParam(required = false) UUID before) {
    return repo.messages(p.getName(), id.toString(), before == null ? null : before.toString());
  }

  @PostMapping("/conversations/{id}/generations")
  Map<String, String> generate(
      Principal p, @PathVariable UUID id, @Valid @RequestBody Generate request) {
    gateway.assertConfigured();
    var reservation = budgets.reserve(p.getName(), id.toString(), request);
    coordinator.launch(reservation);
    return Map.of("id", reservation.generation().id(), "status", "RESERVED");
  }

  @GetMapping("/conversations/{id}/generations/{generation}")
  Generation generation(Principal p, @PathVariable UUID id, @PathVariable UUID generation) {
    return repo.ownGeneration(p.getName(), id.toString(), generation.toString());
  }

  @PostMapping("/conversations/{id}/generations/{generation}/cancel")
  Map<String, Boolean> cancel(Principal p, @PathVariable UUID id, @PathVariable UUID generation) {
    budgets.cancel(p.getName(), id.toString(), generation.toString());
    return Map.of("ok", true);
  }

  @GetMapping(
      value = "/conversations/{id}/generations/{generation}/events",
      produces = "text/event-stream")
  SseEmitter events(Principal p, @PathVariable UUID id, @PathVariable UUID generation) {
    return coordinator.events(p.getName(), id.toString(), generation.toString());
  }

  @GetMapping("/usage")
  Usage usage(Principal p) {
    return budgets.usage(p.getName());
  }

  @GetMapping("/usage/requests")
  List<Map<String, Object>> ledger(Principal p) {
    return repo.jdbc()
        .queryForList(
            "SELECT id,status,reserved_tokens,reported_tokens,charged_tokens,usage_quality FROM"
                + " generations WHERE owner_id=? ORDER BY created_at DESC LIMIT 30",
            p.getName());
  }

  @GetMapping("/generations/active")
  List<Map<String, Object>> active(Principal p) {
    return repo.jdbc()
        .queryForList(
            "SELECT id,conversation_id AS conversationId,status FROM generations WHERE owner_id=?"
                + " AND status IN ('RESERVED','RUNNING')",
            p.getName());
  }
}
