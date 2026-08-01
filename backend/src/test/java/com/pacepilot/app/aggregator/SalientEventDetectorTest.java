package com.pacepilot.app.aggregator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.pacepilot.app.messaging.dto.AggregatedEvent;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Fast unit tests for the pure windowing + salient-event detection logic. No Spring or broker
 * involved. A steady run should be suppressed; a clear pace/HR change or a status transition should
 * surface exactly one salient event with a sensible summary.
 */
class SalientEventDetectorTest {

  private static final String SESSION = "8e5c1b4a-73d1-4e6e-9051-ceb6a45f611a";
  private static final int WINDOW = 30;

  private final SalientEventDetector detector = new SalientEventDetector();

  private static SessionWindow.Sample sample(
      long epochMillis, Double paceSecPerMeter, Double hr, Double distanceM, String status) {
    return new SessionWindow.Sample(epochMillis, paceSecPerMeter, hr, distanceM, status);
  }

  /** Feed the window and return the detector result after the final sample. */
  private Optional<AggregatedEvent> feed(SessionWindow window, SessionWindow.Sample... samples) {
    Optional<AggregatedEvent> last = Optional.empty();
    for (SessionWindow.Sample s : samples) {
      window.add(s);
      last = detector.detect(SESSION, window);
    }
    return last;
  }

  @Test
  void steadyPaceAndHeartRate_noStatusChange_isSuppressed() {
    SessionWindow window = new SessionWindow(WINDOW);
    // Six seconds of near-identical running telemetry: nothing salient should fire.
    Optional<AggregatedEvent> result =
        feed(
            window,
            sample(0, 0.30, 150.0, 0.0, "running"),
            sample(1000, 0.30, 151.0, 3.3, "running"),
            sample(2000, 0.301, 150.0, 6.6, "running"),
            sample(3000, 0.299, 152.0, 9.9, "running"),
            sample(4000, 0.30, 151.0, 13.2, "running"),
            sample(5000, 0.30, 150.0, 16.5, "running"));

    assertThat(result).isEmpty();
  }

  @Test
  void clearPaceIncrease_emitsPaceDrift_withSummary() {
    SessionWindow window = new SessionWindow(WINDOW);
    // Baseline ~0.30 s/m then a jump to 0.36 s/m (20% slower) -> pace drift.
    Optional<AggregatedEvent> result =
        feed(
            window,
            sample(0, 0.30, 150.0, 0.0, "running"),
            sample(1000, 0.30, 150.0, 3.3, "running"),
            sample(2000, 0.30, 150.0, 6.6, "running"),
            sample(3000, 0.36, 150.0, 9.5, "running"));

    assertThat(result).isPresent();
    AggregatedEvent event = result.get();
    assertThat(event.eventType()).isEqualTo("pace_drift");
    assertThat(event.sessionId()).isEqualTo(SESSION);
    assertThat(event.eventId()).isNotBlank();
    assertThat(event.windowSeconds()).isEqualTo(WINDOW);
    // avg pace over the 4 samples = (0.30*3 + 0.36)/4 = 0.315 s/m -> 315 s/km.
    assertThat(event.summary().avgPaceSecPerKm()).isEqualTo(315);
    assertThat(event.summary().avgHeartRate()).isEqualTo(150);
    assertThat(event.summary().distanceM()).isEqualTo(9.5);
    // No plan store yet: run_context is intentionally null.
    assertThat(event.runContext()).isNull();
  }

  @Test
  void clearPaceDecrease_emitsPaceDrift() {
    SessionWindow window = new SessionWindow(WINDOW);
    Optional<AggregatedEvent> result =
        feed(
            window,
            sample(0, 0.32, 150.0, 0.0, "running"),
            sample(1000, 0.32, 150.0, 3.1, "running"),
            sample(2000, 0.32, 150.0, 6.2, "running"),
            sample(3000, 0.27, 150.0, 9.9, "running")); // ~16% faster

    assertThat(result).isPresent();
    assertThat(result.get().eventType()).isEqualTo("pace_drift");
  }

  @Test
  void heartRateClimbingBeyondThreshold_emitsHrDrift() {
    SessionWindow window = new SessionWindow(WINDOW);
    // Pace steady, HR climbs 150 -> 162 (12 bpm spread) over the window -> hr drift.
    Optional<AggregatedEvent> result =
        feed(
            window,
            sample(0, 0.30, 150.0, 0.0, "running"),
            sample(1000, 0.30, 153.0, 3.3, "running"),
            sample(2000, 0.30, 158.0, 6.6, "running"),
            sample(3000, 0.30, 162.0, 9.9, "running"));

    assertThat(result).isPresent();
    AggregatedEvent event = result.get();
    assertThat(event.eventType()).isEqualTo("hr_drift");
    assertThat(event.summary().heartRateDriftBpm()).isEqualTo(12);
  }

  @Test
  void runningToWalking_emitsStateChange() {
    SessionWindow window = new SessionWindow(WINDOW);
    Optional<AggregatedEvent> result =
        feed(
            window,
            sample(0, 0.30, 150.0, 0.0, "running"),
            sample(1000, 0.30, 150.0, 3.3, "running"),
            sample(2000, 0.65, 148.0, 5.0, "walking")); // slowed and status flipped

    assertThat(result).isPresent();
    // State change wins over any concurrent pace/HR drift: it is the most actionable signal.
    assertThat(result.get().eventType()).isEqualTo("state_change");
  }

  @Test
  void paceUnitConversion_secPerMeterToSecPerKm() {
    // 0.30 s/m == 300 s/km; 0.31 s/m rounds to 310 s/km.
    assertThat(SalientEventDetector.paceSecPerMeterToSecPerKm(0.30)).isEqualTo(300);
    assertThat(SalientEventDetector.paceSecPerMeterToSecPerKm(0.31)).isEqualTo(310);
    assertThat(SalientEventDetector.paceSecPerMeterToSecPerKm(0.3225)).isEqualTo(323);
  }

  @Test
  void windowEvictsSamplesOlderThanWindowSeconds() {
    SessionWindow window = new SessionWindow(5);
    window.add(sample(0, 0.30, 150.0, 0.0, "running"));
    window.add(sample(1000, 0.30, 150.0, 3.0, "running"));
    // 10s later: the two early samples fall outside the 5s window.
    window.add(sample(10_000, 0.30, 150.0, 30.0, "running"));

    assertThat(window.size()).isEqualTo(1);
    assertThat(window.latest().distanceM()).isEqualTo(30.0, within(1e-9));
  }
}
