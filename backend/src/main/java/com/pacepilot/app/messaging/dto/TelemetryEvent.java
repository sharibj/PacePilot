package com.pacepilot.app.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Canonical telemetry event shared by the simulator and the unifier. The unifier validates and
 * enriches metadata but does not reshape this payload. Field names match {@code
 * docs/pacer-contracts.md} exactly (snake_case on the wire).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryEvent(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("session_id") String sessionId,
    String status,
    String timestamp,
    @JsonProperty("duration_seconds") Double durationSeconds,
    @JsonProperty("activity_type") String activityType,
    @JsonProperty("step_count") Long stepCount,
    @JsonProperty("speed_mps") Double speedMps,
    @JsonProperty("distance_m") Double distanceM,
    Map<String, Object> metadata,
    Metrics metrics,
    Location location) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Metrics(@JsonProperty("heart_rate") HeartRate heartRate, Pace pace) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record HeartRate(Double value, String unit, Integer zone) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Pace(
      @JsonProperty("current_pace_seconds_per_meter") Double currentPaceSecondsPerMeter,
      @JsonProperty("current_mile_pace") String currentMilePace,
      String unit) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Location(
      Double latitude,
      Double longitude,
      Double altitude,
      @JsonProperty("speed_mps") Double speedMps) {}
}
