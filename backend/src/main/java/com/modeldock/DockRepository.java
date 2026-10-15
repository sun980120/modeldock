package com.modeldock;

import static com.modeldock.Models.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class DockRepository {
  final JdbcTemplate db;

  public DockRepository(JdbcTemplate db) {
    this.db = db;
  }

  public JdbcTemplate jdbc() {
    return db;
  }

  static Instant instant(ResultSet r, String column) throws SQLException {
    return r.getTimestamp(column).toInstant();
  }

  static final RowMapper<User> USER =
      (r, n) ->
          new User(
              r.getString("id"),
              r.getString("email"),
              r.getString("display_name"),
              r.getString("password_hash"),
              r.getString("role"),
              r.getString("status"),
              r.getLong("daily_limit"));
  static final RowMapper<Generation> GENERATION =
      (r, n) ->
          new Generation(
              r.getString("id"),
              r.getString("conversation_id"),
              r.getString("owner_id"),
              r.getString("status"),
              r.getLong("reserved_tokens"),
              (Long) r.getObject("reported_tokens"),
              r.getString("error_code"),
              instant(r, "expires_at"),
              r.getBoolean("cancel_requested"));

  public Optional<User> user(String id) {
    return db.query("SELECT * FROM app_users WHERE id=?", USER, id).stream().findFirst();
  }

  public Optional<User> email(String email) {
    return db.query("SELECT * FROM app_users WHERE email=?", USER, email).stream().findFirst();
  }

  public void insertUser(User u, Instant now) {
    db.update(
        "INSERT INTO"
            + " app_users(id,email,display_name,password_hash,role,status,daily_limit,created_at)"
            + " VALUES (?,?,?,?,?,?,?,?)",
        u.id(),
        u.email(),
        u.displayName(),
        u.passwordHash(),
        u.role(),
        u.status(),
        u.dailyLimit(),
        Timestamp.from(now));
  }

  public User requireApproved(String id) {
    User u = user(id).orElseThrow(() -> new ApiException(401, "UNAUTHENTICATED", "로그인이 필요합니다."));
    if (!u.status().equals("APPROVED"))
      throw new ApiException(403, "ACCOUNT_" + u.status(), "관리자 승인이 필요하거나 이용이 정지된 계정입니다.");
    return u;
  }

  public void own(String owner, String conversation) {
    if (db.queryForObject(
            "SELECT COUNT(*) FROM conversations WHERE id=? AND owner_id=? AND deleted=FALSE",
            Long.class,
            conversation,
            owner)
        != 1) throw ApiException.missing();
  }

  public List<Conversation> conversations(String owner, String query, int offset) {
    return db.query(
        "SELECT id,title,updated_at FROM conversations WHERE owner_id=? AND deleted=FALSE AND"
            + " LOWER(title) LIKE ? ESCAPE '!' ORDER BY updated_at DESC,id DESC LIMIT 50 OFFSET ?",
        (r, n) -> new Conversation(r.getString(1), r.getString(2), instant(r, "updated_at")),
        owner,
        "%"
            + query
                .toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_")
            + "%",
        offset);
  }

  public List<Message> messages(String owner, String conversation, String before) {
    own(owner, conversation);
    Long cursor =
        before == null
            ? Long.MAX_VALUE
            : db
                .query(
                    "SELECT seq FROM messages WHERE id=? AND conversation_id=?",
                    (r, n) -> r.getLong(1),
                    before,
                    conversation)
                .stream()
                .findFirst()
                .orElseThrow(ApiException::missing);
    List<Message> out =
        new ArrayList<>(
            db.query(
                "SELECT m.*,g.status,g.error_code FROM messages m JOIN generations g ON"
                    + " g.id=m.generation_id WHERE m.conversation_id=? AND m.seq<? ORDER BY m.seq"
                    + " DESC LIMIT 100",
                (r, n) ->
                    new Message(
                        r.getString("id"),
                        r.getString("role"),
                        r.getString("content"),
                        r.getString("generation_id"),
                        r.getString("status"),
                        r.getString("error_code")),
                conversation,
                cursor));
    Collections.reverse(out);
    return out;
  }

  public List<History> history(String conversation) {
    long chars =
        db.queryForObject(
            "SELECT COALESCE(SUM(CHAR_LENGTH(m.content)),0) FROM messages m JOIN generations g ON"
                + " g.id=m.generation_id WHERE m.conversation_id=? AND g.status='COMPLETED'",
            Long.class,
            conversation);
    if (chars > 100000)
      throw new ApiException(413, "CONTEXT_TOO_LONG", "대화가 너무 깁니다. 새 대화를 시작해 주세요.");
    return db.query(
        "SELECT m.role,m.content FROM messages m JOIN generations g ON g.id=m.generation_id WHERE"
            + " m.conversation_id=? AND g.status='COMPLETED' ORDER BY m.seq",
        (r, n) -> new History(r.getString(1), r.getString(2)),
        conversation);
  }

  public void message(
      String conversation, String generation, String role, String text, Instant now) {
    db.update(
        "INSERT INTO messages(id,conversation_id,generation_id,role,content,created_at) VALUES"
            + " (?,?,?,?,?,?)",
        UUID.randomUUID().toString(),
        conversation,
        generation,
        role,
        text,
        Timestamp.from(now));
  }

  public Generation generation(String id) {
    return db.query("SELECT * FROM generations WHERE id=?", GENERATION, id).stream()
        .findFirst()
        .orElseThrow(ApiException::missing);
  }

  public Generation ownGeneration(String owner, String conversation, String id) {
    own(owner, conversation);
    Generation g = generation(id);
    if (!g.ownerId().equals(owner) || !g.conversationId().equals(conversation))
      throw ApiException.missing();
    return g;
  }

  public void lock() {
    db.queryForObject("SELECT id FROM runtime_settings WHERE id=1 FOR UPDATE", Integer.class);
  }
}
