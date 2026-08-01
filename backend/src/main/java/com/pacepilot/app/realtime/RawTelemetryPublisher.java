package com.pacepilot.app.realtime;

import com.pacepilot.app.messaging.PacerTopology;
import com.pacepilot.app.messaging.dto.TelemetryEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Publishes inbound simulator telemetry onto the {@code telemetry.raw} routing key. */
@Component
public class RawTelemetryPublisher {

  private final RabbitTemplate rabbitTemplate;

  public RawTelemetryPublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  public void publish(TelemetryEvent event) {
    rabbitTemplate.convertAndSend(PacerTopology.EXCHANGE, PacerTopology.RK_TELEMETRY_RAW, event);
  }
}
