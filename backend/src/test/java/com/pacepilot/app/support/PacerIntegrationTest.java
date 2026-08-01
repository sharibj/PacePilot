package com.pacepilot.app.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base class for pacer integration tests. Boots the full Spring context against real Postgres and
 * RabbitMQ containers via {@link ServiceConnection}, so tests exercise the actual broker topology
 * rather than mocks. Subclasses add their own {@code @Test} methods.
 */
@SpringBootTest
@Testcontainers
public abstract class PacerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

  @DynamicPropertySource
  static void llmProps(DynamicPropertyRegistry registry) {
    registry.add("spring.ai.openai.api-key", () -> "test-key");
    registry.add("spring.ai.openai.base-url", () -> "http://localhost:4000");
  }
}
