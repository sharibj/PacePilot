package com.pacepilot.app.n8n;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pacepilot.app.messaging.dto.AggregatedEvent;
import com.pacepilot.app.messaging.dto.CueTextEvent;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Calls the n8n pacer workflow to turn an {@link AggregatedEvent} into a spoken {@link
 * CueTextEvent}. The workflow POSTs to {@code /webhook/pacer-cue} and returns a structured cue
 * {@code {cue, priority, ttl_seconds, should_speak}} (see {@code docs/pacer-contracts.md} section
 * 4).
 *
 * <p>This resolver only performs the HTTP call and shape-mapping. The caller ({@link
 * PacerCueService}) owns the decision to fall back to {@link FallbackCuePolicy} when this returns
 * {@code null} on failure or timeout.
 *
 * <p>{@code should_speak=false} (or an empty {@code cue}) is a valid, successful "no cue" answer.
 * The resolver returns a {@link CueTextEvent} with a blank cue in that case so the caller
 * suppresses the cue rather than falling back to a canned one.
 */
public class N8nCueResolver {

  private static final Logger log = LoggerFactory.getLogger(N8nCueResolver.class);

  private final RestClient restClient;
  private final MeterRegistry meters;
  private final String webhookUrl;

  public N8nCueResolver(
      RestClient.Builder restClientBuilder, MeterRegistry meters, String webhookUrl) {
    this.restClient = restClientBuilder.build();
    this.meters = meters;
    this.webhookUrl = webhookUrl;
  }

  /**
   * Resolves a cue via the n8n webhook. Returns {@code null} on any failure or timeout so the
   * caller can fall back. Returns a {@link CueTextEvent} (possibly with a blank cue when the agent
   * chose not to speak) on success.
   */
  public CueTextEvent resolve(AggregatedEvent event) {
    Timer.Sample sample = Timer.start(meters);
    try {
      N8nCueResponse response =
          restClient
              .post()
              .uri(webhookUrl)
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .body(event)
              .retrieve()
              .body(N8nCueResponse.class);

      if (response == null) {
        log.warn("n8n returned empty body for event_id={}", event.eventId());
        meters.counter("pacer.n8n.result", "outcome", "empty").increment();
        return null;
      }

      boolean speak = response.shouldSpeak() == null || response.shouldSpeak();
      String cue = speak ? response.cue() : "";
      meters.counter("pacer.n8n.result", "outcome", "ok").increment();
      return new CueTextEvent(
          event.eventId(), event.sessionId(), cue, response.priority(), response.ttlSeconds());
    } catch (RuntimeException ex) {
      log.warn("n8n cue request failed for event_id={}: {}", event.eventId(), ex.getMessage());
      meters.counter("pacer.n8n.result", "outcome", "error").increment();
      return null;
    } finally {
      sample.stop(meters.timer("pacer.n8n.latency"));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  @JsonIgnoreProperties(ignoreUnknown = true)
  private record N8nCueResponse(
      String cue,
      String priority,
      @JsonProperty("ttl_seconds") Integer ttlSeconds,
      @JsonProperty("should_speak") Boolean shouldSpeak) {}
}
