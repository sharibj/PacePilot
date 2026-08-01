package com.pacepilot.app.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Aggregated (salient) event produced by the aggregator and consumed by the n8n handler. Matches
 * the aggregated event contract in {@code docs/pacer-contracts.md}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record AggregatedEvent(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("session_id") String sessionId,
    String timestamp,
    @JsonProperty("window_seconds") Integer windowSeconds,
    @JsonProperty("event_type") String eventType,
    Summary summary,
    @JsonProperty("run_context") RunContext runContext) {

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Summary(
      @JsonProperty("avg_pace_sec_per_km") Integer avgPaceSecPerKm,
      @JsonProperty("avg_heart_rate") Integer avgHeartRate,
      @JsonProperty("heart_rate_drift_bpm") Integer heartRateDriftBpm,
      @JsonProperty("distance_m") Double distanceM) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record RunContext(
      @JsonProperty("planned_phase") String plannedPhase,
      @JsonProperty("target_pace_sec_per_km") Integer targetPaceSecPerKm,
      @JsonProperty("remaining_distance_m") Double remainingDistanceM) {}
}
