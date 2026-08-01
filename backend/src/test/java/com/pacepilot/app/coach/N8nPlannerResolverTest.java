package com.pacepilot.app.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.pacepilot.app.common.UpstreamException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class N8nPlannerResolverTest {

  private static final String URL = "http://n8n.test/webhook/planner-chat";

  private MockRestServiceServer server;
  private N8nPlannerResolver resolver;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    resolver = new N8nPlannerResolver(builder, new SimpleMeterRegistry(), URL);
  }

  @Test
  void mapsReplyFromPlanner() {
    server
        .expect(requestTo(URL))
        .andExpect(method(HttpMethod.POST))
        .andExpect(jsonPath("$.session_id").value("s1"))
        .andExpect(jsonPath("$.message").value("what's next?"))
        .andRespond(
            withSuccess(
                "{\"session_id\":\"s1\",\"reply\":\"Your next run is an easy 5k tomorrow.\"}",
                MediaType.APPLICATION_JSON));

    String reply = resolver.ask("s1", "what's next?");

    assertThat(reply).isEqualTo("Your next run is an easy 5k tomorrow.");
    server.verify();
  }

  @Test
  void emptyReplyThrowsUpstream() {
    server
        .expect(requestTo(URL))
        .andRespond(
            withSuccess("{\"session_id\":\"s1\",\"reply\":\"\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> resolver.ask("s1", "hi")).isInstanceOf(UpstreamException.class);
  }

  @Test
  void serverErrorThrowsUpstream() {
    server.expect(requestTo(URL)).andRespond(withServerError());

    assertThatThrownBy(() -> resolver.ask("s1", "hi")).isInstanceOf(UpstreamException.class);
  }
}
