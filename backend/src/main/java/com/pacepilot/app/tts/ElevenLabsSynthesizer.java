package com.pacepilot.app.tts;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pacepilot.app.common.UpstreamException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** ElevenLabs-backed implementation of {@link TtsSynthesizer}. */
@Component
public class ElevenLabsSynthesizer implements TtsSynthesizer {

  private static final MediaType AUDIO_MPEG = MediaType.valueOf("audio/mpeg");

  private final RestClient restClient;
  private final MeterRegistry meters;
  private final String apiKey;
  private final String voiceId;
  private final String modelId;
  private final String outputFormat;

  public ElevenLabsSynthesizer(
      RestClient.Builder restClientBuilder,
      MeterRegistry meters,
      @Value("${pacer.tts.elevenlabs-base-url:https://api.elevenlabs.io}") String baseUrl,
      @Value("${pacer.tts.elevenlabs-api-key:}") String apiKey,
      @Value("${pacer.tts.elevenlabs-voice-id:}") String voiceId,
      @Value("${pacer.tts.elevenlabs-model-id:eleven_flash_v2_5}") String modelId,
      @Value("${pacer.tts.elevenlabs-output-format:mp3_44100_128}") String outputFormat) {
    this.restClient = restClientBuilder.baseUrl(baseUrl).build();
    this.meters = meters;
    this.apiKey = apiKey;
    this.voiceId = voiceId;
    this.modelId = modelId;
    this.outputFormat = outputFormat;
  }

  @Override
  public byte[] synthesize(String text) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("Missing pacer.tts.elevenlabs-api-key");
    }
    if (voiceId == null || voiceId.isBlank()) {
      throw new IllegalStateException("Missing pacer.tts.elevenlabs-voice-id");
    }

    meters.counter("tts.requests", "provider", "elevenlabs").increment();
    Timer.Sample sample = Timer.start(meters);
    try {
      byte[] audio =
          restClient
              .post()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/v1/text-to-speech/{voiceId}")
                          .queryParam("output_format", outputFormat)
                          .build(voiceId))
              .header("xi-api-key", apiKey)
              .contentType(MediaType.APPLICATION_JSON)
              .accept(AUDIO_MPEG)
              .body(new ElevenLabsRequest(text, modelId))
              .retrieve()
              .body(byte[].class);

      if (audio == null || audio.length == 0) {
        throw new IllegalStateException("ElevenLabs returned empty audio");
      }
      return audio;
    } catch (RuntimeException ex) {
      throw new UpstreamException("ElevenLabs TTS request failed: " + ex.getMessage(), ex);
    } finally {
      sample.stop(meters.timer("tts.latency", "provider", "elevenlabs"));
    }
  }

  private record ElevenLabsRequest(String text, @JsonProperty("model_id") String modelId) {}
}
