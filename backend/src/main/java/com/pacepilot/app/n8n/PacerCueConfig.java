package com.pacepilot.app.n8n;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Wires the n8n handler beans. The {@link Clock} is exposed as a bean so it can be overridden in
 * tests, and the cooldown / guardrail thresholds are read from configuration ({@code
 * pacer.cue.cooldown-seconds}, {@code pacer.guardrail.max-hr}).
 *
 * <p>The {@link N8nCueResolver} bean is created only when {@code pacer.n8n.pacer-webhook-url} is
 * set; otherwise it is {@code null} and {@link PacerCueService} runs the deterministic fallback for
 * every event.
 */
@Configuration
public class PacerCueConfig {

  @Bean
  Clock pacerClock() {
    return Clock.systemUTC();
  }

  @Bean
  FallbackCuePolicy fallbackCuePolicy(@Value("${pacer.guardrail.max-hr:190}") int maxHeartRate) {
    return new FallbackCuePolicy(maxHeartRate);
  }

  @Bean
  N8nCueResolver n8nCueResolver(
      RestClient.Builder restClientBuilder,
      MeterRegistry meters,
      @Value("${pacer.n8n.pacer-webhook-url:}") String webhookUrl,
      @Value("${pacer.n8n.timeout-ms:1200}") int timeoutMs) {
    if (!StringUtils.hasText(webhookUrl)) {
      return null;
    }
    Duration timeout = Duration.ofMillis(timeoutMs);
    ClientHttpRequestFactorySettings settings =
        ClientHttpRequestFactorySettings.defaults()
            .withConnectTimeout(timeout)
            .withReadTimeout(timeout);
    // Use the SimpleClientHttpRequestFactory (one fresh HttpURLConnection per request). The n8n
    // webhook answers every call with "Connection: close"; pooling clients (JDK HttpClient, Apache
    // HC5) then hand out a half-closed socket on the next call, which stalls until the read timeout
    // so the request never reaches n8n. A non-pooling factory honors the close and avoids that.
    RestClient.Builder timed =
        restClientBuilder.requestFactory(ClientHttpRequestFactoryBuilder.simple().build(settings));
    return new N8nCueResolver(timed, meters, webhookUrl);
  }

  @Bean
  PacerCueService pacerCueService(
      FallbackCuePolicy fallbackCuePolicy,
      org.springframework.beans.factory.ObjectProvider<N8nCueResolver> n8nCueResolver,
      CuePublisher cuePublisher,
      Clock pacerClock,
      @Value("${pacer.cue.cooldown-seconds:20}") int cooldownSeconds,
      MeterRegistry meters) {
    return new PacerCueService(
        fallbackCuePolicy,
        n8nCueResolver.getIfAvailable(),
        cuePublisher,
        pacerClock,
        cooldownSeconds,
        meters);
  }
}
