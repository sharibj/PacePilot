package com.pacepilot.app.n8n;

import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the n8n handler beans. The {@link Clock} is exposed as a bean so it can be overridden in
 * tests, and the cooldown / guardrail thresholds are read from configuration ({@code
 * pacer.cue.cooldown-seconds}, {@code pacer.guardrail.max-hr}).
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
  PacerCueService pacerCueService(
      FallbackCuePolicy fallbackCuePolicy,
      CuePublisher cuePublisher,
      Clock pacerClock,
      @Value("${pacer.cue.cooldown-seconds:20}") int cooldownSeconds) {
    return new PacerCueService(fallbackCuePolicy, cuePublisher, pacerClock, cooldownSeconds);
  }
}
