package com.pacepilot.app.n8n;

import com.pacepilot.app.messaging.PacerTopology;
import com.pacepilot.app.messaging.dto.CueTextEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/** Publishes resolved cues onto the {@code cue.text} routing key for the TTS stage to consume. */
@Component
public class RabbitCuePublisher implements CuePublisher {

  private final RabbitTemplate rabbitTemplate;

  public RabbitCuePublisher(RabbitTemplate rabbitTemplate) {
    this.rabbitTemplate = rabbitTemplate;
  }

  @Override
  public void publish(CueTextEvent cue) {
    rabbitTemplate.convertAndSend(PacerTopology.EXCHANGE, PacerTopology.RK_CUE_TEXT, cue);
  }
}
