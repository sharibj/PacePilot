package com.pacepilot.app.unifier;

import com.pacepilot.app.messaging.PacerTopology;
import com.pacepilot.app.messaging.dto.TelemetryEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Core unifier logic: validate a raw telemetry event, enrich its metadata, and republish the
 * canonical event unchanged in shape (see {@code docs/pacer-contracts.md} section 1). Kept free of
 * AMQP listener concerns so it can be exercised directly in tests.
 */
@Service
public class UnifierService {

  private static final Logger log = LoggerFactory.getLogger(UnifierService.class);
  private static final String SOURCE = "unifier";

  private final TelemetryValidator validator;
  private final RabbitTemplate rabbitTemplate;
  private final MeterRegistry meters;

  public UnifierService(
      TelemetryValidator validator, RabbitTemplate rabbitTemplate, MeterRegistry meters) {
    this.validator = validator;
    this.rabbitTemplate = rabbitTemplate;
    this.meters = meters;
  }

  /**
   * Validates the event (throwing {@link InvalidTelemetryException} when invalid), enriches its
   * metadata with {@code source=unifier}, and publishes it to the canonical routing key.
   */
  public void unify(TelemetryEvent raw) {
    validator.validateOrThrow(raw);
    TelemetryEvent canonical = enrich(raw);
    rabbitTemplate.convertAndSend(
        PacerTopology.EXCHANGE, PacerTopology.RK_TELEMETRY_CANONICAL, canonical);
    meters.counter("pacer.unifier.canonical").increment();
    log.debug(
        "Unified telemetry event {} for session {} -> {}",
        canonical.eventId(),
        canonical.sessionId(),
        PacerTopology.RK_TELEMETRY_CANONICAL);
  }

  /**
   * Returns a copy of the event whose metadata carries {@code source=unifier}, preserving any
   * existing metadata keys. The rest of the payload is left untouched.
   */
  private TelemetryEvent enrich(TelemetryEvent event) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    if (event.metadata() != null) {
      metadata.putAll(event.metadata());
    }
    metadata.put("source", SOURCE);

    return new TelemetryEvent(
        event.eventId(),
        event.sessionId(),
        event.status(),
        event.timestamp(),
        event.durationSeconds(),
        event.activityType(),
        event.stepCount(),
        event.speedMps(),
        event.distanceM(),
        metadata,
        event.metrics(),
        event.location());
  }
}
