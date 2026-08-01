package com.pacepilot.app.n8n;

import static org.assertj.core.api.Assertions.assertThat;

import com.pacepilot.app.messaging.dto.AggregatedEvent;
import com.pacepilot.app.messaging.dto.CueTextEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

/**
 * Verifies how {@link PacerCueService} chooses between the n8n resolver and the deterministic
 * fallback. The n8n HTTP call itself is covered by {@link N8nCueResolverTest}; here the resolver is
 * a thin stub so we can assert the branch logic without a network.
 */
class PacerCueServiceResolverTest {

  private static final int COOLDOWN_SECONDS = 20;
  private static final int MAX_HR = 190;

  private final Clock clock =
      Clock.fixed(java.time.Instant.parse("2026-08-01T09:00:00Z"), ZoneOffset.UTC);

  private AggregatedEvent event() {
    return new AggregatedEvent(
        "e1",
        "s1",
        "2026-08-01T09:00:00Z",
        30,
        "pace_drift",
        new AggregatedEvent.Summary(322, 150, 2, 150.0),
        new AggregatedEvent.RunContext("tempo", 300, 2800.0));
  }

  /** A resolver stub whose {@link #resolve} return value is scripted per test. */
  private static N8nCueResolver stubResolver(CueTextEvent scripted) {
    return new N8nCueResolver(RestClient.builder(), new SimpleMeterRegistry(), "http://unused") {
      @Override
      public CueTextEvent resolve(AggregatedEvent event) {
        return scripted;
      }
    };
  }

  @Test
  void usesN8nCueWhenResolverSucceeds() {
    List<CueTextEvent> published = new ArrayList<>();
    CueTextEvent aiCue = new CueTextEvent("e1", "s1", "AI: ease back to tempo.", "medium", 20);
    PacerCueService service =
        new PacerCueService(
            new FallbackCuePolicy(MAX_HR),
            stubResolver(aiCue),
            published::add,
            clock,
            COOLDOWN_SECONDS,
            new SimpleMeterRegistry());

    service.handle(event());

    assertThat(published).hasSize(1);
    assertThat(published.get(0).cue()).isEqualTo("AI: ease back to tempo.");
  }

  @Test
  void fallsBackToDeterministicCueWhenResolverReturnsNull() {
    List<CueTextEvent> published = new ArrayList<>();
    PacerCueService service =
        new PacerCueService(
            new FallbackCuePolicy(MAX_HR),
            stubResolver(null),
            published::add,
            clock,
            COOLDOWN_SECONDS,
            new SimpleMeterRegistry());

    service.handle(event());

    assertThat(published).hasSize(1);
    // The deterministic pace_drift cue, not an AI one.
    assertThat(published.get(0).cue()).isEqualTo("Pick it up slightly to reach your tempo pace.");
  }

  @Test
  void suppressesWhenN8nReturnsBlankCue() {
    List<CueTextEvent> published = new ArrayList<>();
    CueTextEvent silent = new CueTextEvent("e1", "s1", "", "low", 15);
    PacerCueService service =
        new PacerCueService(
            new FallbackCuePolicy(MAX_HR),
            stubResolver(silent),
            published::add,
            clock,
            COOLDOWN_SECONDS,
            new SimpleMeterRegistry());

    service.handle(event());

    // Blank cue from a successful n8n call means "do not speak" — no fallback, nothing published.
    assertThat(published).isEmpty();
  }
}
