package com.modeldock;

import static com.modeldock.Models.*;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConversationService {
  final DockRepository repo;
  final Clock clock;

  public ConversationService(DockRepository repo, Clock clock) {
    this.repo = repo;
    this.clock = clock;
  }

  @Transactional
  public Conversation create(String owner) {
    repo.requireApproved(owner);
    String id = UUID.randomUUID().toString();
    var now = clock.instant();
    repo.jdbc()
        .update(
            "INSERT INTO conversations(id,owner_id,title,created_at,updated_at) VALUES (?,?,'새"
                + " 대화',?,?)",
            id,
            owner,
            Timestamp.from(now),
            Timestamp.from(now));
    return new Conversation(id, "새 대화", now);
  }

  @Transactional
  public void title(String owner, String id, String title) {
    repo.own(owner, id);
    repo.jdbc()
        .update(
            "UPDATE conversations SET title=?,updated_at=? WHERE id=? AND owner_id=?",
            title.strip(),
            Timestamp.from(clock.instant()),
            id,
            owner);
  }

  @Transactional
  public void delete(String owner, String id) {
    repo.lock();
    repo.own(owner, id);
    if (repo.jdbc()
            .queryForObject(
                "SELECT COUNT(*) FROM generations WHERE conversation_id=? AND status IN"
                    + " ('RESERVED','RUNNING')",
                Long.class,
                id)
        > 0) throw new ApiException(409, "GENERATION_ACTIVE", "진행 중인 생성을 먼저 중지해 주세요.");
    repo.jdbc().update("DELETE FROM messages WHERE conversation_id=?", id);
    repo.jdbc().update("UPDATE conversations SET title='삭제된 대화',deleted=TRUE WHERE id=?", id);
  }
}
