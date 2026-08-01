package com.pacepilot.app.n8n;

import static org.assertj.core.api.Assertions.assertThat;

import com.pacepilot.app.messaging.dto.AggregatedEvent;
import com.pacepilot.app.messaging.dto.CueTextEvent;
import org.junit.jupiter.api.Test;

class FallbackCuePolicyTest {

  private static final int MAX_HR = 190;
  private final FallbackCuePolicy policy = new FallbackCuePolicy(MAX_HR);

  private AggregatedEvent event(
      String type, AggregatedEvent.Summary summary, AggregatedEvent.RunContext context) {
    return new AggregatedEvent(
        "evt-1", "sess-1", "2026-08-01T09:34:30Z", 30, type, summary, context);
  }

  @Test
  void paceDriftSlowerThanTargetTellsRunnerToPickItUp() {
    // avg pace 322 sec/km is slower (larger) than target 300
    AggregatedEvent e =
        event(
            "pace_drift",
            new AggregatedEvent.Summary(322, 150, 2, 150.0),
            new AggregatedEvent.RunContext("tempo", 300, 2800.0));

    CueTextEvent cue = policy.decide(e);

    assertThat(cue.cue()).isNotBlank();
    assertThat(cue.cue().length()).isLessThan(140);
    assertThat(cue.cue().toLowerCase()).contains("pick");
    assertThat(cue.priority()).isEqualTo("medium");
    assertThat(cue.eventId()).isEqualTo("evt-1");
    assertThat(cue.sessionId()).isEqualTo("sess-1");
    assertThat(cue.ttlSeconds()).isPositive();
  }

  @Test
  void paceDriftFasterThanTargetTellsRunnerToEaseBack() {
    // avg pace 280 sec/km is faster (smaller) than target 300
    AggregatedEvent e =
        event(
            "pace_drift",
            new AggregatedEvent.Summary(280, 150, 2, 150.0),
            new AggregatedEvent.RunContext("tempo", 300, 2800.0));

    CueTextEvent cue = policy.decide(e);

    assertThat(cue.cue()).isNotBlank();
    assertThat(cue.cue().length()).isLessThan(140);
    assertThat(cue.cue().toLowerCase()).contains("ease");
    assertThat(cue.priority()).isEqualTo("medium");
  }

  @Test
  void hrDriftHighGivesCalmingHighPriorityCue() {
    AggregatedEvent e =
        event(
            "hr_drift",
            new AggregatedEvent.Summary(300, 168, 12, 150.0),
            new AggregatedEvent.RunContext("tempo", 300, 2800.0));

    CueTextEvent cue = policy.decide(e);

    assertThat(cue.cue()).isNotBlank();
    assertThat(cue.cue().length()).isLessThan(140);
    assertThat(cue.cue().toLowerCase()).contains("heart rate");
    assertThat(cue.priority()).isEqualTo("high");
  }

  @Test
  void guardrailSuppressesPushHarderAdviceWhenHrExceedsMax() {
    // pace_drift slower normally says "pick it up", but HR 195 > 190 must NOT push harder.
    AggregatedEvent e =
        event(
            "pace_drift",
            new AggregatedEvent.Summary(322, 195, 20, 150.0),
            new AggregatedEvent.RunContext("tempo", 300, 2800.0));

    CueTextEvent cue = policy.decide(e);

    assertThat(cue.cue()).isNotBlank();
    assertThat(cue.cue().length()).isLessThan(140);
    assertThat(cue.cue().toLowerCase()).doesNotContain("pick");
    assertThat(cue.cue().toLowerCase()).doesNotContain("harder");
    assertThat(cue.cue().toLowerCase()).doesNotContain("faster");
    // Guardrail cue is a back-off / calming cue, treated as high priority.
    assertThat(cue.priority()).isEqualTo("high");
  }

  @Test
  void stateChangeToWalkingGivesGentleReengageCue() {
    AggregatedEvent e =
        event(
            "state_change",
            new AggregatedEvent.Summary(null, 120, 0, 150.0),
            new AggregatedEvent.RunContext("tempo", 300, 2800.0));

    CueTextEvent cue = policy.decide(e);

    assertThat(cue.cue()).isNotBlank();
    assertThat(cue.cue().length()).isLessThan(140);
    assertThat(cue.priority()).isIn("low", "medium");
  }

  @Test
  void handlesNullRunContextWithoutCrashing() {
    AggregatedEvent e = event("pace_drift", new AggregatedEvent.Summary(322, 150, 2, 150.0), null);

    CueTextEvent cue = policy.decide(e);

    assertThat(cue.cue()).isNotBlank();
    assertThat(cue.cue().length()).isLessThan(140);
  }

  @Test
  void unknownEventTypeStillProducesNonBlankCue() {
    AggregatedEvent e =
        event(
            "mystery",
            new AggregatedEvent.Summary(300, 150, 0, 150.0),
            new AggregatedEvent.RunContext("tempo", 300, 2800.0));

    CueTextEvent cue = policy.decide(e);

    assertThat(cue.cue()).isNotBlank();
    assertThat(cue.cue().length()).isLessThan(140);
  }
}
