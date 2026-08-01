package com.pacepilot.app.realtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pacepilot.app.messaging.dto.TelemetryEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket ingress for simulator telemetry. Each inbound frame is a canonical {@link
 * TelemetryEvent}; the handler registers the connection under its {@code session_id}, then
 * publishes the event to {@code telemetry.raw} for the unifier to consume. Frames that cannot be
 * parsed or lack a session id are dropped with a warning (they never enter the pipeline).
 */
@Component
public class TelemetryWebSocketHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(TelemetryWebSocketHandler.class);

  private final ObjectMapper objectMapper;
  private final SessionRegistry registry;
  private final RawTelemetryPublisher publisher;
  private final MeterRegistry meters;

  public TelemetryWebSocketHandler(
      ObjectMapper objectMapper,
      SessionRegistry registry,
      RawTelemetryPublisher publisher,
      MeterRegistry meters) {
    this.objectMapper = objectMapper;
    this.registry = registry;
    this.publisher = publisher;
    this.meters = meters;
  }

  @Override
  protected void handleTextMessage(WebSocketSession session, TextMessage message) {
    JsonNode root;
    try {
      root = objectMapper.readTree(message.getPayload());
    } catch (Exception e) {
      log.warn("Dropping unparseable telemetry frame: {}", e.getMessage());
      meters.counter("pacer.telemetry.rejected", "reason", "unparseable").increment();
      return;
    }

    if (isRegisterFrame(root)) {
      registerClientSession(session, root.path("session_id").asText());
      return;
    }

    TelemetryEvent event;
    try {
      event = objectMapper.treeToValue(root, TelemetryEvent.class);
    } catch (Exception e) {
      log.warn("Dropping unparseable telemetry frame: {}", e.getMessage());
      meters.counter("pacer.telemetry.rejected", "reason", "unparseable").increment();
      return;
    }

    if (event.sessionId() == null || event.sessionId().isBlank()) {
      log.warn("Dropping telemetry frame without session_id");
      meters.counter("pacer.telemetry.rejected", "reason", "no_session_id").increment();
      return;
    }

    registry.register(event.sessionId(), session);
    publisher.publish(event);
    meters.counter("pacer.telemetry.ingested").increment();
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
    registry.removeByWsId(session.getId());
  }

  private boolean isRegisterFrame(JsonNode root) {
    return "register".equalsIgnoreCase(root.path("type").asText());
  }

  private void registerClientSession(WebSocketSession session, String sessionId) {
    if (sessionId == null || sessionId.isBlank()) {
      meters.counter("pacer.telemetry.rejected", "reason", "register_no_session_id").increment();
      return;
    }

    registry.register(sessionId, session);
    meters.counter("pacer.session.registered").increment();
    try {
      String ack =
          objectMapper.writeValueAsString(Map.of("type", "registered", "session_id", sessionId));
      session.sendMessage(new TextMessage(ack));
    } catch (Exception e) {
      log.debug("Failed to send register ack for {}: {}", sessionId, e.getMessage());
    }
  }
}
