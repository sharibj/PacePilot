package com.pacepilot.app.n8n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.pacepilot.app.messaging.PacerTopology;
import com.pacepilot.app.messaging.dto.AggregatedEvent;
import com.pacepilot.app.messaging.dto.CueTextEvent;
import com.pacepilot.app.support.PacerIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * End-to-end check that an aggregated event flowing through the broker is turned into a cue by the
 * {@link PacerCueService} listener and published to {@code cue.text}, and that cooldown suppresses
 * an immediate follow-up for the same session.
 *
 * <p>Only the n8n handler listener is enabled here; the downstream TTS listener stays off so the
 * cue remains on {@code cue.text} for this test to read.
 */
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PacerCueServiceIntegrationTest extends PacerIntegrationTest {

  @DynamicPropertySource
  static void enableListener(DynamicPropertyRegistry registry) {
    registry.add("pacer.listener.n8n.enabled", () -> "true");
  }

  @Autowired RabbitTemplate rabbitTemplate;

  private AggregatedEvent paceDrift(String eventId, String sessionId) {
    return new AggregatedEvent(
        eventId,
        sessionId,
        "2026-08-01T09:34:30Z",
        30,
        "pace_drift",
        new AggregatedEvent.Summary(322, 168, 9, 150.0),
        new AggregatedEvent.RunContext("tempo", 300, 2800.0));
  }

  @Test
  void aggregatedEventProducesCueAndCooldownSuppressesTheNext() {
    String sessionId = UUID.randomUUID().toString();
    String firstEventId = UUID.randomUUID().toString();

    rabbitTemplate.convertAndSend(
        PacerTopology.EXCHANGE,
        PacerTopology.RK_TELEMETRY_AGGREGATED,
        paceDrift(firstEventId, sessionId));

    await()
        .atMost(Duration.ofSeconds(10))
        .untilAsserted(
            () -> {
              CueTextEvent cue =
                  (CueTextEvent) rabbitTemplate.receiveAndConvert(PacerTopology.Q_CUE_TEXT, 500);
              assertThat(cue).isNotNull();
              assertThat(cue.eventId()).isEqualTo(firstEventId);
              assertThat(cue.sessionId()).isEqualTo(sessionId);
              assertThat(cue.cue()).isNotBlank();
              assertThat(cue.cue().length()).isLessThan(140);
            });

    // Second event for the SAME session immediately -> cooldown must suppress it.
    String secondEventId = UUID.randomUUID().toString();
    rabbitTemplate.convertAndSend(
        PacerTopology.EXCHANGE,
        PacerTopology.RK_TELEMETRY_AGGREGATED,
        paceDrift(secondEventId, sessionId));

    // Poll the cue queue for a short window; a suppressed cue means nothing ever arrives. Short,
    // non-blocking receives (rather than one long blocking receive) keep the broker connection
    // from being held open across container/context recycling in the shared test suite.
    long deadline = System.currentTimeMillis() + 2000;
    CueTextEvent suppressed = null;
    while (System.currentTimeMillis() < deadline && suppressed == null) {
      suppressed = (CueTextEvent) rabbitTemplate.receiveAndConvert(PacerTopology.Q_CUE_TEXT, 200);
    }
    assertThat(suppressed).isNull();
  }
}
