package com.pacepilot.app.tts;

import com.pacepilot.app.messaging.PacerTopology;
import com.pacepilot.app.messaging.dto.CueTextEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Final pipeline stage: consumes text cues from {@code cue.text} and hands them to {@link
 * TtsService}, which synthesizes ElevenLabs audio and pushes a {@code pacer_audio} message to the
 * session's WebSocket. If synthesis fails or no API key is configured, {@link TtsService} degrades
 * to a text-only message. A blank cue means "no spoken instruction" and is skipped.
 */
@Component
public class TtsListener {

  private static final Logger log = LoggerFactory.getLogger(TtsListener.class);

  private final TtsService ttsService;
  private final MeterRegistry meters;

  public TtsListener(TtsService ttsService, MeterRegistry meters) {
    this.ttsService = ttsService;
    this.meters = meters;
  }

  @RabbitListener(
      id = "tts",
      queues = PacerTopology.Q_CUE_TEXT,
      autoStartup = "${pacer.listener.tts.enabled:true}")
  public void onCue(CueTextEvent cue) {
    if (cue == null || cue.cue() == null || cue.cue().isBlank()) {
      meters.counter("pacer.cue.skipped", "reason", "empty").increment();
      return;
    }
    Timer.Sample sample = Timer.start(meters);
    try {
      TtsService.SpeakResult result =
          ttsService.speakToSession(cue.sessionId(), cue.eventId(), cue.cue());
      meters
          .counter("pacer.cue.delivered", "delivered", Boolean.toString(result.delivered()))
          .increment();
      if (!result.delivered()) {
        log.debug("Cue {} for session {} had no open channel", cue.eventId(), cue.sessionId());
      }
    } finally {
      sample.stop(meters.timer("pacer.tts.latency"));
    }
  }
}
