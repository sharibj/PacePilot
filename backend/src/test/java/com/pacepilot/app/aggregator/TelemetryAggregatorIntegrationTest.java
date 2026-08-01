package com.pacepilot.app.aggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.pacepilot.app.messaging.PacerTopology;
import com.pacepilot.app.messaging.dto.AggregatedEvent;
import com.pacepilot.app.messaging.dto.TelemetryEvent;
import com.pacepilot.app.support.PacerIntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Integration test that drives the {@code TelemetryAggregator} listener over a real RabbitMQ
 * broker. A telemetry sequence with an abrupt status change should surface exactly one aggregated
 * event on {@code telemetry.aggregated}; a steady sequence should surface nothing (suppression).
 */
class TelemetryAggregatorIntegrationTest extends PacerIntegrationTest {

  @Autowired RabbitTemplate rabbitTemplate;

  private static TelemetryEvent canonical(
      String sessionId, Instant ts, String status, double paceSecPerMeter, double hr, double dist) {
    return new TelemetryEvent(
        UUID.randomUUID().toString(),
        sessionId,
        status,
        ts.toString(),
        0.0,
        status,
        0L,
        1.0 / paceSecPerMeter,
        dist,
        null,
        new TelemetryEvent.Metrics(
            new TelemetryEvent.HeartRate(hr, "count/min", 3),
            new TelemetryEvent.Pace(paceSecPerMeter, null, "min/mi")),
        null);
  }

  private void publish(TelemetryEvent event) {
    rabbitTemplate.convertAndSend(
        PacerTopology.EXCHANGE, PacerTopology.RK_TELEMETRY_CANONICAL, event);
  }

  @Test
  void statusChangeSequence_producesExactlyOneAggregatedEvent() {
    String sessionId = UUID.randomUUID().toString();
    Instant base = Instant.parse("2026-08-01T09:34:00Z");

    // Steady running, then an abrupt flip to walking on the third sample.
    publish(canonical(sessionId, base, "running", 0.30, 150.0, 0.0));
    publish(canonical(sessionId, base.plusSeconds(1), "running", 0.30, 150.0, 3.3));
    publish(canonical(sessionId, base.plusSeconds(2), "walking", 0.65, 149.0, 5.0));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              AggregatedEvent received =
                  (AggregatedEvent)
                      rabbitTemplate.receiveAndConvert(PacerTopology.Q_TELEMETRY_AGGREGATED, 500);
              assertThat(received).isNotNull();
              assertThat(received.sessionId()).isEqualTo(sessionId);
              assertThat(received.eventType()).isEqualTo("state_change");
              assertThat(received.windowSeconds()).isEqualTo(30);
            });

    // No further aggregated events should be sitting on the queue for this short sequence.
    Object leftover = rabbitTemplate.receiveAndConvert(PacerTopology.Q_TELEMETRY_AGGREGATED, 500);
    assertThat(leftover).isNull();
  }

  @Test
  void stableSequence_producesNoAggregatedEvent() {
    String sessionId = UUID.randomUUID().toString();
    Instant base = Instant.parse("2026-08-01T10:00:00Z");

    for (int i = 0; i < 6; i++) {
      publish(canonical(sessionId, base.plusSeconds(i), "running", 0.30, 150.0 + (i % 2), i * 3.3));
    }

    // Give the listener time to process, then confirm nothing was emitted.
    Object received = rabbitTemplate.receiveAndConvert(PacerTopology.Q_TELEMETRY_AGGREGATED, 2000);
    assertThat(received).isNull();
  }
}
