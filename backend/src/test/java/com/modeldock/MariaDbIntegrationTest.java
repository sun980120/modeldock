package com.modeldock;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Tag;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;

@Tag("mariadb")
class MariaDbIntegrationTest extends ModelDockIntegrationTest {
  static MariaDBContainer<?> database;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    String external = System.getenv("MODELDOCK_TEST_DATABASE_URL");
    if (external != null && !external.isBlank()) {
      if (!external.matches("jdbc:mariadb://[^/]+/modeldock_test_[A-Za-z0-9]+(?:\\?.*)?"))
        throw new IllegalArgumentException(
            "External integration tests require a disposable modeldock_test_* database");
      properties.add("spring.datasource.url", () -> external);
      properties.add(
          "spring.datasource.username",
          () -> System.getenv().getOrDefault("MODELDOCK_TEST_DATABASE_USER", "root"));
      properties.add(
          "spring.datasource.password",
          () -> System.getenv().getOrDefault("MODELDOCK_TEST_DATABASE_PASSWORD", ""));
    } else {
      database = new MariaDBContainer<>("mariadb:11.4");
      database.start();
      properties.add("spring.datasource.url", database::getJdbcUrl);
      properties.add("spring.datasource.username", database::getUsername);
      properties.add("spring.datasource.password", database::getPassword);
    }
  }

  @AfterAll
  static void stopDatabase() {
    if (database != null) database.stop();
  }
}
