package com.pacepilot.app.n8n;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.pacepilot.app.messaging.dto.AggregatedEvent;
import com.pacepilot.app.messaging.dto.CueTextEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class N8nCueResolverTest {

  private static final String URL = "http://n8n.test/webhook/pacer-cue";

  private MockRestServiceServer server;
  private N8nCueResolver resolver;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    resolver = new N8nCueResolver(builder, new SimpleMeterRegistry(), URL);
  }

  private AggregatedEvent event() {
    return new AggregatedEvent(
        "e1",
        "s1",
        "2026-08-01T09:00:00Z",
        30,
        "pace_drift",
        new AggregatedEvent.Summary(322, 168, 4, 150.0),
        new AggregatedEvent.RunContext("tempo", 300, 2800.0));
  }

  @Test
  void mapsSuccessfulResponseToCue() {
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.event_id").value("e1"))
        .andExpect(jsonPath("$.event_type").value("pace_drift"))
        .andExpect(jsonPath("$.summary.avg_heart_rate").value(168))
        .andRespond(
            withSuccess(
                """
                {"event_id":"e1","session_id":"s1","cue":"Ease back to tempo.",\
                "priority":"medium","ttl_seconds":20,"should_speak":true}""",
                MediaType.APPLICATION_JSON));

    CueTextEvent cue = resolver.resolve(event());

    assertThat(cue).isNotNull();
    assertThat(cue.eventId()).isEqualTo("e1");
    assertThat(cue.sessionId()).isEqualTo("s1");
    assertThat(cue.cue()).isEqualTo("Ease back to tempo.");
    assertThat(cue.priority()).isEqualTo("medium");
    assertThat(cue.ttlSeconds()).isEqualTo(20);
    server.verify();
  }

  @Test
  void shouldSpeakFalseYieldsBlankCue() {
    server
        .expect(requestTo(URL))
        .andRespond(
            withSuccess(
                """
                {"event_id":"e1","session_id":"s1","cue":"",\
                "priority":"low","ttl_seconds":15,"should_speak":false}""",
                MediaType.APPLICATION_JSON));

    CueTextEvent cue = resolver.resolve(event());

    assertThat(cue).isNotNull();
    assertThat(cue.cue()).isBlank();
  }

  @Test
  void serverErrorReturnsNullSoCallerFallsBack() {
    server.expect(requestTo(URL)).andRespond(withServerError());

    assertThat(resolver.resolve(event())).isNull();
  }
}
