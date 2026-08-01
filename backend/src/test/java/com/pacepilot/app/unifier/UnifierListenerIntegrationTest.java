package com.pacepilot.app.unifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.pacepilot.app.messaging.PacerTopology;
import com.pacepilot.app.messaging.dto.TelemetryEvent;
import com.pacepilot.app.support.PacerIntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

class UnifierListenerIntegrationTest extends PacerIntegrationTest {

  @Autowired RabbitTemplate rabbitTemplate;

  private static TelemetryEvent validEvent(String eventId) {
    return new TelemetryEvent(
        eventId,
        "8e5c1b4a-73d1-4e6e-9051-ceb6a45f611a",
        "running",
        "2026-08-01T09:34:00Z",
        1245.8,
        "running",
        1567L,
        5.2,
        4210.5,
        Map.of("device_name", "simulator"),
        new TelemetryEvent.Metrics(
            new TelemetryEvent.HeartRate(154.0, "count/min", 3),
            new TelemetryEvent.Pace(0.31, "08:18", "min/mi")),
        new TelemetryEvent.Location(52.520008, 13.404954, 34.2, 3.2));
  }

  @Test
  void validRawEventIsEnrichedAndPublishedToCanonical() {
    TelemetryEvent raw = validEvent("3d6fcb36-f66f-4c43-b0a4-3af54c730f57");

    rabbitTemplate.convertAndSend(PacerTopology.EXCHANGE, PacerTopology.RK_TELEMETRY_RAW, raw);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              TelemetryEvent canonical =
                  (TelemetryEvent)
                      rabbitTemplate.receiveAndConvert(PacerTopology.Q_TELEMETRY_CANONICAL, 500);
              assertThat(canonical).isNotNull();
              assertThat(canonical.eventId()).isEqualTo(raw.eventId());
              assertThat(canonical.sessionId()).isEqualTo(raw.sessionId());
              assertThat(canonical.status()).isEqualTo("running");
              assertThat(canonical.metadata()).isNotNull();
              assertThat(canonical.metadata()).containsEntry("source", "unifier");
              // Existing metadata keys are preserved.
              assertThat(canonical.metadata()).containsEntry("device_name", "simulator");
              // Schema is otherwise unchanged.
              assertThat(canonical.metrics().heartRate().value()).isEqualTo(154.0);
              assertThat(canonical.metrics().pace().currentPaceSecondsPerMeter()).isEqualTo(0.31);
            });
  }

  @Test
  void invalidRawEventIsDeadLettered() {
    // Missing session_id => required-field validation fails => reject to DLQ.
    TelemetryEvent invalid =
        new TelemetryEvent(
            "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
            null,
            "running",
            "2026-08-01T09:34:00Z",
            1245.8,
            "running",
            1567L,
            5.2,
            4210.5,
            Map.of("device_name", "simulator"),
            new TelemetryEvent.Metrics(
                new TelemetryEvent.HeartRate(154.0, "count/min", 3),
                new TelemetryEvent.Pace(0.31, "08:18", "min/mi")),
            new TelemetryEvent.Location(52.520008, 13.404954, 34.2, 3.2));

    rabbitTemplate.convertAndSend(PacerTopology.EXCHANGE, PacerTopology.RK_TELEMETRY_RAW, invalid);

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              Message deadLettered = rabbitTemplate.receive(PacerTopology.Q_DEADLETTER, 500);
              assertThat(deadLettered).isNotNull();
            });
  }
}
