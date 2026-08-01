package com.pacepilot.app.unifier;

import com.pacepilot.app.messaging.PacerTopology;
import com.pacepilot.app.messaging.dto.TelemetryEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes raw telemetry from {@link PacerTopology#Q_TELEMETRY_RAW}, delegates to {@link
 * UnifierService} to validate/enrich/republish, and dead-letters malformed events. Rejecting with
 * {@link AmqpRejectAndDontRequeueException} lets the queue's configured DLX route the message to
 * {@link PacerTopology#Q_DEADLETTER} (see {@code docs/pacer-contracts.md} section 6).
 */
@Component
public class UnifierListener {

  private static final Logger log = LoggerFactory.getLogger(UnifierListener.class);

  private final UnifierService unifierService;

  public UnifierListener(UnifierService unifierService) {
    this.unifierService = unifierService;
  }

  @RabbitListener(
      id = "unifier",
      queues = PacerTopology.Q_TELEMETRY_RAW,
      autoStartup = "${pacer.listener.unifier.enabled:true}")
  public void onRawTelemetry(TelemetryEvent event) {
    try {
      unifierService.unify(event);
    } catch (InvalidTelemetryException ex) {
      log.warn(
          "Dead-lettering malformed telemetry event {} (session {}): {}",
          event == null ? "<null>" : event.eventId(),
          event == null ? "<null>" : event.sessionId(),
          ex.errors());
      throw new AmqpRejectAndDontRequeueException(ex.getMessage(), ex);
    }
  }
}
