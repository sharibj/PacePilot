package com.pacepilot.app.tts;

import static org.assertj.core.api.Assertions.assertThat;

import com.pacepilot.app.messaging.dto.CueTextEvent;
import com.pacepilot.app.messaging.dto.PacerAudioMessage;
import org.junit.jupiter.api.Test;

class PassthroughCueSynthesizerTest {

  private final PassthroughCueSynthesizer synthesizer = new PassthroughCueSynthesizer();

  @Test
  void producesTextOnlyPacerAudioPreservingIds() {
    CueTextEvent cue =
        new CueTextEvent("evt-1", "sess-1", "Ease back to tempo pace.", "medium", 20);

    PacerAudioMessage message = synthesizer.synthesize(cue);

    assertThat(message.type()).isEqualTo("pacer_audio");
    assertThat(message.eventId()).isEqualTo("evt-1");
    assertThat(message.sessionId()).isEqualTo("sess-1");
    assertThat(message.text()).isEqualTo("Ease back to tempo pace.");
    assertThat(message.audioBase64()).isNull();
  }
}
