// Shared types for the telemetry simulator.
// The outbound frame matches the canonical backend telemetry schema (snake_case).

export type RunStatus = 'running' | 'walking' | 'stopped' | 'paused';

export interface HeartRateMetric {
  value: number;
  unit: 'count/min';
  zone: number;
}

export interface PaceMetric {
  current_pace_seconds_per_meter: number;
  unit: 'min/mi';
}

export interface TelemetryMetrics {
  heart_rate: HeartRateMetric;
  pace: PaceMetric;
}

export interface TelemetryLocation {
  latitude: number;
  longitude: number;
}

export interface TelemetryMetadata {
  device_name: 'simulator';
  sw_version: 'sim_v1.0.0';
}

/** Outbound frame: simulator -> backend. */
export interface TelemetryFrame {
  event_id: string;
  session_id: string;
  status: RunStatus;
  timestamp: string;
  duration_seconds: number;
  activity_type: RunStatus;
  step_count: number;
  speed_mps: number;
  distance_m: number;
  metadata: TelemetryMetadata;
  metrics: TelemetryMetrics;
  location?: TelemetryLocation;
}

/** Inbound frame: backend -> simulator. */
export interface PacerAudioMessage {
  type: 'pacer_audio';
  session_id: string;
  event_id: string;
  audio_base64: string | null;
  text: string;
}

export type ConnectionState = 'closed' | 'connecting' | 'open' | 'error';
