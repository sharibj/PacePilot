package com.pacepilot.app.unifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pacepilot.app.messaging.dto.TelemetryEvent;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TelemetryValidatorTest {

  private final TelemetryValidator validator = new TelemetryValidator();

  private static TelemetryEvent.Metrics validMetrics() {
    return new TelemetryEvent.Metrics(
        new TelemetryEvent.HeartRate(154.0, "count/min", 3),
        new TelemetryEvent.Pace(0.31, "08:18", "min/mi"));
  }

  private static TelemetryEvent validEvent() {
    return new TelemetryEvent(
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
        validMetrics(),
        new TelemetryEvent.Location(52.520008, 13.404954, 34.2, 3.2));
  }

  @Test
  void validEventHasNoErrors() {
    assertThat(validator.validate(validEvent())).isEmpty();
  }

  @Test
  void validateOrThrowPassesForValidEvent() {
    validator.validateOrThrow(validEvent());
  }

  @Test
  void nullEventIsInvalid() {
    assertThat(validator.validate(null)).containsExactly("event is null");
  }

  @Test
  void missingEventIdFails() {
    TelemetryEvent e = validEvent();
    TelemetryEvent bad =
        new TelemetryEvent(
            null,
            e.sessionId(),
            e.status(),
            e.timestamp(),
            e.durationSeconds(),
            e.activityType(),
            e.stepCount(),
            e.speedMps(),
            e.distanceM(),
            e.metadata(),
            e.metrics(),
            e.location());
    assertThat(validator.validate(bad)).contains("event_id is missing");
  }

  @Test
  void missingSessionIdFails() {
    TelemetryEvent e = validEvent();
    TelemetryEvent bad =
        new TelemetryEvent(
            e.eventId(),
            null,
            e.status(),
            e.timestamp(),
            e.durationSeconds(),
            e.activityType(),
            e.stepCount(),
            e.speedMps(),
            e.distanceM(),
            e.metadata(),
            e.metrics(),
            e.location());
    assertThat(validator.validate(bad)).contains("session_id is missing");
  }

  @Test
  void missingTimestampFails() {
    TelemetryEvent e = validEvent();
    TelemetryEvent bad =
        new TelemetryEvent(
            e.eventId(),
            e.sessionId(),
            e.status(),
            null,
            e.durationSeconds(),
            e.activityType(),
            e.stepCount(),
            e.speedMps(),
            e.distanceM(),
            e.metadata(),
            e.metrics(),
            e.location());
    assertThat(validator.validate(bad)).contains("timestamp is missing");
  }

  @Test
  void missingStatusFails() {
    TelemetryEvent e = validEvent();
    TelemetryEvent bad =
        new TelemetryEvent(
            e.eventId(),
            e.sessionId(),
            null,
            e.timestamp(),
            e.durationSeconds(),
            e.activityType(),
            e.stepCount(),
            e.speedMps(),
            e.distanceM(),
            e.metadata(),
            e.metrics(),
            e.location());
    assertThat(validator.validate(bad)).contains("status is missing");
  }

  @Test
  void invalidStatusFails() {
    TelemetryEvent e = validEvent();
    TelemetryEvent bad =
        new TelemetryEvent(
            e.eventId(),
            e.sessionId(),
            "sprinting",
            e.timestamp(),
            e.durationSeconds(),
            e.activityType(),
            e.stepCount(),
            e.speedMps(),
            e.distanceM(),
            e.metadata(),
            e.metrics(),
            e.location());
    assertThat(validator.validate(bad)).anyMatch(msg -> msg.startsWith("status 'sprinting'"));
  }

  @Test
  void missingDurationFails() {
    TelemetryEvent e = validEvent();
    TelemetryEvent bad =
        new TelemetryEvent(
            e.eventId(),
            e.sessionId(),
            e.status(),
            e.timestamp(),
            null,
            e.activityType(),
            e.stepCount(),
            e.speedMps(),
            e.distanceM(),
            e.metadata(),
            e.metrics(),
            e.location());
    assertThat(validator.validate(bad)).contains("duration_seconds is missing");
  }

  @Test
  void missingDistanceFails() {
    TelemetryEvent e = validEvent();
    TelemetryEvent bad =
        new TelemetryEvent(
            e.eventId(),
            e.sessionId(),
            e.status(),
            e.timestamp(),
            e.durationSeconds(),
            e.activityType(),
            e.stepCount(),
            e.speedMps(),
            null,
            e.metadata(),
            e.metrics(),
            e.location());
    assertThat(validator.validate(bad)).contains("distance_m is missing");
  }

  @Test
  void missingSpeedFails() {
    TelemetryEvent e = validEvent();
    TelemetryEvent bad =
        new TelemetryEvent(
            e.eventId(),
            e.sessionId(),
            e.status(),
            e.timestamp(),
            e.durationSeconds(),
            e.activityType(),
            e.stepCount(),
            null,
            e.distanceM(),
            e.metadata(),
            e.metrics(),
            e.location());
    assertThat(validator.validate(bad)).contains("speed_mps is missing");
  }

  @Test
  void nullMetricsFails() {
    TelemetryEvent e = validEvent();
    TelemetryEvent bad =
        new TelemetryEvent(
            e.eventId(),
            e.sessionId(),
            e.status(),
            e.timestamp(),
            e.durationSeconds(),
            e.activityType(),
            e.stepCount(),
            e.speedMps(),
            e.distanceM(),
            e.metadata(),
            null,
            e.location());
    assertThat(validator.validate(bad)).contains("metrics is missing");
  }

  @Test
  void nullHeartRateValueFails() {
    TelemetryEvent e = validEvent();
    TelemetryEvent.Metrics metrics =
        new TelemetryEvent.Metrics(
            new TelemetryEvent.HeartRate(null, "count/min", 3),
            new TelemetryEvent.Pace(0.31, "08:18", "min/mi"));
    TelemetryEvent bad =
        new TelemetryEvent(
            e.eventId(),
            e.sessionId(),
            e.status(),
            e.timestamp(),
            e.durationSeconds(),
            e.activityType(),
            e.stepCount(),
            e.speedMps(),
            e.distanceM(),
            e.metadata(),
            metrics,
            e.location());
    assertThat(validator.validate(bad)).contains("metrics.heart_rate.value is missing");
  }

  @Test
  void nullPaceFails() {
    TelemetryEvent e = validEvent();
    TelemetryEvent.Metrics metrics =
        new TelemetryEvent.Metrics(new TelemetryEvent.HeartRate(154.0, "count/min", 3), null);
    TelemetryEvent bad =
        new TelemetryEvent(
            e.eventId(),
            e.sessionId(),
            e.status(),
            e.timestamp(),
            e.durationSeconds(),
            e.activityType(),
            e.stepCount(),
            e.speedMps(),
            e.distanceM(),
            e.metadata(),
            metrics,
            e.location());
    assertThat(validator.validate(bad)).contains("metrics.pace is missing");
  }

  @Test
  void nullPaceValueFails() {
    TelemetryEvent e = validEvent();
    TelemetryEvent.Metrics metrics =
        new TelemetryEvent.Metrics(
            new TelemetryEvent.HeartRate(154.0, "count/min", 3),
            new TelemetryEvent.Pace(null, "08:18", "min/mi"));
    TelemetryEvent bad =
        new TelemetryEvent(
            e.eventId(),
            e.sessionId(),
            e.status(),
            e.timestamp(),
            e.durationSeconds(),
            e.activityType(),
            e.stepCount(),
            e.speedMps(),
            e.distanceM(),
            e.metadata(),
            metrics,
            e.location());
    assertThat(validator.validate(bad))
        .contains("metrics.pace.current_pace_seconds_per_meter is missing");
  }

  @Test
  void validateOrThrowRaisesWithAllErrors() {
    TelemetryEvent bad =
        new TelemetryEvent(
            null, null, "sprinting", null, null, null, null, null, null, null, null, null);
    assertThatThrownBy(() -> validator.validateOrThrow(bad))
        .isInstanceOf(InvalidTelemetryException.class)
        .satisfies(
            ex -> {
              InvalidTelemetryException ite = (InvalidTelemetryException) ex;
              assertThat(ite.errors()).contains("event_id is missing", "session_id is missing");
            });
  }
}
