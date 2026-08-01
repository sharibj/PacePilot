package com.pacepilot.app.aggregator;

import com.pacepilot.app.messaging.PacerTopology;
import com.pacepilot.app.messaging.dto.AggregatedEvent;
import com.pacepilot.app.messaging.dto.TelemetryEvent;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Consumes canonical telemetry, maintains a rolling window per {@code session_id}, and publishes an
 * {@link AggregatedEvent} to {@code telemetry.aggregated} only when a salient event (pace drift, HR
 * drift, or abrupt state change) is detected. Stable telemetry produces no output, which keeps the
 * downstream queue quiet.
 *
 * <p>All windowing and detection math lives in {@link SessionWindow} / {@link
 * SalientEventDetector}; this class only adapts messages, guards per-session state for concurrent
 * delivery, and publishes.
 */
@Component
public class TelemetryAggregator {

  private static final Logger log = LoggerFactory.getLogger(TelemetryAggregator.class);

  private final RabbitTemplate rabbitTemplate;
  private final SalientEventDetector detector;
  private final int windowSeconds;

  /**
   * Per-session windows. {@link ConcurrentHashMap} lets different sessions be processed in
   * parallel; mutation of a single session's window is guarded by synchronizing on that window
   * instance, so concurrent deliveries for the same session are serialized without a global lock.
   */
  private final ConcurrentHashMap<String, SessionWindow> windows = new ConcurrentHashMap<>();

  public TelemetryAggregator(
      RabbitTemplate rabbitTemplate,
      SalientEventDetector detector,
      @Value("${pacer.aggregator.window-seconds:30}") int windowSeconds) {
    this.rabbitTemplate = rabbitTemplate;
    this.detector = detector;
    this.windowSeconds = windowSeconds;
  }

  @RabbitListener(
      id = "aggregator",
      queues = PacerTopology.Q_TELEMETRY_CANONICAL,
      autoStartup = "${pacer.listener.aggregator.enabled:true}")
  public void onCanonicalTelemetry(TelemetryEvent event) {
    if (event == null || event.sessionId() == null) {
      log.warn("Dropping canonical telemetry with no session id: {}", event);
      return;
    }

    SessionWindow window =
        windows.computeIfAbsent(event.sessionId(), k -> new SessionWindow(windowSeconds));

    Optional<AggregatedEvent> salient;
    // Serialize mutation + detection per session so concurrent deliveries for one session cannot
    // interleave and corrupt the window. Different sessions use different locks.
    synchronized (window) {
      window.add(toSample(event));
      salient = detector.detect(event.sessionId(), window);
    }

    salient.ifPresent(this::publish);
  }

  private void publish(AggregatedEvent aggregated) {
    log.debug(
        "Emitting {} for session {} (event {})",
        aggregated.eventType(),
        aggregated.sessionId(),
        aggregated.eventId());
    rabbitTemplate.convertAndSend(
        PacerTopology.EXCHANGE, PacerTopology.RK_TELEMETRY_AGGREGATED, aggregated);
  }

  private SessionWindow.Sample toSample(TelemetryEvent event) {
    Double pace = null;
    Double heartRate = null;
    if (event.metrics() != null) {
      if (event.metrics().pace() != null) {
        pace = event.metrics().pace().currentPaceSecondsPerMeter();
      }
      if (event.metrics().heartRate() != null) {
        heartRate = event.metrics().heartRate().value();
      }
    }
    return new SessionWindow.Sample(
        parseEpochMillis(event.timestamp()), pace, heartRate, event.distanceM(), event.status());
  }

  /** Parses an ISO-8601 timestamp to epoch millis, falling back to now on missing/invalid input. */
  private long parseEpochMillis(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return System.currentTimeMillis();
    }
    try {
      return Instant.parse(timestamp).toEpochMilli();
    } catch (DateTimeParseException e) {
      log.warn("Unparseable telemetry timestamp '{}', using current time", timestamp);
      return System.currentTimeMillis();
    }
  }
}
