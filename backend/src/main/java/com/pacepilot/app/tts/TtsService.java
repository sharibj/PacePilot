package com.pacepilot.app.tts;

import com.pacepilot.app.messaging.dto.PacerAudioMessage;
import com.pacepilot.app.realtime.PacerAudioSender;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Base64;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Converts cue text to audio and pushes the result to the session WebSocket channel. */
@Service
public class TtsService {

  private final TtsSynthesizer synthesizer;
  private final PacerAudioSender sender;
  private final MeterRegistry meters;

  public TtsService(TtsSynthesizer synthesizer, PacerAudioSender sender, MeterRegistry meters) {
    this.synthesizer = synthesizer;
    this.sender = sender;
    this.meters = meters;
  }

  public SpeakResult speakToSession(String sessionId, String eventId, String cueText) {
    String resolvedEventId =
        (eventId == null || eventId.isBlank()) ? UUID.randomUUID().toString() : eventId;

    try {
      byte[] audio = synthesizer.synthesize(cueText);
      String base64Audio = Base64.getEncoder().encodeToString(audio);
      boolean delivered =
          sender.send(
              PacerAudioMessage.withAudio(sessionId, resolvedEventId, base64Audio, cueText));
      meters.counter("tts.delivered", "mode", delivered ? "audio" : "undelivered").increment();
      return new SpeakResult(resolvedEventId, delivered, true, null);
    } catch (RuntimeException ex) {
      boolean delivered =
          sender.send(PacerAudioMessage.textOnly(sessionId, resolvedEventId, cueText));
      meters
          .counter("tts.delivered", "mode", delivered ? "text_fallback" : "undelivered")
          .increment();
      return new SpeakResult(resolvedEventId, delivered, false, ex.getMessage());
    }
  }

  public record SpeakResult(
      String eventId, boolean delivered, boolean audioGenerated, String fallbackReason) {}
}
