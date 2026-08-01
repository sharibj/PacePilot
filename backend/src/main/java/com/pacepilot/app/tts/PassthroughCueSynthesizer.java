package com.pacepilot.app.tts;

import com.pacepilot.app.messaging.dto.CueTextEvent;
import com.pacepilot.app.messaging.dto.PacerAudioMessage;
import org.springframework.stereotype.Component;

/**
 * Text-only synthesizer used while ElevenLabs is stubbed. It carries the cue text straight through
 * to the WebSocket layer with a null audio payload; the simulator displays the text and tolerates
 * the missing audio.
 */
@Component
public class PassthroughCueSynthesizer implements CueSynthesizer {

  @Override
  public PacerAudioMessage synthesize(CueTextEvent cue) {
    return PacerAudioMessage.textOnly(cue.sessionId(), cue.eventId(), cue.cue());
  }
}
