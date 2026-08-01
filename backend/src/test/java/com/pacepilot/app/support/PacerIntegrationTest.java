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
 * <p>Containers use the Testcontainers "singleton container" pattern: they are started once in a
 * static initializer and never stopped by the JUnit lifecycle (no {@code @Testcontainers} /
 * {@code @Container}), so they stay alive and keep the same host ports across every subclass in the
 * JVM. Ryuk reaps them when the JVM exits. This matters because Spring caches the application
 * context by configuration, and a cached {@code RabbitTemplate} would otherwise point at a
 * container that a per-class lifecycle had already stopped, causing "Connection refused" in later
 * test classes.
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
