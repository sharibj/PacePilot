# Pace Pilot

## Context

For a runner, three capabilities matter most:

- Planner: plan runs based on fitness level and goals.
- Logger: log runs and track progress over time.
- Pacer: guide effort and speed during a live run.

Today these are fragmented. Planning tools and logging tools exist, but live pacing is often informal (a buddy, a watch, or intuition).

## Gaps

- Planner gaps: most plans do not adapt enough to mood, schedule, accumulated fatigue, or multi-step goals.
- Pacer gaps: live guidance usually lacks real-time personalization, risk awareness, and flexibility.
- Combined gap: there is no single loop that connects plan -> live run signals -> adaptive cues -> logged outcomes.

## Solution

Pace Pilot combines planner and pacer, while integrating with logging platforms.

- The planner uses fitness, goals, schedule, and state.
- The pacer adapts in real time from telemetry and workout targets.
- The logger integration closes the loop so future plans and pacing improve.

## Technical Approach

### Planner

- Planner agent already exists as an n8n workflow.
- Backend invokes planner workflow via HTTP contract.
- Planner output is stored in backend and used by pacer decision context.

### Pacer
#### Overview
We will create a frontend that will be our simulator.
The simulator will simulate stuff like when someone has stopped when someone is running when someone is walking, what is their pace, what is their heart rate etc.
the simulator in combination with the WebSocket server will continuously emit and events that will be sent to a queue.
This queue will be consumed by a unifier in the backend which will basically transform the simulator message to another JSON.
The idea is that Unifier will be able to support multiple devices. For now it's one simulator but in the future it can be a watch or a mobile app.
The unifier will simply transform the message and publish it to another queue.
This queue will then become consumed by an aggregator.
The aggregator will be responsible for
    - at the worst case combining data. For example instead of per second it will give data for 30 seconds combined or
  - At the best case it will detect important indicators like pace change, heartbeat drift etc and only emit important events

The aggregator will also emit the events to another queue.
A service will read from this queue and send the events to an AI agent.
This agent will have an overview of our training plan and the target run and based on that it will decide if and what message to pass on to the runner.
The message will be a short English sentence.
This message will be passed through a TTS layer (elevenlabs) and the audio will be streamed back to the user, in our case the simulator, to play.

#### Architecture decisions (locked)

- Frontend hosts the simulator client.
- Backend hosts the WebSocket server.
- RabbitMQ is the event broker.
- Backend remains one Spring Boot app with modular packages.
- n8n handles only agentic decisions (`aggregated-event -> n8n -> text cue`).
- ElevenLabs is the first TTS provider.
- Simulator and unifier use the same canonical telemetry schema.
- Existing n8n deployment is reused (no compose changes for n8n in this phase).

#### Runtime flow

`simulator (frontend) -> backend websocket ingress -> rabbitmq(raw) -> unifier -> rabbitmq(canonical) -> aggregator -> rabbitmq(aggregated) -> n8n-agent-handler -> text cue -> tts -> audio -> backend websocket -> simulator`

#### Backend module boundaries

- `com.pacepilot.app.unifier`: validate, enrich, and republish canonical events.
- `com.pacepilot.app.aggregator`: windowing and salient-event detection.
- `com.pacepilot.app.n8n`: invoke n8n workflows and parse cue response.
- `com.pacepilot.app.tts`: synthesize cue audio and return streamable payload.
- `com.pacepilot.app.realtime` (or similar): websocket ingest and websocket cue delivery.

#### Queue topology (RabbitMQ)

- Exchange: `pacer.events` (topic)
- Routing keys:
  - `telemetry.raw`
  - `telemetry.canonical`
  - `telemetry.aggregated`
  - `cue.text`
- Queues:
  - `pacer.telemetry.raw.q`
  - `pacer.telemetry.canonical.q`
  - `pacer.telemetry.aggregated.q`
  - `pacer.cue.text.q`
  - `pacer.deadletter.q`

#### Canonical telemetry schema (simulator and unifier)

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
    "sw_version": "sim_v1.0.0"
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

#### Aggregated event contract (to n8n handler)

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
    "target_pace_sec_per_km": 300
  }
}
```

#### n8n pacer response contract

```json
{
  "event_id": "f0bf8fbf-1f58-4856-b74a-5501f5ec74ce",
  "session_id": "8e5c1b4a-73d1-4e6e-9051-ceb6a45f611a",
  "cue": "Ease back slightly for one minute, then settle to tempo pace.",
  "priority": "medium",
  "ttl_seconds": 20
}
```

#### Initial performance and safety targets

- P95 latency (`aggregated` event to cue text) <= 2 seconds in local/dev.
- Cue cooldown: minimum 20 seconds between spoken cues for one session.
- n8n timeout: 1200 ms; fallback to deterministic local cue on timeout.
- Guardrails: avoid high-risk advice when HR exceeds configured threshold.

## Delivery milestones

1. Add RabbitMQ and messaging config; keep n8n untouched.
2. Add backend module skeletons: `unifier`, `aggregator`, `n8n`, `tts`, `realtime`.
3. Implement websocket ingest from simulator and raw event publishing.
4. Implement unifier and aggregator pipelines.
5. Implement n8n handler and cooldown/idempotency logic.
6. Implement ElevenLabs TTS adapter and audio push over websocket.
7. Implement frontend simulator stream + audio playback.

Detailed implementation handoff is in `docs/implementation-handoff.md` and `docs/pacer-contracts.md`.

