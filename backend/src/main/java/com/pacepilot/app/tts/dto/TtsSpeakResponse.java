package com.pacepilot.app.tts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TtsSpeakResponse(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("session_id") String sessionId,
    boolean delivered,
    @JsonProperty("audio_generated") boolean audioGenerated,
    @JsonProperty("fallback_reason") String fallbackReason) {}
