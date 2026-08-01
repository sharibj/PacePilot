package com.pacepilot.app.n8n;

import com.pacepilot.app.messaging.PacerTopology;
import com.pacepilot.app.messaging.dto.AggregatedEvent;
import com.pacepilot.app.messaging.dto.CueTextEvent;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;

/**
 * Consumes aggregated telemetry events and produces spoken text cues.
 *
 * <p>Pipeline stage: {@code telemetry.aggregated -> [this] -> cue.text}. For this phase the n8n
 * webhook is stubbed (URL unset), so every event is resolved through the deterministic {@link
 * FallbackCuePolicy}. A real HTTP resolver can later be slotted in front of the fallback without
 * touching the idempotency / cooldown logic here.
 *
 * <p>Reliability rules (see {@code docs/pacer-contracts.md} section 6):
 *
 * <ul>
 *   <li><b>Idempotency</b> by {@code event_id}: each id is handled once. Ids are tracked in a
 *       size-capped, insertion-ordered set (a synchronized {@link LinkedHashMap#removeEldestEntry}
 *       LRU) so memory stays bounded under a long-running session; the oldest ids age out.
 *   <li><b>Cooldown</b> by {@code session_id}: at most one cue per session per {@code
 *       cooldown-seconds}. The last-emit {@link Instant} per session is kept in a {@link
 *       ConcurrentHashMap} and compared against an injectable {@link Clock} so the window is unit
 *       testable without sleeping.
 * </ul>
 */
public class PacerCueService {

  private static final Logger log = LoggerFactory.getLogger(PacerCueService.class);

  /** Upper bound on remembered event ids before the eldest are evicted. */
  private static final int MAX_SEEN_EVENT_IDS = 10_000;

  private final FallbackCuePolicy fallbackCuePolicy;
  private final CuePublisher cuePublisher;
  private final Clock clock;
  private final Duration cooldown;

  private final Set<String> seenEventIds =
      Collections.synchronizedSet(
          Collections.newSetFromMap(
              new LinkedHashMap<>(16, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                  return size() > MAX_SEEN_EVENT_IDS;
                }
              }));

  private final Map<String, Instant> lastEmitBySession = new ConcurrentHashMap<>();

  public PacerCueService(
      FallbackCuePolicy fallbackCuePolicy,
      CuePublisher cuePublisher,
      Clock clock,
      int cooldownSeconds) {
    this.fallbackCuePolicy = fallbackCuePolicy;
    this.cuePublisher = cuePublisher;
    this.clock = clock;
    this.cooldown = Duration.ofSeconds(cooldownSeconds);
  }

  @RabbitListener(
      id = "n8nHandler",
      queues = PacerTopology.Q_TELEMETRY_AGGREGATED,
      autoStartup = "${pacer.listener.n8n.enabled:true}")
  public void onAggregatedEvent(AggregatedEvent event) {
    handle(event);
  }

  /** Package-visible entry point exercised directly by unit tests. */
  void handle(AggregatedEvent event) {
    if (event == null || event.eventId() == null || event.sessionId() == null) {
      log.warn("Dropping aggregated event with missing id/session: {}", event);
      return;
    }

    if (!seenEventIds.add(event.eventId())) {
      log.debug("Skipping duplicate event_id={}", event.eventId());
      return;
    }

    if (isOnCooldown(event.sessionId())) {
      log.debug(
          "Cooldown active for session_id={}, suppressing cue for event_id={}",
          event.sessionId(),
          event.eventId());
      return;
    }

    CueTextEvent cue = resolveCue(event);
    if (cue == null || cue.cue() == null || cue.cue().isBlank()) {
      log.debug("No cue produced for event_id={}", event.eventId());
      return;
    }

    lastEmitBySession.put(event.sessionId(), clock.instant());
    cuePublisher.publish(cue);
    log.info(
        "Published cue for session_id={} event_id={} priority={}",
        cue.sessionId(),
        cue.eventId(),
        cue.priority());
  }

  private boolean isOnCooldown(String sessionId) {
    Instant lastEmit = lastEmitBySession.get(sessionId);
    if (lastEmit == null) {
      return false;
    }
    return Duration.between(lastEmit, clock.instant()).compareTo(cooldown) < 0;
  }

  /**
   * Resolves a cue for the event. The n8n HTTP path is a clean seam for later: when a webhook is
   * configured, an HTTP resolver would be tried here first and this fallback used on
   * failure/timeout. For now the fallback is always used.
   */
  private CueTextEvent resolveCue(AggregatedEvent event) {
    return fallbackCuePolicy.decide(event);
  }
}
