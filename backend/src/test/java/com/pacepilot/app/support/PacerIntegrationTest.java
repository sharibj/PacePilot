package com.pacepilot.app.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Base class for pacer integration tests. Boots the full Spring context against real Postgres and
 * RabbitMQ containers via {@link ServiceConnection}, so tests exercise the actual broker topology
 * rather than mocks. Subclasses add their own {@code @Test} methods.
 *
 * <p>The containers are started once (singleton pattern) and shared across every integration test
 * class in the suite. They are intentionally never stopped in test code — Ryuk reaps them at JVM
 * exit. This keeps the Spring context (which is cached and reused across test classes) pointed at a
 * live broker; stopping per-class containers while the cached context lingers would surface as
 * "Connection refused" in later classes.
 */
@SpringBootTest
public abstract class PacerIntegrationTest {

  @ServiceConnection
  static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @ServiceConnection
  static final RabbitMQContainer rabbitmq =
      new RabbitMQContainer("rabbitmq:3.13-management-alpine");

  static {
    postgres.start();
    rabbitmq.start();
  }

  @DynamicPropertySource
  static void llmProps(DynamicPropertyRegistry registry) {
    registry.add("spring.ai.openai.api-key", () -> "test-key");
    registry.add("spring.ai.openai.base-url", () -> "http://localhost:4000");
  }
}
