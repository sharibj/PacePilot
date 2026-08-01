package com.pacepilot.app.messaging.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Outbound WebSocket message delivered to the simulator. {@code audioBase64} may be null when TTS
 * is unavailable (text-only fallback). Matches the {@code pacer_audio} contract in {@code
 * docs/pacer-contracts.md}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record PacerAudioMessage(
    String type,
    @JsonProperty("session_id") String sessionId,
    @JsonProperty("event_id") String eventId,
    @JsonProperty("audio_base64") String audioBase64,
    String text) {

  public static PacerAudioMessage textOnly(String sessionId, String eventId, String text) {
    return new PacerAudioMessage("pacer_audio", sessionId, eventId, null, text);
  }

  public static PacerAudioMessage withAudio(
      String sessionId, String eventId, String audioBase64, String text) {
    return new PacerAudioMessage("pacer_audio", sessionId, eventId, audioBase64, text);
  }
}
