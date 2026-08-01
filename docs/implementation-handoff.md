# AI Implementation Handoff

This is the execution guide for implementing the pacer pipeline in this repository.

## Objective

Build the first end-to-end pacer loop where:

1. Frontend simulator streams telemetry to backend over WebSocket.
2. Backend pipeline processes events (`unifier -> aggregator -> n8n handler -> tts`).
3. Spoken cue audio is sent back to the simulator over WebSocket.

## Locked decisions

- Backend remains one Spring Boot app with modular packages.
- RabbitMQ is used for pipeline messaging.
- Backend hosts WebSocket server.
- Frontend hosts simulator client.
- n8n is already running; do not add or modify n8n service in compose.
- n8n is used for planner and pacer agentic decisions only.
- ElevenLabs is the TTS provider for this phase.
- Simulator and unifier share one canonical telemetry schema.

## Artifacts to use as source of truth

- Product and architecture: `docs/plan.md`
- Message contracts: `docs/pacer-contracts.md`
- Existing app scaffold: `AGENTS.md`

## Repository touchpoints

### Infrastructure

- `docker-compose.yml`
- `.env.example`

### Backend

- `backend/build.gradle`
- `backend/src/main/resources/application.yml`
- New package roots under `backend/src/main/java/com/pacepilot/app/`:
  - `unifier/`
  - `aggregator/`
  - `n8n/`
  - `tts/`
  - `realtime/`
  - `messaging/` (RabbitMQ config)

### Frontend

- `frontend/src/App.tsx`
- `frontend/src/shared/api/` (if any REST helpers are needed)
- New simulator modules under `frontend/src/simulator/`

## Step-by-step execution plan

## Step 1: Compose and env wiring

### Tasks

- Add RabbitMQ service in `docker-compose.yml`.
- Add RabbitMQ env vars to backend service.
- Add n8n and TTS env vars to backend service.
- Do not add n8n service to compose in this step.

### Acceptance criteria

- `docker compose up` starts RabbitMQ and backend can connect.
- Existing services still start (`postgres`, `litellm`, `backend`, `frontend`).

## Step 2: Backend package scaffolding

### Tasks

- Create package skeletons:
  - `unifier`
  - `aggregator`
  - `n8n`
  - `tts`
  - `realtime`
  - `messaging`
- Add DTO classes from `docs/pacer-contracts.md`.
- Add Spring AMQP config for exchange/queues/routing keys.

### Acceptance criteria

- App boots with queue declarations created at startup.
- DTO serialization/deserialization works for sample payloads.

## Step 3: WebSocket ingress/egress

### Tasks

- Add backend WebSocket endpoint for simulator telemetry ingest.
- Map websocket connection to `session_id`.
- Publish inbound telemetry to `telemetry.raw`.
- Add outbound websocket publisher for `pacer_audio` messages.

### Acceptance criteria

- Simulated telemetry frame reaches `pacer.telemetry.raw.q`.
- Backend can send a test audio payload to connected simulator client.

## Step 4: Unifier module

### Tasks

- Consume from `pacer.telemetry.raw.q`.
- Validate required fields and normalize safe defaults.
- Keep canonical shape unchanged (same schema as simulator).
- Enrich metadata if needed (for example, source tags).
- Publish to `telemetry.canonical`.

### Acceptance criteria

- Valid events appear on `pacer.telemetry.canonical.q`.
- Invalid events go to dead-letter path with reason logging.

## Step 5: Aggregator module

### Tasks

- Consume canonical telemetry.
- Maintain rolling window state per session (30s initial window).
- Emit only salient events:
  - pace drift
  - heart-rate drift
  - abrupt state change (run -> walk, etc.)
- Publish to `telemetry.aggregated`.

### Acceptance criteria

- Aggregated events conform to `docs/pacer-contracts.md`.
- No event flood under stable pace (suppression logic works).

## Step 6: n8n agent handler module

### Tasks

- Consume aggregated events.
- Build pacer request payload and invoke `N8N_PACER_WEBHOOK_URL`.
- Apply timeout and retry policy.
- Enforce idempotency by `event_id`.
- Enforce session cooldown before forwarding to `cue.text`.

### Acceptance criteria

- n8n response cue is published to `pacer.cue.text.q`.
- Timeout/failure produces fallback deterministic cue.

## Step 7: TTS module

### Tasks

- Consume text cues from `pacer.cue.text.q`.
- Invoke ElevenLabs API.
- Build websocket outbound payload (`pacer_audio`) with base64 audio.
- Send to correct session channel.

### Acceptance criteria

- Simulator receives and plays cue audio.
- If TTS fails, simulator still receives text-only cue fallback.

## Step 8: Frontend simulator

### Tasks

- Build a telemetry simulator UI and run-state controls.
- Open websocket connection to backend endpoint.
- Stream canonical telemetry at configured interval.
- Receive `pacer_audio` events and play audio.
- Display current status and latest cue text.

### Acceptance criteria

- User can start/stop simulation and hear cue playback.
- UI shows connection health and latest pipeline state.

## Step 9: Observability and tests

### Tasks

- Add counters/timers per stage:
  - input telemetry count
  - aggregated event count
  - n8n latency
  - TTS latency
  - cue delivery count
- Add unit tests for unifier and aggregator logic.
- Add integration test for end-to-end sample event path.

### Acceptance criteria

- Metrics visible in existing observability setup.
- Test suite passes for new modules.

## Suggested implementation order for AI agent runs

Execute in multiple focused runs to reduce context loss:

1. Compose + backend messaging skeleton.
2. WebSocket ingest + simulator stub.
3. Unifier + aggregator logic with tests.
4. n8n handler + fallback behavior.
5. TTS + websocket return path.
6. Frontend simulator polish + end-to-end validation.

## Definition of done (phase 1)

- End-to-end loop works in local dev with RabbitMQ and existing n8n.
- A simulator session receives at least one generated cue as playable audio.
- All queue/message contracts match `docs/pacer-contracts.md`.
- Errors degrade gracefully (timeouts, malformed messages, unavailable n8n/TTS).
- New code passes backend build/tests and frontend lint/build.

## Notes for implementation agent

- Keep all host/port values environment-driven.
- Do not hardcode n8n URL or credentials.
- Keep module boundaries strict; avoid leaking queue concerns into UI.
- Avoid changing existing CRUD/chat scaffold behavior while adding pacer modules.

