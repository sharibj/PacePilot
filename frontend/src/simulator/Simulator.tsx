import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  createSession,
  formatDuration,
  formatPace,
  isMoving,
  MAX_SPEED_MPS,
  MIN_SPEED_MPS,
  nudgeSpeed,
  setStatus,
  setTargetSpeed,
  type SimState,
  tick,
  toFrame,
} from './simulatorEngine';
import { resolveSocketBaseUrl, useTelemetrySocket } from './useTelemetrySocket';
import type { ConnectionState, PacerAudioMessage, RunStatus } from './types';

const RUN_STATUSES: RunStatus[] = ['running', 'walking', 'stopped', 'paused'];
const DEFAULT_INTERVAL_MS = 1000;
const MAX_CUE_HISTORY = 8;

interface CueEntry {
  id: string;
  text: string;
  hasAudio: boolean;
  at: string;
}

const CONNECTION_LABEL: Record<ConnectionState, string> = {
  closed: 'Disconnected',
  connecting: 'Connecting…',
  open: 'Connected',
  error: 'Error',
};

export default function Simulator() {
  const [sim, setSim] = useState<SimState>(() => createSession('running'));
  const [streaming, setStreaming] = useState(false);
  const [intervalMs, setIntervalMs] = useState(DEFAULT_INTERVAL_MS);
  const [cues, setCues] = useState<CueEntry[]>([]);

  const handleCue = useCallback((cue: PacerAudioMessage) => {
    setCues((prev) =>
      [
        {
          id: cue.event_id || crypto.randomUUID(),
          text: cue.text,
          hasAudio: Boolean(cue.audio_base64),
          at: new Date().toLocaleTimeString(),
        },
        ...prev,
      ].slice(0, MAX_CUE_HISTORY),
    );
  }, []);

  const socket = useTelemetrySocket({ onCue: handleCue });
  const { state: connState, error: connError, connect, disconnect, send } = socket;

  // Keep the latest sim + interval in refs so the tick loop stays stable.
  const simRef = useRef(sim);
  simRef.current = sim;
  const intervalRef = useRef(intervalMs);
  intervalRef.current = intervalMs;

  // Streaming loop: advance the model and emit a frame each interval.
  useEffect(() => {
    if (!streaming) return;
    const dtSeconds = intervalRef.current / 1000;
    const timer = window.setInterval(() => {
      setSim((prev) => {
        const next = tick(prev, dtSeconds);
        send(toFrame(next));
        return next;
      });
    }, intervalMs);
    return () => window.clearInterval(timer);
  }, [streaming, intervalMs, send]);

  const startStreaming = () => {
    connect();
    setStreaming(true);
  };

  const stopStreaming = () => {
    setStreaming(false);
    disconnect();
  };

  const resetSession = () => {
    stopStreaming();
    setSim(createSession('running'));
    setCues([]);
  };

  const changeStatus = (status: RunStatus) => setSim((prev) => setStatus(prev, status));
  const bumpSpeed = (deltaMps: number) => setSim((prev) => nudgeSpeed(prev, deltaMps));
  const onSpeedSlider = (value: number) => setSim((prev) => setTargetSpeed(prev, value));

  const frame = useMemo(() => toFrame(sim), [sim]);
  const pace = frame.metrics.pace.current_pace_seconds_per_meter;
  const moving = isMoving(sim.status);
  const latestCue = cues[0];

  return (
    <section className="card sim">
      <h2>Telemetry Simulator</h2>

      <div className="sim-conn">
        <span className={`sim-badge sim-badge--${connState}`}>{CONNECTION_LABEL[connState]}</span>
        <span className="sim-endpoint">{resolveSocketBaseUrl()}/ws/telemetry</span>
        {streaming && <span className="sim-live">● streaming</span>}
      </div>
      {connError && <p className="error">{connError}</p>}

      <div className="row sim-controls">
        {RUN_STATUSES.map((status) => (
          <button
            key={status}
            onClick={() => changeStatus(status)}
            className={sim.status === status ? 'sim-active' : 'sim-inactive'}
          >
            {status}
          </button>
        ))}
      </div>

      <div className="row sim-controls">
        {!streaming ? (
          <button onClick={startStreaming}>Start streaming</button>
        ) : (
          <button onClick={stopStreaming}>Stop streaming</button>
        )}
        <button className="sim-inactive" onClick={resetSession}>
          New session
        </button>
      </div>

      <div className="sim-field">
        <label htmlFor="sim-speed">
          Target speed: <strong>{moving ? `${sim.targetSpeedMps.toFixed(1)} m/s` : '—'}</strong>
        </label>
        <div className="row sim-speed-row">
          <button className="sim-inactive" onClick={() => bumpSpeed(-0.2)} disabled={!moving}>
            −
          </button>
          <input
            id="sim-speed"
            type="range"
            min={MIN_SPEED_MPS}
            max={MAX_SPEED_MPS}
            step={0.1}
            value={sim.targetSpeedMps}
            disabled={!moving}
            onChange={(e) => onSpeedSlider(Number(e.target.value))}
          />
          <button className="sim-inactive" onClick={() => bumpSpeed(0.2)} disabled={!moving}>
            +
          </button>
        </div>
      </div>

      <div className="sim-field">
        <label htmlFor="sim-interval">
          Emit interval: <strong>{(intervalMs / 1000).toFixed(1)}s</strong>
        </label>
        <input
          id="sim-interval"
          type="range"
          min={250}
          max={3000}
          step={250}
          value={intervalMs}
          onChange={(e) => setIntervalMs(Number(e.target.value))}
        />
      </div>

      <dl className="sim-metrics">
        <Metric label="Status" value={sim.status} />
        <Metric label="Pace" value={formatPace(pace)} />
        <Metric label="Heart rate" value={`${frame.metrics.heart_rate.value} bpm`} />
        <Metric label="Speed" value={`${frame.speed_mps.toFixed(2)} m/s`} />
        <Metric label="Distance" value={`${(frame.distance_m / 1000).toFixed(2)} km`} />
        <Metric label="Duration" value={formatDuration(frame.duration_seconds)} />
      </dl>

      <div className="sim-cue">
        <h3>Latest coaching cue</h3>
        {latestCue ? (
          <p className="reply">
            {latestCue.hasAudio ? '🔊 ' : ''}
            {latestCue.text}
          </p>
        ) : (
          <p className="sim-muted">No cues yet. Start streaming to receive coaching.</p>
        )}
      </div>

      {cues.length > 1 && (
        <div className="sim-cue-history">
          <h3>Cue history</h3>
          <ul>
            {cues.slice(1).map((cue) => (
              <li key={cue.id}>
                <span>{cue.text}</span>
                <span className="sim-muted">{cue.at}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </section>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div className="sim-metric">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
