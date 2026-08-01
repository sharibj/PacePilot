package com.pacepilot.app.aggregator;

import com.pacepilot.app.messaging.dto.AggregatedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Pure salient-event detection over a {@link SessionWindow}. Contains no Spring/Rabbit dependencies
 * so the windowing math, thresholds, and unit conversions can be unit-tested in isolation.
 *
 * <p>The detector inspects the window after a new sample has been appended and decides whether the
 * newest reading is "salient" enough to forward downstream. Under stable pace and heart rate with
 * no status change it returns {@link Optional#empty()} — this suppression is what prevents an event
 * flood on {@code telemetry.aggregated}.
 *
 * <p>Priority when several conditions trigger at once: state change first (a run->walk transition
 * is the most actionable signal), then pace drift, then heart-rate drift.
 */
@Component
final class SalientEventDetector {

  // --- Thresholds. Structural ones are constants; sensitivity knobs are configurable so they can
  // be tuned per environment (e.g. lowered for a live demo) without a rebuild. ---

  /** Default sensitivity values, also used by the no-arg constructor in tests. */
  private static final int DEFAULT_MIN_SAMPLES_FOR_DRIFT = 3;

  private static final double DEFAULT_PACE_DRIFT_FRACTION = 0.08;
  private static final double DEFAULT_HR_DRIFT_BPM = 8.0;

  /**
   * A status transition is only salient once we have at least this many samples, so the very first
   * reading of a session (which has no prior status to compare against) never fires.
   */
  private static final int MIN_SAMPLES_FOR_STATE = 2;

  /**
   * Pace/HR drift needs a baseline built from earlier samples. We require a few readings before
   * trusting the baseline so a cold window does not emit noise. Lower it to react to changes
   * sooner (at the cost of more noise on a cold window).
   */
  private final int minSamplesForDrift;

  /**
   * Fractional pace deviation (current vs. baseline) that counts as drift. 0.08 (8%) ignores normal
   * jitter but trips on a deliberate slow-down/speed-up. Lower it to fire on smaller pace changes.
   */
  private final double paceDriftFraction;

  /**
   * Heart-rate drift in bpm across the window that counts as salient. 8 bpm reflects a meaningful
   * cardiac drift rather than beat-to-beat noise. Lower it to fire on smaller HR changes.
   */
  private final double hrDriftBpm;

  private static final double METERS_PER_KM = 1000.0;

  SalientEventDetector(
      @Value("${pacer.aggregator.min-samples-for-drift:3}") int minSamplesForDrift,
      @Value("${pacer.aggregator.pace-drift-fraction:0.08}") double paceDriftFraction,
      @Value("${pacer.aggregator.hr-drift-bpm:8.0}") double hrDriftBpm) {
    this.minSamplesForDrift = minSamplesForDrift;
    this.paceDriftFraction = paceDriftFraction;
    this.hrDriftBpm = hrDriftBpm;
  }

  /** Uses the default sensitivity thresholds. */
  SalientEventDetector() {
    this(DEFAULT_MIN_SAMPLES_FOR_DRIFT, DEFAULT_PACE_DRIFT_FRACTION, DEFAULT_HR_DRIFT_BPM);
  }

  /**
   * Converts pace expressed in seconds per meter to seconds per kilometer. Kept here (rather than
   * inline) so it has a single tested home. Example: 0.30 s/m -> 300 s/km.
   */
  static int paceSecPerMeterToSecPerKm(double paceSecPerMeter) {
    return (int) Math.round(paceSecPerMeter * METERS_PER_KM);
  }

  /**
   * Inspects the window (already containing the newest sample) and returns a salient {@link
   * AggregatedEvent}, or empty when nothing noteworthy happened.
   */
  Optional<AggregatedEvent> detect(String sessionId, SessionWindow window) {
    List<SessionWindow.Sample> samples = window.samples();
    if (samples.isEmpty()) {
      return Optional.empty();
    }
    SessionWindow.Sample latest = samples.get(samples.size() - 1);

    String eventType = classify(samples, latest);
    if (eventType == null) {
      return Optional.empty();
    }
    return Optional.of(build(sessionId, window, samples, latest, eventType));
  }

  private String classify(List<SessionWindow.Sample> samples, SessionWindow.Sample latest) {
    // 1) Abrupt state change: newest status differs from the immediately preceding sample.
    if (samples.size() >= MIN_SAMPLES_FOR_STATE) {
      SessionWindow.Sample prev = samples.get(samples.size() - 2);
      if (prev.status() != null
          && latest.status() != null
          && !prev.status().equals(latest.status())) {
        return "state_change";
      }
    }

    if (samples.size() >= minSamplesForDrift) {
      List<SessionWindow.Sample> baseline = samples.subList(0, samples.size() - 1);

      // 2) Pace drift: newest pace vs. baseline average pace.
      Double baselinePace = averagePace(baseline);
      if (baselinePace != null && latest.paceSecPerMeter() != null && baselinePace > 0) {
        double deviation = Math.abs(latest.paceSecPerMeter() - baselinePace) / baselinePace;
        if (deviation >= paceDriftFraction) {
          return "pace_drift";
        }
      }

      // 3) HR drift: spread between the highest and lowest heart rate across the window.
      Double drift = heartRateDrift(samples);
      if (drift != null && drift >= hrDriftBpm) {
        return "hr_drift";
      }
    }
    return null;
  }

  private AggregatedEvent build(
      String sessionId,
      SessionWindow window,
      List<SessionWindow.Sample> samples,
      SessionWindow.Sample latest,
      String eventType) {

    Double avgPace = averagePace(samples);
    Integer avgPaceSecPerKm = avgPace == null ? null : paceSecPerMeterToSecPerKm(avgPace);

    Double avgHr = averageHeartRate(samples);
    Integer avgHeartRate = avgHr == null ? null : (int) Math.round(avgHr);

    Double drift = heartRateDrift(samples);
    Integer hrDriftBpm = drift == null ? null : (int) Math.round(drift);

    Double distanceM = distanceCovered(samples);

    AggregatedEvent.Summary summary =
        new AggregatedEvent.Summary(avgPaceSecPerKm, avgHeartRate, hrDriftBpm, distanceM);

    // No plan store exists yet, so run_context is omitted (null) rather than invented. Downstream
    // consumers treat a missing run_context as "no plan available".
    return new AggregatedEvent(
        UUID.randomUUID().toString(),
        sessionId,
        Instant.ofEpochMilli(latest.epochMillis()).toString(),
        window.windowSeconds(),
        eventType,
        summary,
        null);
  }

  // --- window aggregates ---

  private Double averagePace(List<SessionWindow.Sample> samples) {
    double sum = 0;
    int n = 0;
    for (SessionWindow.Sample s : samples) {
      if (s.paceSecPerMeter() != null) {
        sum += s.paceSecPerMeter();
        n++;
      }
    }
    return n == 0 ? null : sum / n;
  }

  private Double averageHeartRate(List<SessionWindow.Sample> samples) {
    double sum = 0;
    int n = 0;
    for (SessionWindow.Sample s : samples) {
      if (s.heartRate() != null) {
        sum += s.heartRate();
        n++;
      }
    }
    return n == 0 ? null : sum / n;
  }

  /** Heart-rate drift = max bpm minus min bpm across the window (null if no HR readings). */
  private Double heartRateDrift(List<SessionWindow.Sample> samples) {
    Double min = null;
    Double max = null;
    for (SessionWindow.Sample s : samples) {
      if (s.heartRate() == null) {
        continue;
      }
      if (min == null || s.heartRate() < min) {
        min = s.heartRate();
      }
      if (max == null || s.heartRate() > max) {
        max = s.heartRate();
      }
    }
    return (min == null) ? null : max - min;
  }

  /** Distance covered within the window = newest cumulative distance minus oldest. */
  private Double distanceCovered(List<SessionWindow.Sample> samples) {
    Double first = null;
    Double last = null;
    for (SessionWindow.Sample s : samples) {
      if (s.distanceM() == null) {
        continue;
      }
      if (first == null) {
        first = s.distanceM();
      }
      last = s.distanceM();
    }
    if (first == null) {
      return null;
    }
    return Math.max(0.0, last - first);
  }
}
