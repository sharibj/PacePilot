package com.pacepilot.app.tts;

import com.pacepilot.app.messaging.dto.CueTextEvent;
import com.pacepilot.app.messaging.dto.PacerAudioMessage;

/**
 * Turns a text cue into a deliverable {@link PacerAudioMessage}. ElevenLabs synthesis is stubbed
 * for this phase, so the default implementation produces a text-only message (no {@code
 * audio_base64}). A real synthesizer can be slotted in behind this seam later without touching the
 * consumer.
 */
public interface CueSynthesizer {

  PacerAudioMessage synthesize(CueTextEvent cue);
}
