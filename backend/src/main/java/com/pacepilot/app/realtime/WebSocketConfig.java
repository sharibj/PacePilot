package com.pacepilot.app.realtime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the telemetry WebSocket endpoint used by the simulator client. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

  private final TelemetryWebSocketHandler handler;
  private final String allowedOrigin;

  public WebSocketConfig(
      TelemetryWebSocketHandler handler,
      @Value("${app.cors.allowed-origin:http://localhost:5173}") String allowedOrigin) {
    this.handler = handler;
    this.allowedOrigin = allowedOrigin;
  }

  @Override
  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(handler, "/ws/telemetry").setAllowedOrigins(allowedOrigin);
  }
}
