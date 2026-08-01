# Pace Pilot

Your AI co-pilot for every run. Pace Pilot plans a run around your real life, then coaches you through it out loud while you run.

https://github.com/user-attachments/assets/6f0c6ae8-2829-462a-96c1-de57580e05e4

Most running tools handle one piece each: a planner builds a schedule, a logger tracks what you did, and live pacing is left to a watch or a guess. Pace Pilot joins them into one loop. A planner agent reads your fitness, calendar, weather, and how you say you're feeling, and produces a plan that fits the week you actually have. During the run, a pacer pipeline turns live telemetry into short spoken cues, so the guidance reacts to what your pace and heart rate mean right now instead of just reporting numbers.

There's a slide deck and a walkthrough video of the concept in [`docs/deck`](docs/deck).

## How the live pacer works

Telemetry flows through a chain of small stages, each publishing to the next over RabbitMQ:

```
simulator -> websocket ingress -> unifier -> aggregator -> n8n coach agent -> TTS -> audio back to the runner
```

- **Unifier** normalizes raw device telemetry into one canonical schema, so a phone simulator today and a watch later look the same downstream.
- **Aggregator** watches a rolling window and only emits the moments worth reacting to, like heart-rate drift, rather than every reading.
- **n8n coach agent** holds the plan and today's target and decides whether to say anything, and what. If it times out, the backend falls back to a deterministic cue.
- **TTS** turns the cue into speech (ElevenLabs) and streams the audio back over the websocket for the runner to hear.

The backend is one Spring Boot app with a package per stage: `unifier`, `aggregator`, `n8n`, `tts`, `realtime`, and `messaging`. See [`docs/plan.md`](docs/plan.md) and [`docs/pacer-contracts.md`](docs/pacer-contracts.md) for the schemas and reliability rules.

## Stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3.4, Java 21, Gradle 8.10, Lombok |
| Frontend | React 19, Vite 6, TypeScript (strict) |
| Database | PostgreSQL 16 + Flyway migrations |
| Messaging | RabbitMQ (topic exchange, per-stage queues) |
| Agents | n8n workflows for planning and pacing decisions |
| Speech | ElevenLabs text-to-speech |
| LLM | LiteLLM proxy (OpenAI-compatible), reached via Spring AI |
| Infra | Docker Compose, multi-stage Dockerfiles |

## Quickstart

```sh
cp .env.example .env    # add ELEVENLABS_API_KEY and a provider key if you want live cues and speech
docker compose up --build
```

Then open http://localhost:5173. The app runs without the API keys; you just won't get spoken cues from a real model until they're set.

## Ports

| Service | Port |
|---|---|
| Frontend (Vite dev) | 5173 |
| Backend | 8080 |
| Postgres | 5432 |
| RabbitMQ (management UI) | 15672 |
| LiteLLM | 4000 |
| n8n | 5678 |
| Grafana (observability) | 3000 |

## Configuration

Config is env-driven with localhost defaults, so nothing hardcodes a host or port. The pieces you'll usually set:

- **LLM through LiteLLM.** The backend talks to one OpenAI-compatible endpoint, never a provider SDK. `LLM_API_BASE`, `LLM_API_KEY`, and `LLM_MODEL` point it at LiteLLM; provider keys (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`) are read by LiteLLM itself. Swap models by editing `litellm/litellm_config.yaml`, not the code.
- **Agents.** `N8N_PLANNER_WEBHOOK_URL` and `N8N_PACER_WEBHOOK_URL` point at the n8n workflows.
- **Speech and pacing.** `ELEVENLABS_API_KEY` / `ELEVENLABS_VOICE_ID` for TTS, plus `PACER_CUE_COOLDOWN_SECONDS`, `PACER_N8N_TIMEOUT_MS`, and `PACER_MAX_HR_GUARDRAIL` to tune how often and how safely the coach speaks.

Full list lives in `.env.example` and `docs/pacer-contracts.md`.

## Observability

The backend ships metrics, traces, and logs over OTLP to a single `grafana/otel-lgtm` container (OTel Collector, Prometheus, Tempo, Loki, and Grafana with datasources pre-wired). Open Grafana at http://localhost:3000 (anonymous access is on).

You get JVM, HTTP, and connection-pool metrics via Micrometer; a span for every HTTP request with the outbound LiteLLM call as a child span; and app logs in Loki with trace ids attached. Per-stage pipeline metrics are instrumented so you can watch telemetry move through the unifier, aggregator, and cue path. The OTLP endpoint is env-driven (`OTEL_*` vars), so you can point it at any collector; `/actuator/prometheus` is there if you prefer a pull-based scrape.

`grafana/otel-lgtm` is a dev image. Use a real observability backend in production.

## Running without Docker

```sh
# backend (needs Postgres on :5432, LiteLLM on :4000, and RabbitMQ on :5672)
cd backend && ./gradlew bootRun

# frontend
cd frontend && npm install && npm run dev
```

## CI

`.github/workflows/ci.yml` runs three jobs on push and PR: backend (`./gradlew spotlessCheck build`), frontend (`npm ci && lint && build`), and a `docker compose build`. The backend tests use Testcontainers, so CI and local `./gradlew build` both need a Docker daemon.
