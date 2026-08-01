# AI Agent Step Prompts

Use these prompts one by one with a coding agent. Keep each run focused on one step.

## Prompt 1: RabbitMQ infrastructure wiring

Implement Step 1 from `docs/implementation-handoff.md`.

Constraints:

- Modify `docker-compose.yml` to add RabbitMQ only.
- Do not add or modify n8n service definitions.
- Add only environment variables required by backend for RabbitMQ, n8n endpoint, and ElevenLabs.
- Keep all host/port values environment-driven.

Deliverables:

- Updated `docker-compose.yml`
- Updated `.env.example`
- Short note of new env vars and defaults.

Validation:

- Verify compose config is valid.

## Prompt 2: Backend module and messaging skeleton

Implement Step 2 from `docs/implementation-handoff.md`.

Constraints:

- Create package skeletons under `backend/src/main/java/com/pacepilot/app/`:
  - `unifier`, `aggregator`, `n8n`, `tts`, `realtime`, `messaging`
- Add DTOs that match `docs/pacer-contracts.md`.
- Configure RabbitMQ exchange, queues, and bindings exactly as documented.

Deliverables:

- New Java packages and DTOs
- Messaging configuration classes
- Any dependency updates in `backend/build.gradle`

Validation:

- Run backend build/tests.

## Prompt 3: WebSocket ingest and egress base

Implement Step 3 from `docs/implementation-handoff.md`.

Constraints:

- Add backend WebSocket endpoint for telemetry ingest.
- Publish inbound telemetry to `telemetry.raw`.
- Add outbound `pacer_audio` websocket message support.

Deliverables:

- WebSocket config/handler code
- Minimal in-memory session map keyed by `session_id`

Validation:

- Add at least one integration test or reproducible manual check.

## Prompt 4: Unifier implementation

Implement Step 4 from `docs/implementation-handoff.md`.

Constraints:

- Consume raw events.
- Validate required fields.
- Keep payload schema unchanged.
- Publish canonical events.
- Dead-letter malformed events.

Deliverables:

- Unifier consumer/service classes
- Validation logic
- Unit tests for valid and invalid payloads

Validation:

- Backend test suite passes.

## Prompt 5: Aggregator implementation

Implement Step 5 from `docs/implementation-handoff.md`.

Constraints:

- Session-scoped rolling window (30s default).
- Detect and emit salient events only.
- Publish aggregated events conforming to `docs/pacer-contracts.md`.

Deliverables:

- Aggregator consumer/service classes
- Salient event rules
- Unit tests for drift and suppression logic

Validation:

- Backend test suite passes.

## Prompt 6: n8n handler with fallback

Implement Step 6 from `docs/implementation-handoff.md`.

Constraints:

- Invoke `N8N_PACER_WEBHOOK_URL` with timeout and retry.
- Idempotency by `event_id`.
- Cooldown by `session_id`.
- Fallback cue on timeout/failure.

Deliverables:

- n8n client/service classes
- Configuration properties
- Tests for timeout/fallback/cooldown behavior

Validation:

- Backend test suite passes.

## Prompt 7: ElevenLabs TTS module

Implement Step 7 from `docs/implementation-handoff.md`.

Constraints:

- Consume text cues and call ElevenLabs API.
- Publish/send `pacer_audio` websocket message.
- Fall back to text-only cue if TTS fails.

Deliverables:

- TTS service/client classes
- Error handling and fallback path
- Tests for payload construction and fallback

Validation:

- Backend test suite passes.

## Prompt 8: Frontend simulator client

Implement Step 8 from `docs/implementation-handoff.md`.

Constraints:

- Build simulator controls in frontend.
- Stream canonical telemetry over WebSocket.
- Receive and play `pacer_audio` payloads.
- Show latest cue and connection state.

Deliverables:

- Simulator UI components
- WebSocket client code
- Local audio playback integration

Validation:

- Run frontend lint/build.

## Prompt 9: Metrics and end-to-end checks

Implement Step 9 from `docs/implementation-handoff.md`.

Constraints:

- Add counters/timers for each stage.
- Add integration coverage for end-to-end sample.

Deliverables:

- Metrics instrumentation
- Tests
- Final verification notes

Validation:

- Backend build/tests pass.
- Frontend lint/build pass.

