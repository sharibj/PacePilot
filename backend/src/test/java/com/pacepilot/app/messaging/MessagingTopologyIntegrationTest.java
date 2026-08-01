package com.pacepilot.app.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.pacepilot.app.messaging.dto.AggregatedEvent;
import com.pacepilot.app.messaging.dto.TelemetryEvent;
import com.pacepilot.app.support.PacerIntegrationTest;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

class MessagingTopologyIntegrationTest extends PacerIntegrationTest {

  @Autowired RabbitTemplate rabbitTemplate;
  @Autowired AmqpAdmin amqpAdmin;

  @Test
  void allQueuesAreDeclaredAtStartup() {
    assertThat(amqpAdmin.getQueueProperties(PacerTopology.Q_TELEMETRY_RAW)).isNotNull();
    assertThat(amqpAdmin.getQueueProperties(PacerTopology.Q_TELEMETRY_CANONICAL)).isNotNull();
    assertThat(amqpAdmin.getQueueProperties(PacerTopology.Q_TELEMETRY_AGGREGATED)).isNotNull();
    assertThat(amqpAdmin.getQueueProperties(PacerTopology.Q_CUE_TEXT)).isNotNull();
    assertThat(amqpAdmin.getQueueProperties(PacerTopology.Q_DEADLETTER)).isNotNull();
  }

  @Test
  void telemetryEventRoundTripsThroughBroker() {
    TelemetryEvent sent =
        new TelemetryEvent(
            "3d6fcb36-f66f-4c43-b0a4-3af54c730f57",
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

    // The unifier now consumes pacer.telemetry.raw.q, so we cannot poll that queue directly for a
    // round-trip assertion. Route through the aggregated queue instead (no consumer yet) — this
    // still proves TelemetryEvent JSON (de)serialization and topic-exchange routing.
    rabbitTemplate.convertAndSend(
        PacerTopology.EXCHANGE, PacerTopology.RK_TELEMETRY_AGGREGATED, sent);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              TelemetryEvent received =
                  (TelemetryEvent)
                      rabbitTemplate.receiveAndConvert(PacerTopology.Q_TELEMETRY_AGGREGATED, 500);
              assertThat(received).isNotNull();
              assertThat(received.eventId()).isEqualTo(sent.eventId());
              assertThat(received.sessionId()).isEqualTo(sent.sessionId());
              assertThat(received.status()).isEqualTo("running");
              assertThat(received.metrics().heartRate().value()).isEqualTo(154.0);
              assertThat(received.metrics().pace().currentPaceSecondsPerMeter()).isEqualTo(0.31);
            });
  }

  @Test
  void aggregatedEventRoundTripsThroughBroker() {
    AggregatedEvent sent =
        new AggregatedEvent(
            "f0bf8fbf-1f58-4856-b74a-5501f5ec74ce",
            "8e5c1b4a-73d1-4e6e-9051-ceb6a45f611a",
            "2026-08-01T09:34:30Z",
            30,
            "pace_drift",
            new AggregatedEvent.Summary(322, 168, 9, 150.0),
            new AggregatedEvent.RunContext("tempo", 300, 2800.0));

    rabbitTemplate.convertAndSend(
        PacerTopology.EXCHANGE, PacerTopology.RK_TELEMETRY_AGGREGATED, sent);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              AggregatedEvent received =
                  (AggregatedEvent)
                      rabbitTemplate.receiveAndConvert(PacerTopology.Q_TELEMETRY_AGGREGATED, 500);
              assertThat(received).isNotNull();
              assertThat(received.eventType()).isEqualTo("pace_drift");
              assertThat(received.summary().avgPaceSecPerKm()).isEqualTo(322);
              assertThat(received.runContext().targetPaceSecPerKm()).isEqualTo(300);
            });
  }
}
