package com.pacepilot.app.tts.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public record TtsSpeakRequest(
    @NotBlank @JsonProperty("session_id") String sessionId,
    @JsonProperty("event_id") String eventId,
    @NotBlank String text) {}
