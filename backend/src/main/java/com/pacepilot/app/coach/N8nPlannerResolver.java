package com.pacepilot.app.coach;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.pacepilot.app.common.UpstreamException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * Calls the n8n Planner workflow to answer a coach chat turn. The workflow POSTs to {@code
 * /webhook/planner-chat} with {@code {session_id, message}} and returns {@code {session_id, reply}}
 * from a running-coach agent (Claude plus Strava, calendar, running-plan, and MCP-memory tools).
 *
 * <p>Unlike the pacer resolver, there is no local fallback: the coach chat is only meaningful with
 * the real agent, so a failure surfaces as an {@link UpstreamException} for the controller to turn
 * into an error response.
 */
public class N8nPlannerResolver {

  private static final Logger log = LoggerFactory.getLogger(N8nPlannerResolver.class);

  private final RestClient restClient;
  private final MeterRegistry meters;
  private final String webhookUrl;

  public N8nPlannerResolver(
      RestClient.Builder restClientBuilder, MeterRegistry meters, String webhookUrl) {
    this.restClient = restClientBuilder.build();
    this.meters = meters;
    this.webhookUrl = webhookUrl;
  }

  /** Sends the message to the Planner and returns its reply text. */
  public String ask(String sessionId, String message) {
    meters.counter("coach.requests").increment();
    Timer.Sample sample = Timer.start(meters);
    try {
      PlannerResponse response =
          restClient
              .post()
              .uri(webhookUrl)
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON)
              .body(new PlannerRequest(sessionId, message))
              .retrieve()
              .body(PlannerResponse.class);

      if (response == null || response.reply() == null || response.reply().isBlank()) {
        throw new UpstreamException("Planner returned an empty reply", null);
      }
      return response.reply();
    } catch (UpstreamException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      log.warn("Planner chat request failed for session_id={}: {}", sessionId, ex.getMessage());
      throw new UpstreamException("Planner chat request failed: " + ex.getMessage(), ex);
    } finally {
      sample.stop(meters.timer("coach.latency"));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record PlannerRequest(@JsonProperty("session_id") String sessionId, String message) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record PlannerResponse(String reply) {}
}
