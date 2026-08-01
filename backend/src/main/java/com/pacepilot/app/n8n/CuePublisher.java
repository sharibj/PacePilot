package com.pacepilot.app.n8n;

import com.pacepilot.app.messaging.dto.CueTextEvent;

/**
 * Seam for delivering a resolved cue downstream. Kept as a narrow functional interface so the
 * {@link PacerCueService} can be unit tested without a running broker. The production
 * implementation publishes to the {@code cue.text} routing key.
 */
@FunctionalInterface
public interface CuePublisher {
  void publish(CueTextEvent cue);
}
