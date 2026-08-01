package com.pacepilot.app.unifier;

import com.pacepilot.app.messaging.dto.TelemetryEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Validates that a raw {@link TelemetryEvent} carries every field the canonical contract requires
 * (see {@code docs/pacer-contracts.md} section 1). The validator is a pure, Spring-free component
 * so it can be unit tested without a context; it collects all errors rather than failing fast so
 * the dead-letter log explains everything that was wrong.
 */
@Component
public class TelemetryValidator {

  private static final Set<String> VALID_STATUSES =
      Set.of("running", "walking", "stopped", "paused");

  /**
   * Returns the list of validation errors for the given event. An empty list means the event is
   * valid.
   */
  public List<String> validate(TelemetryEvent event) {
    List<String> errors = new ArrayList<>();
    if (event == null) {
      errors.add("event is null");
      return errors;
    }

    requireText(errors, "event_id", event.eventId());
    requireText(errors, "session_id", event.sessionId());
    requireText(errors, "timestamp", event.timestamp());

    if (!StringUtils.hasText(event.status())) {
      errors.add("status is missing");
    } else if (!VALID_STATUSES.contains(event.status())) {
      errors.add("status '" + event.status() + "' is not one of " + VALID_STATUSES);
    }

    requireNumber(errors, "duration_seconds", event.durationSeconds());
    requireNumber(errors, "distance_m", event.distanceM());
    requireNumber(errors, "speed_mps", event.speedMps());

    TelemetryEvent.Metrics metrics = event.metrics();
    if (metrics == null) {
      errors.add("metrics is missing");
    } else {
      if (metrics.heartRate() == null) {
        errors.add("metrics.heart_rate is missing");
      } else {
        requireNumber(errors, "metrics.heart_rate.value", metrics.heartRate().value());
      }
      if (metrics.pace() == null) {
        errors.add("metrics.pace is missing");
      } else {
        requireNumber(
            errors,
            "metrics.pace.current_pace_seconds_per_meter",
            metrics.pace().currentPaceSecondsPerMeter());
      }
    }

    return errors;
  }

  /**
   * Convenience wrapper that throws {@link InvalidTelemetryException} when the event is invalid.
   */
  public void validateOrThrow(TelemetryEvent event) {
    List<String> errors = validate(event);
    if (!errors.isEmpty()) {
      throw new InvalidTelemetryException(errors);
    }
  }

  private static void requireText(List<String> errors, String field, String value) {
    if (!StringUtils.hasText(value)) {
      errors.add(field + " is missing");
    }
  }

  private static void requireNumber(List<String> errors, String field, Number value) {
    if (value == null) {
      errors.add(field + " is missing");
    }
  }
}
