package com.pacepilot.app.realtime;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Thread-safe registry mapping a pacer {@code session_id} to its live WebSocket session. The
 * ingress handler registers sessions as telemetry arrives; the egress path ({@link
 * PacerAudioSender}) looks them up to push cue audio back to the right simulator.
 */
@Component
public class SessionRegistry {

  private static final Logger log = LoggerFactory.getLogger(SessionRegistry.class);

  private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

  public void register(String sessionId, WebSocketSession session) {
    if (sessionId == null || session == null) {
      return;
    }
    sessions.put(sessionId, session);
  }

  public void removeByWsId(String wsSessionId) {
    sessions.values().removeIf(s -> s.getId().equals(wsSessionId));
  }

  /**
   * Sends a text payload to the given pacer session. Returns true if the session was connected and
   * open; false otherwise (caller may treat this as an undeliverable cue).
   */
  public boolean sendText(String sessionId, String payload) {
    WebSocketSession session = sessions.get(sessionId);
    if (session == null || !session.isOpen()) {
      return false;
    }
    try {
      session.sendMessage(new TextMessage(payload));
      return true;
    } catch (IOException e) {
      log.warn("Failed to send message to session {}: {}", sessionId, e.getMessage());
      return false;
    }
  }

  public boolean isConnected(String sessionId) {
    WebSocketSession session = sessions.get(sessionId);
    return session != null && session.isOpen();
  }

  public int size() {
    return sessions.size();
  }
}
