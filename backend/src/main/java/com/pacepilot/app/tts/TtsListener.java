package com.pacepilot.app.tts;

import com.pacepilot.app.messaging.PacerTopology;
import com.pacepilot.app.messaging.dto.CueTextEvent;
import com.pacepilot.app.messaging.dto.PacerAudioMessage;
import com.pacepilot.app.realtime.PacerAudioSender;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Final pipeline stage: consumes text cues from {@code cue.text}, synthesizes a {@link
 * PacerAudioMessage} (text-only while ElevenLabs is stubbed), and pushes it to the simulator over
 * the WebSocket channel. A blank cue means "no spoken instruction" and is skipped.
 */
@Component
public class TtsListener {

  private static final Logger log = LoggerFactory.getLogger(TtsListener.class);

  private final CueSynthesizer synthesizer;
  private final PacerAudioSender audioSender;
  private final MeterRegistry meters;

  public TtsListener(
      CueSynthesizer synthesizer, PacerAudioSender audioSender, MeterRegistry meters) {
    this.synthesizer = synthesizer;
    this.audioSender = audioSender;
    this.meters = meters;
  }

  @RabbitListener(queues = PacerTopology.Q_CUE_TEXT)
  public void onCue(CueTextEvent cue) {
    if (cue == null || cue.cue() == null || cue.cue().isBlank()) {
      meters.counter("pacer.cue.skipped", "reason", "empty").increment();
      return;
    }
    Timer.Sample sample = Timer.start(meters);
    try {
      PacerAudioMessage message = synthesizer.synthesize(cue);
      boolean delivered = audioSender.send(message);
      meters.counter("pacer.cue.delivered", "delivered", Boolean.toString(delivered)).increment();
      if (!delivered) {
        log.debug("Cue {} for session {} had no open channel", cue.eventId(), cue.sessionId());
      }
    } finally {
      sample.stop(meters.timer("pacer.tts.latency"));
    }
  }
}
