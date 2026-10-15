package com.modeldock;

import static com.modeldock.Models.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {
  final DockRepository repo;
  final ObjectMapper json;

  public SettingsService(DockRepository repo, ObjectMapper json) {
    this.repo = repo;
    this.json = json;
  }

  public AdminSettings get() {
    return repo.jdbc()
        .queryForObject(
            "SELECT * FROM runtime_settings WHERE id=1",
            (r, n) -> {
              try {
                return new AdminSettings(
                    json.readValue(r.getString("fusion_json"), FusionSettings.class),
                    r.getLong("service_daily_limit"),
                    r.getBoolean("share_service_budget"));
              } catch (Exception e) {
                throw new IllegalStateException("Invalid persisted fusion configuration");
              }
            });
  }

  @Transactional
  public AdminSettings save(AdminSettings s) {
    s.fusion().requireReady();
    repo.lock();
    try {
      repo.jdbc()
          .update(
              "UPDATE runtime_settings SET"
                  + " fusion_json=?,service_daily_limit=?,share_service_budget=? WHERE id=1",
              json.writeValueAsString(s.fusion()),
              s.serviceDailyLimit(),
              s.shareServiceBudget());
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize settings");
    }
    return get();
  }
}
