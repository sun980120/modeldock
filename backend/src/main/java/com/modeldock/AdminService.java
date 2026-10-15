package com.modeldock;

import static com.modeldock.Models.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
  final DockRepository repo;

  public AdminService(DockRepository repo) {
    this.repo = repo;
  }

  @Transactional
  public void update(String id, UserAction action) {
    repo.lock();
    User u = repo.user(id).orElseThrow(ApiException::missing);
    if (u.role().equals("ADMIN"))
      throw new ApiException(400, "ADMIN_IMMUTABLE", "관리자 계정은 이 화면에서 변경할 수 없습니다.");
    repo.jdbc()
        .update(
            "UPDATE app_users SET status=?,daily_limit=? WHERE id=?",
            action.status(),
            action.dailyLimit(),
            id);
    if (!action.status().equals("APPROVED"))
      repo.jdbc()
          .update(
              "UPDATE generations SET cancel_requested=TRUE WHERE owner_id=? AND status IN"
                  + " ('RESERVED','RUNNING')",
              id);
  }
}
