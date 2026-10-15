package com.modeldock;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("dock")
public record DockProperties(
    String apiUrl,
    String apiKey,
    long userDailyLimit,
    long serviceDailyLimit,
    String bootstrapEmail,
    String bootstrapPassword) {
  public DockProperties {
    apiKey = apiKey == null ? "" : apiKey;
    bootstrapEmail = bootstrapEmail == null ? "" : bootstrapEmail;
    bootstrapPassword = bootstrapPassword == null ? "" : bootstrapPassword;
    if (apiUrl == null || (!apiUrl.startsWith("http://") && !apiUrl.startsWith("https://")))
      throw new IllegalArgumentException("An HTTP(S) FreeLLMAPI base URL is required");
    if (userDailyLimit < 0 || serviceDailyLimit < 0)
      throw new IllegalArgumentException("Budget limits must be non-negative");
  }
}
