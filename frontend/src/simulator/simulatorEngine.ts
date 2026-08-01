// Pure physics/telemetry model for the simulator.
// No React, no side effects — easy to reason about and test.

import type { RunStatus, TelemetryFrame, TelemetryLocation, TelemetryMetadata } from './types';

const METADATA: TelemetryMetadata = {
  device_name: 'simulator',
  sw_version: 'sim_v1.0.0',
};

// A rough starting point (San Francisco) for the optional location field.
const START_LOCATION: TelemetryLocation = {
  latitude: 37.7749,
  longitude: -122.4194,
};

/** Mutable simulation state that evolves each tick. */
export interface SimState {
  sessionId: string;
  status: RunStatus;
  /** Target ground speed in m/s the user has dialed in for the "moving" statuses. */
  targetSpeedMps: number;
  /** Current (smoothed) speed in m/s. */
  speedMps: number;
  durationSeconds: number;
  distanceM: number;
  stepCount: number;
  heartRate: number;
  /**
   * User-set heart-rate target (bpm). When null the model drifts toward the
   * status default; when set the model eases toward this value instead, so the
   * user can push HR up (e.g. to trip the max-HR guardrail).
   */
  targetHeartRate: number | null;
}

/** Default target speeds (m/s) per status; only used when the status is "moving". */
export const DEFAULT_TARGET_SPEED_MPS: Record<RunStatus, number> = {
  running: 3.2, // ~5:12 min/km
  walking: 1.4, // brisk walk
  stopped: 0,
  paused: 0,
};

/** Heart-rate targets (bpm) the model drifts toward per status. */
const HEART_RATE_TARGET: Record<RunStatus, number> = {
  running: 158,
  walking: 110,
  stopped: 85,
  paused: 80,
};

const MIN_SPEED_MPS = 0.5;
const MAX_SPEED_MPS = 6.5;

/** Heart-rate slider bounds (bpm). Upper bound sits above the guardrail so it's reachable. */
const MIN_HEART_RATE = 50;
const MAX_HEART_RATE = 200;

/** Average stride length in metres, used to derive a plausible step count. */
const STRIDE_M = 0.9;

export function isMoving(status: RunStatus): boolean {
  return status === 'running' || status === 'walking';
}

export function createSession(status: RunStatus = 'running'): SimState {
  const target = DEFAULT_TARGET_SPEED_MPS[status];
  return {
    sessionId: crypto.randomUUID(),
    status,
    targetSpeedMps: target,
    speedMps: isMoving(status) ? target : 0,
    durationSeconds: 0,
    distanceM: 0,
    stepCount: 0,
    heartRate: HEART_RATE_TARGET[status],
    targetHeartRate: null,
  };
}

/** Switch run status; resets the target speed to the status default and clears any HR override. */
export function setStatus(state: SimState, status: RunStatus): SimState {
  return {
    ...state,
    status,
    targetSpeedMps: DEFAULT_TARGET_SPEED_MPS[status],
    targetHeartRate: null,
  };
}

/** Nudge the target speed (m/s) for moving statuses. Clamped to a sane range. */
export function nudgeSpeed(state: SimState, deltaMps: number): SimState {
  if (!isMoving(state.status)) return state;
  const targetSpeedMps = clamp(state.targetSpeedMps + deltaMps, MIN_SPEED_MPS, MAX_SPEED_MPS);
  return { ...state, targetSpeedMps };
}

/** Set the target speed (m/s) directly, e.g. from a slider. */
export function setTargetSpeed(state: SimState, targetMps: number): SimState {
  if (!isMoving(state.status)) return state;
  return { ...state, targetSpeedMps: clamp(targetMps, MIN_SPEED_MPS, MAX_SPEED_MPS) };
}

/** Override the heart-rate target (bpm) directly from a slider. */
export function setHeartRate(state: SimState, bpm: number): SimState {
  return { ...state, targetHeartRate: clamp(bpm, MIN_HEART_RATE, MAX_HEART_RATE) };
}

/** Drop the manual heart-rate override so HR drifts back toward the status default. */
export function clearHeartRate(state: SimState): SimState {
  return { ...state, targetHeartRate: null };
}

/**
 * Advance the simulation by `dtSeconds`.
 * - integrates distance from speed
 * - eases speed toward its target (or 0 when not moving)
 * - drifts heart rate toward the status target
 */
export function tick(state: SimState, dtSeconds: number): SimState {
  const moving = isMoving(state.status);
  const goalSpeed = moving ? state.targetSpeedMps : 0;

  // Ease speed toward the goal so pace changes are gradual, not instant.
  const speedMps = approach(state.speedMps, goalSpeed, 0.35 * dtSeconds);

  const distanceM = state.distanceM + speedMps * dtSeconds;
  const stepCount = Math.round(distanceM / STRIDE_M);

  // Heart rate eases toward the target; a mild jitter keeps it lively.
  // A manual override wins over the status default so the user can drive HR.
  const hrTarget = state.targetHeartRate ?? HEART_RATE_TARGET[state.status];
  const jitter = (Math.random() - 0.5) * 2; // +/- 1 bpm
  const heartRate = clamp(
    approach(state.heartRate, hrTarget, 0.08 * dtSeconds * Math.max(1, dtSeconds)) + jitter,
    50,
    200,
  );

  return {
    ...state,
    speedMps,
    distanceM,
    stepCount,
    heartRate,
    durationSeconds: state.durationSeconds + dtSeconds,
  };
}

/** Build the canonical outbound telemetry frame from current state. */
export function toFrame(state: SimState): TelemetryFrame {
  const speed = round(state.speedMps, 3);
  const moving = speed > 0;
  // seconds per metre; 0 when not moving so downstream code can guard on it.
  const pace = moving ? round(1 / speed, 4) : 0;
  const hr = Math.round(state.heartRate);

  return {
    event_id: crypto.randomUUID(),
    session_id: state.sessionId,
    status: state.status,
    timestamp: new Date().toISOString(),
    duration_seconds: round(state.durationSeconds, 1),
    activity_type: state.status,
    step_count: state.stepCount,
    speed_mps: speed,
    distance_m: round(state.distanceM, 1),
    metadata: METADATA,
    metrics: {
      heart_rate: { value: hr, unit: 'count/min', zone: heartRateZone(hr) },
      pace: { current_pace_seconds_per_meter: pace, unit: 'min/km' },
    },
    location: START_LOCATION,
  };
}

/** Format seconds-per-metre as a min/km pace string, e.g. "5:12 /km". */
export function formatPace(secondsPerMeter: number): string {
  if (!secondsPerMeter || secondsPerMeter <= 0) return '—';
  const secondsPerKm = secondsPerMeter * 1000;
  const minutes = Math.floor(secondsPerKm / 60);
  const seconds = Math.round(secondsPerKm % 60);
  const paddedSeconds = seconds.toString().padStart(2, '0');
  return `${minutes}:${paddedSeconds} /km`;
}

/** Format a duration in seconds as H:MM:SS (or M:SS under an hour). */
export function formatDuration(totalSeconds: number): string {
  const total = Math.floor(totalSeconds);
  const hours = Math.floor(total / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  const mm = minutes.toString().padStart(2, '0');
  const ss = seconds.toString().padStart(2, '0');
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${minutes}:${ss}`;
}

/** Metres per second -> kilometres per hour. */
export function mpsToKmh(mps: number): number {
  return mps * 3.6;
}

/** Format a speed (m/s) as a metric "12.3 km/h" string. */
export function formatSpeedKmh(mps: number): string {
  return `${mpsToKmh(mps).toFixed(1)} km/h`;
}

// --- small numeric helpers ---

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}

/** Move `current` toward `target` by up to `rate` fraction of the gap. */
function approach(current: number, target: number, rate: number): number {
  const factor = clamp(rate, 0, 1);
  return current + (target - current) * factor;
}

function round(value: number, decimals: number): number {
  const factor = 10 ** decimals;
  return Math.round(value * factor) / factor;
}

function heartRateZone(bpm: number): number {
  if (bpm < 100) return 1;
  if (bpm < 130) return 2;
  if (bpm < 150) return 3;
  if (bpm < 170) return 4;
  return 5;
}

export { MIN_SPEED_MPS, MAX_SPEED_MPS, MIN_HEART_RATE, MAX_HEART_RATE };
