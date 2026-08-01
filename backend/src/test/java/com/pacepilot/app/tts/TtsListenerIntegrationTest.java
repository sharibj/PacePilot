package com.pacepilot.app.tts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.pacepilot.app.messaging.PacerTopology;
import com.pacepilot.app.messaging.dto.CueTextEvent;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class TtsListenerIntegrationTest {

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @Container @ServiceConnection
  static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.13-management-alpine");

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry registry) {
    registry.add("spring.ai.openai.api-key", () -> "test-key");
    registry.add("spring.ai.openai.base-url", () -> "http://localhost:4000");
    registry.add("app.cors.allowed-origin", () -> "*");
    // This test drives the TTS stage directly, so its listener must be running.
    registry.add("pacer.listener.tts.enabled", () -> "true");
  }

  @LocalServerPort int port;

  @Autowired RabbitTemplate rabbitTemplate;
  @Autowired com.pacepilot.app.realtime.SessionRegistry sessionRegistry;

  private static final String SESSION_ID = "8e5c1b4a-73d1-4e6e-9051-ceb6a45f611a";

  @Test
  void cueOnQueueIsDeliveredToConnectedSimulator() throws Exception {
    CopyOnWriteArrayList<String> received = new CopyOnWriteArrayList<>();

    StandardWebSocketClient client = new StandardWebSocketClient();
    WebSocketSession session =
        client
            .execute(
                new TextWebSocketHandler() {
                  @Override
                  protected void handleTextMessage(WebSocketSession s, TextMessage message) {
                    received.add(message.getPayload());
                  }
                },
                new WebSocketHttpHeaders(),
                URI.create("ws://localhost:" + port + "/ws/telemetry"))
            .get(5, TimeUnit.SECONDS);

    // Register the session with the backend by sending one telemetry frame, then
    // wait until the backend has actually registered it before publishing a cue.
    session.sendMessage(new TextMessage(sampleTelemetryJson()));
    await().atMost(Duration.ofSeconds(5)).until(() -> sessionRegistry.isConnected(SESSION_ID));

    rabbitTemplate.convertAndSend(
        PacerTopology.EXCHANGE,
        PacerTopology.RK_CUE_TEXT,
        new CueTextEvent(
            "evt-tts-1", SESSION_ID, "Ease back slightly, then settle to tempo.", "medium", 20));

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(received).anyMatch(m -> m.contains("pacer_audio"));
              assertThat(received)
                  .anyMatch(m -> m.contains("Ease back slightly, then settle to tempo."));
            });

    session.close();
  }

  private static String sampleTelemetryJson() {
    return """
        {
          "event_id": "3d6fcb36-f66f-4c43-b0a4-3af54c730f57",
          "session_id": "%s",
          "status": "running",
          "timestamp": "2026-08-01T09:34:00Z",
          "duration_seconds": 1245.8,
          "activity_type": "running",
          "step_count": 1567,
          "speed_mps": 5.2,
          "distance_m": 4210.5,
          "metrics": {
            "heart_rate": { "value": 154.0, "unit": "count/min", "zone": 3 },
            "pace": { "current_pace_seconds_per_meter": 0.31, "unit": "min/mi" }
          }
        }
        """
        .formatted(SESSION_ID);
  }
}
