package com.pacepilot.app.n8n;

import com.pacepilot.app.messaging.dto.AggregatedEvent;
import com.pacepilot.app.messaging.dto.CueTextEvent;

/**
 * Pure, deterministic mapping from an {@link AggregatedEvent} to a spoken {@link CueTextEvent}.
 * This is the local fallback used whenever the n8n webhook is unavailable (currently always, since
 * the webhook URL is unset this phase). It has no Spring or I/O dependencies so it is trivially
 * unit testable.
 *
 * <p>All cues are plain English under 140 characters. A safety guardrail overrides any
 * "push-harder" advice when the athlete's heart rate exceeds the configured maximum: in that case
 * only a calming / back-off cue is produced.
 */
public class FallbackCuePolicy {

  private static final int DEFAULT_TTL_SECONDS = 20;

  private final int maxHeartRate;

  public FallbackCuePolicy(int maxHeartRate) {
    this.maxHeartRate = maxHeartRate;
  }

  /** Maps the aggregated event to a cue. Never returns {@code null}. */
  public CueTextEvent decide(AggregatedEvent event) {
    AggregatedEvent.Summary summary = event.summary();
    Integer avgHeartRate = summary == null ? null : summary.avgHeartRate();

    // Guardrail: if HR is above the ceiling, never advise more effort; calm the runner down.
    if (avgHeartRate != null && avgHeartRate > maxHeartRate) {
      return cue(
          event, "Heart rate is very high. Slow to an easy jog and let it come back down.", "high");
    }

    String eventType = event.eventType() == null ? "" : event.eventType();
    return switch (eventType) {
      case "pace_drift" -> paceDriftCue(event);
      case "hr_drift" ->
          cue(event, "Your heart rate is climbing; relax your effort for a minute.", "high");
      case "state_change" ->
          cue(event, "Nice, keep moving. Ease back into your rhythm when you're ready.", "low");
      default -> cue(event, "Stay steady and keep your effort consistent.", "low");
    };
  }

  private CueTextEvent paceDriftCue(AggregatedEvent event) {
    AggregatedEvent.Summary summary = event.summary();
    AggregatedEvent.RunContext context = event.runContext();
    Integer avgPace = summary == null ? null : summary.avgPaceSecPerKm();
    Integer targetPace = context == null ? null : context.targetPaceSecPerKm();

    if (avgPace != null && targetPace != null) {
      // Larger seconds-per-km means slower. Slower than target -> speed up; faster -> ease off.
      if (avgPace > targetPace) {
        return cue(event, "Pick it up slightly to reach your tempo pace.", "medium");
      }
      if (avgPace < targetPace) {
        return cue(event, "Ease back a touch to settle into tempo.", "medium");
      }
    }
    return cue(event, "You're on pace. Hold this effort steady.", "low");
  }

  private CueTextEvent cue(AggregatedEvent event, String text, String priority) {
    return new CueTextEvent(
        event.eventId(), event.sessionId(), text, priority, DEFAULT_TTL_SECONDS);
  }
}
