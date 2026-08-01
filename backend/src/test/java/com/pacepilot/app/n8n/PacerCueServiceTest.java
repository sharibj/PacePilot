package com.pacepilot.app.n8n;

import static org.assertj.core.api.Assertions.assertThat;

import com.pacepilot.app.messaging.dto.AggregatedEvent;
import com.pacepilot.app.messaging.dto.CueTextEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PacerCueServiceTest {

  private static final int COOLDOWN_SECONDS = 20;
  private static final int MAX_HR = 190;

  private final List<CueTextEvent> published = new ArrayList<>();
  private final AtomicReference<Instant> now =
      new AtomicReference<>(Instant.parse("2026-08-01T09:00:00Z"));
  private PacerCueService service;

  @BeforeEach
  void setUp() {
    published.clear();
    now.set(Instant.parse("2026-08-01T09:00:00Z"));
    // A Clock that reads the mutable AtomicReference so tests can advance time without sleeping.
    Clock mutableClock =
        new Clock() {
          @Override
          public ZoneOffset getZone() {
            return ZoneOffset.UTC;
          }

          @Override
          public Clock withZone(java.time.ZoneId zone) {
            return this;
          }

          @Override
          public Instant instant() {
            return now.get();
          }
        };
    service =
        new PacerCueService(
            new FallbackCuePolicy(MAX_HR), published::add, mutableClock, COOLDOWN_SECONDS);
  }

  private AggregatedEvent event(String eventId, String sessionId) {
    return new AggregatedEvent(
        eventId,
        sessionId,
        "2026-08-01T09:00:00Z",
        30,
        "pace_drift",
        new AggregatedEvent.Summary(322, 150, 2, 150.0),
        new AggregatedEvent.RunContext("tempo", 300, 2800.0));
  }

  private void advance(Duration d) {
    now.set(now.get().plus(d));
  }

  @Test
  void firstEventForSessionEmitsCue() {
    service.handle(event("e1", "s1"));
    assertThat(published).hasSize(1);
    assertThat(published.get(0).sessionId()).isEqualTo("s1");
    assertThat(published.get(0).cue()).isNotBlank();
  }

  @Test
  void secondEventWithinCooldownIsSuppressed() {
    service.handle(event("e1", "s1"));
    advance(Duration.ofSeconds(COOLDOWN_SECONDS - 1));
    service.handle(event("e2", "s1"));
    assertThat(published).hasSize(1);
  }

  @Test
  void eventAfterCooldownEmitsAgain() {
    service.handle(event("e1", "s1"));
    advance(Duration.ofSeconds(COOLDOWN_SECONDS + 1));
    service.handle(event("e2", "s1"));
    assertThat(published).hasSize(2);
  }

  @Test
  void cooldownIsPerSession() {
    service.handle(event("e1", "s1"));
    service.handle(event("e2", "s2")); // different session, not on cooldown
    assertThat(published).hasSize(2);
  }

  @Test
  void sameEventIdProcessedOnlyOnce() {
    service.handle(event("dup", "s1"));
    advance(Duration.ofSeconds(COOLDOWN_SECONDS + 5)); // past cooldown so only idempotency blocks
    service.handle(event("dup", "s1"));
    assertThat(published).hasSize(1);
  }

  @Test
  void distinctEventIdsAfterCooldownBothEmit() {
    service.handle(event("a", "s1"));
    advance(Duration.ofSeconds(COOLDOWN_SECONDS + 5));
    service.handle(event("b", "s1"));
    assertThat(published).hasSize(2);
  }
}
