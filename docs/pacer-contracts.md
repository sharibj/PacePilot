# Pacer Contracts

This document defines stable contracts between simulator, backend modules, RabbitMQ, n8n pacer workflow, and TTS.

## 1) Canonical telemetry event

The simulator and unifier use the same schema. Unifier validates and enriches metadata but does not reshape the payload.

### Required fields

- `event_id`: UUID string
- `session_id`: UUID string
- `timestamp`: ISO-8601 UTC timestamp
- `status`: `running | walking | stopped | paused`
- `duration_seconds`: number
- `distance_m`: number
- `speed_mps`: number
- `metrics.heart_rate.value`: number
- `metrics.pace.current_pace_seconds_per_meter`: number

### Example

```json
{
  "event_id": "3d6fcb36-f66f-4c43-b0a4-3af54c730f57",
  "session_id": "8e5c1b4a-73d1-4e6e-9051-ceb6a45f611a",
  "status": "running",
  "timestamp": "2026-08-01T09:34:00Z",
  "duration_seconds": 1245.8,
  "activity_type": "running",
  "step_count": 1567,
  "speed_mps": 5.2,
  "distance_m": 4210.5,
  "metadata": {
    "device_name": "simulator",
    "sw_version": "sim_v1.0.0",
    "source": "frontend-simulator"
  },
  "metrics": {
    "heart_rate": {
      "value": 154.0,
      "unit": "count/min",
      "zone": 3
    },
    "pace": {
      "current_pace_seconds_per_meter": 0.31,
      "current_mile_pace": "08:18",
      "unit": "min/mi"
    }
  },
  "location": {
    "latitude": 52.520008,
    "longitude": 13.404954,
    "altitude": 34.2,
    "speed_mps": 3.2
  }
}
```

## 2) RabbitMQ topology

- Exchange: `pacer.events` (`topic`)
- DLX exchange: `pacer.events.dlx` (`topic`)

### Routing keys and queues

- `telemetry.raw` -> `pacer.telemetry.raw.q`
- `telemetry.canonical` -> `pacer.telemetry.canonical.q`
- `telemetry.aggregated` -> `pacer.telemetry.aggregated.q`
- `cue.text` -> `pacer.cue.text.q`
- Dead letters -> `pacer.deadletter.q`

## 3) Aggregated event contract

Produced by aggregator and consumed by n8n handler.

```json
{
  "event_id": "f0bf8fbf-1f58-4856-b74a-5501f5ec74ce",
  "session_id": "8e5c1b4a-73d1-4e6e-9051-ceb6a45f611a",
  "timestamp": "2026-08-01T09:34:30Z",
  "window_seconds": 30,
  "event_type": "pace_drift",
  "summary": {
    "avg_pace_sec_per_km": 322,
    "avg_heart_rate": 168,
    "heart_rate_drift_bpm": 9,
    "distance_m": 150
  },
  "run_context": {
    "planned_phase": "tempo",
    "target_pace_sec_per_km": 300,
    "remaining_distance_m": 2800
  }
}
```

## 4) n8n pacer request and response

The backend invokes an existing n8n workflow endpoint. Exact URL is environment-driven.

### Request body

```json
{
  "event_id": "f0bf8fbf-1f58-4856-b74a-5501f5ec74ce",
  "session_id": "8e5c1b4a-73d1-4e6e-9051-ceb6a45f611a",
  "timestamp": "2026-08-01T09:34:30Z",
  "event_type": "pace_drift",
  "summary": {
    "avg_pace_sec_per_km": 322,
    "avg_heart_rate": 168
  },
  "run_context": {
    "planned_phase": "tempo",
    "target_pace_sec_per_km": 300
  },
  "athlete_context": {
    "fitness_level": "intermediate",
    "goal": "5k_sub_25"
  }
}
```

### Response body

```json
{
  "event_id": "f0bf8fbf-1f58-4856-b74a-5501f5ec74ce",
  "session_id": "8e5c1b4a-73d1-4e6e-9051-ceb6a45f611a",
  "cue": "Ease back slightly for one minute, then settle to tempo pace.",
  "priority": "medium",
  "ttl_seconds": 20
}
```

### Response rules

- `cue` is plain English and less than 140 characters.
- `priority` is one of `low | medium | high`.
- Empty or missing `cue` means no spoken instruction.

## 5) TTS request and output

TTS module consumes `cue.text` payload and emits audio payload for websocket delivery.

### Input

```json
{
  "event_id": "f0bf8fbf-1f58-4856-b74a-5501f5ec74ce",
  "session_id": "8e5c1b4a-73d1-4e6e-9051-ceb6a45f611a",
  "cue": "Ease back slightly for one minute, then settle to tempo pace.",
  "voice": "default",
  "format": "mp3"
}
```

### WebSocket outbound message

```json
{
  "type": "pacer_audio",
  "session_id": "8e5c1b4a-73d1-4e6e-9051-ceb6a45f611a",
  "event_id": "f0bf8fbf-1f58-4856-b74a-5501f5ec74ce",
  "audio_base64": "<base64-encoded-mp3>",
  "text": "Ease back slightly for one minute, then settle to tempo pace."
}
```

## 6) Reliability rules

- Idempotency key: `event_id` across all pipeline stages.
- Cooldown: backend suppresses cues for 20 seconds per `session_id`.
- Timeout: n8n request timeout 1200 ms.
- Fallback: if n8n fails/timeouts, emit deterministic fallback cue from backend rules.
- DLQ: malformed or repeatedly failing events go to `pacer.deadletter.q`.

## 7) Environment variables (planned)

- `RABBITMQ_HOST`
- `RABBITMQ_PORT`
- `RABBITMQ_USER`
- `RABBITMQ_PASSWORD`
- `RABBITMQ_VHOST`
- `N8N_PACER_WEBHOOK_URL`
- `N8N_PLANNER_WEBHOOK_URL`
- `ELEVENLABS_API_KEY`
- `ELEVENLABS_VOICE_ID`
- `PACER_CUE_COOLDOWN_SECONDS`
- `PACER_N8N_TIMEOUT_MS`
- `PACER_MAX_HR_GUARDRAIL`

