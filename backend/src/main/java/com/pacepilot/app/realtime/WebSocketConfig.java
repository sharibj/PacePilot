package com.pacepilot.app.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the telemetry WebSocket endpoint used by the simulator client. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final TelemetryWebSocketHandler handler;

  public WebSocketConfig(TelemetryWebSocketHandler handler) {
    this.handler = handler;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    // Allow any origin so the Flutter clients can complete the handshake: the web
    // dev server runs on a random port, and native iOS/Android send no Origin
    // header at all. setAllowedOriginPatterns("*") also permits those null-origin
    // native clients, which setAllowedOrigins("*") would reject. This is fine for
    // a local demo; tighten to a concrete origin list before exposing it publicly.
    registry.addHandler(handler, "/ws/telemetry").setAllowedOriginPatterns("*");
  }
}
