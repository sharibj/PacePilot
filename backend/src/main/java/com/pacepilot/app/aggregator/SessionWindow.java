package com.pacepilot.app.aggregator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Per-session rolling window of telemetry samples. Holds the most recent samples within {@code
 * windowSeconds} and prunes anything older than the newest sample's timestamp. This class is a
 * plain mutable value holder with no Spring/Rabbit dependencies so it stays unit-testable; callers
 * are responsible for guarding concurrent mutation (see {@code TelemetryAggregator}).
 */
final class SessionWindow {

  /**
   * A single canonical reading reduced to just the fields the aggregator reasons about. {@code
   * epochMillis} is the sample time, {@code paceSecPerMeter} is pace in seconds per meter (may be
   * null when the device reports no pace, e.g. stopped), {@code heartRate} is bpm (may be null),
   * {@code distanceM} is cumulative session distance in meters, and {@code status} is the activity
   * status (running/walking/stopped/paused).
   */
  record Sample(
      long epochMillis,
      Double paceSecPerMeter,
      Double heartRate,
      Double distanceM,
      String status) {}

  private final int windowSeconds;
  private final Deque<Sample> samples = new ArrayDeque<>();

  SessionWindow(int windowSeconds) {
    this.windowSeconds = windowSeconds;
  }

  /** Adds a sample and evicts anything that fell outside the window relative to this new sample. */
  void add(Sample sample) {
    samples.addLast(sample);
    long cutoff = sample.epochMillis() - (long) windowSeconds * 1000L;
    while (!samples.isEmpty() && samples.peekFirst().epochMillis() < cutoff) {
      samples.pollFirst();
    }
  }

  List<Sample> samples() {
    return List.copyOf(samples);
  }

  Sample latest() {
    return samples.peekLast();
  }

  int size() {
    return samples.size();
  }

  int windowSeconds() {
    return windowSeconds;
  }
}
