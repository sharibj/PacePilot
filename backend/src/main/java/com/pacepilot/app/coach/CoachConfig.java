package com.pacepilot.app.coach;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Wires the coach chat resolver. The Planner agent is slow (Claude plus tool calls, ~13s+), so the
 * timeout is generous and configured separately from the pacer's tight budget.
 */
@Configuration
public class CoachConfig {

  /**
   * Builds the Planner {@link N8nPlannerResolver} from a fresh {@link RestClient.Builder} so its
   * 30s timeout stays isolated from the pacer's 1.2s client.
   *
   * <p>Uses the non-pooling {@link ClientHttpRequestFactoryBuilder#simple()} factory: n8n answers
   * every webhook call with {@code Connection: close}, and pooling clients then reuse a half-closed
   * socket on the next call and stall until the read timeout. A fresh connection per request avoids
   * that.
   */
  @Bean
  N8nPlannerResolver n8nPlannerResolver(
      MeterRegistry meters,
      @Value("${pacer.n8n.planner-webhook-url:}") String webhookUrl,
      @Value("${pacer.n8n.planner-timeout-ms:120000}") int timeoutMs) {
    Duration timeout = Duration.ofMillis(timeoutMs);
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(timeout)
            .withReadTimeout(timeout);
    RestClient.Builder builder =
        RestClient.builder()
            .requestFactory(ClientHttpRequestFactoryBuilder.simple().build(settings));
    return new N8nPlannerResolver(builder, meters, webhookUrl);
  }
}
