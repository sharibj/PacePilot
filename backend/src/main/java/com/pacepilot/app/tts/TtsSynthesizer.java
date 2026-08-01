package com.pacepilot.app.tts;

/** Abstraction for text-to-speech providers used by the pacer audio pipeline. */
public interface TtsSynthesizer {

  /** Returns encoded audio bytes for the provided cue text. */
  byte[] synthesize(String text);
}
