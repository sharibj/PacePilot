package com.pacepilot.app.realtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacepilot.app.messaging.dto.PacerAudioMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Outbound seam for pushing {@code pacer_audio} messages to a connected simulator. The TTS stage
 * calls this to deliver a cue (with or without synthesized audio) over the WebSocket channel.
 */
@Component
public class PacerAudioSender {

  private static final Logger log = LoggerFactory.getLogger(PacerAudioSender.class);

  private final SessionRegistry registry;
  private final ObjectMapper objectMapper;

  public PacerAudioSender(SessionRegistry registry, ObjectMapper objectMapper) {
    this.registry = registry;
    this.objectMapper = objectMapper;
  }

  /** Serializes and delivers the message to its session. Returns true if delivered. */
  public boolean send(PacerAudioMessage message) {
    try {
      String json = objectMapper.writeValueAsString(message);
      boolean delivered = registry.sendText(message.sessionId(), json);
      if (!delivered) {
        log.debug(
            "No open session for {}; cue {} not delivered", message.sessionId(), message.eventId());
      }
      return delivered;
    } catch (Exception e) {
      log.warn("Failed to serialize pacer_audio for {}: {}", message.sessionId(), e.getMessage());
      return false;
    }
  }
}
