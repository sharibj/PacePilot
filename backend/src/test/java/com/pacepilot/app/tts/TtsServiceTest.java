package com.pacepilot.app.tts;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pacepilot.app.messaging.dto.PacerAudioMessage;
import com.pacepilot.app.realtime.PacerAudioSender;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class TtsServiceTest {

  @Test
  void speakToSession_sendsAudioWhenSynthesisSucceeds() {
    TtsSynthesizer synthesizer = mock(TtsSynthesizer.class);
    PacerAudioSender sender = mock(PacerAudioSender.class);
    SimpleMeterRegistry meters = new SimpleMeterRegistry();

    when(synthesizer.synthesize("Hold this pace for one more minute."))
        .thenReturn(new byte[] {1, 2, 3});
    when(sender.send(any(PacerAudioMessage.class))).thenReturn(true);

    TtsService service = new TtsService(synthesizer, sender, meters);

    TtsService.SpeakResult result =
        service.speakToSession("session-123", "event-123", "Hold this pace for one more minute.");

    assertTrue(result.delivered());
    assertTrue(result.audioGenerated());
    assertNull(result.fallbackReason());

    verify(sender)
        .send(
            argThat(
                msg ->
                    "session-123".equals(msg.sessionId())
                        && "event-123".equals(msg.eventId())
                        && msg.audioBase64() != null
                        && !msg.audioBase64().isBlank()
                        && "Hold this pace for one more minute.".equals(msg.text())));
  }

  @Test
  void speakToSession_fallsBackToTextWhenSynthesisFails() {
    TtsSynthesizer synthesizer = mock(TtsSynthesizer.class);
    PacerAudioSender sender = mock(PacerAudioSender.class);
    SimpleMeterRegistry meters = new SimpleMeterRegistry();

    when(synthesizer.synthesize("Ease back slightly.")).thenThrow(new RuntimeException("boom"));
    when(sender.send(any(PacerAudioMessage.class))).thenReturn(true);

    TtsService service = new TtsService(synthesizer, sender, meters);

    TtsService.SpeakResult result =
        service.speakToSession("session-456", "event-456", "Ease back slightly.");

    assertTrue(result.delivered());
    assertFalse(result.audioGenerated());
    assertTrue(result.fallbackReason().contains("boom"));

    verify(sender)
        .send(
            argThat(
                msg ->
                    "session-456".equals(msg.sessionId())
                        && "event-456".equals(msg.eventId())
                        && msg.audioBase64() == null
                        && "Ease back slightly.".equals(msg.text())));
  }
}
