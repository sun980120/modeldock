package com.modeldock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication(
    exclude =
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
            .class)
@EnableConfigurationProperties(DockProperties.class)
public class ModelDockApplication {
  public static void main(String[] args) {
    SpringApplication.run(ModelDockApplication.class, args);
  }
}
