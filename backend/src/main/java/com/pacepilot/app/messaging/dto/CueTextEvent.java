package com.pacepilot.app.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Text cue carried on the {@code cue.text} routing key. Combines the n8n pacer response with the
 * session routing needed by the TTS stage. {@code cue} is plain English under 140 characters; an
 * empty cue means no spoken instruction.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record CueTextEvent(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("session_id") String sessionId,
    String cue,
    String priority,
    @JsonProperty("ttl_seconds") Integer ttlSeconds) {}
