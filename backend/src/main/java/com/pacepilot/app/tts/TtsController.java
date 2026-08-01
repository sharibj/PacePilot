package com.pacepilot.app.tts;

import com.pacepilot.app.tts.dto.TtsSpeakRequest;
import com.pacepilot.app.tts.dto.TtsSpeakResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger endpoint for TTS integration testing. This stands in for the future n8n agent
 * output and pushes the synthesized cue directly to connected WebSocket clients.
 */
@RestController
@RequestMapping("/api/tts")
public class TtsController {

  private final TtsService ttsService;

  public TtsController(TtsService ttsService) {
    this.ttsService = ttsService;
  }

  @PostMapping("/speak")
  public TtsSpeakResponse speak(@Valid @RequestBody TtsSpeakRequest request) {
    TtsService.SpeakResult result =
        ttsService.speakToSession(request.sessionId(), request.eventId(), request.text());
    return new TtsSpeakResponse(
        result.eventId(),
        request.sessionId(),
        result.delivered(),
        result.audioGenerated(),
        result.fallbackReason());
  }
}
