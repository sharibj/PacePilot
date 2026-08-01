# pace-pilot
Your AI co-pilot for every run.


A full-stack template you can clone and build on: Spring Boot 3 (Java 21) backend, React 19 + Vite 6 frontend, Postgres, and a LiteLLM proxy, wired together in Docker Compose with CI, linters, and agent docs.

## Stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3.4, Java 21, Gradle 8.10, Lombok |
| Frontend | React 19, Vite 6, TypeScript (strict) |
| Database | PostgreSQL 16 + Flyway migrations |
| LLM | LiteLLM proxy (OpenAI-compatible), reached via Spring AI |
| Infra | Docker Compose, multi-stage Dockerfiles |

## Quickstart

```sh
cp .env.example .env          # optionally add OPENAI_API_KEY / ANTHROPIC_API_KEY
docker compose up --build
```

Then open http://localhost:5173. The Items panel talks to the CRUD API; the Chat panel talks to the model through LiteLLM. Without a provider key the chat call returns a 502, but everything else works offline.

## Ports

| Service | Port |
|---|---|
| Frontend (Vite dev) | 5173 |
| Backend | 8080 |
| Postgres | 5432 |
| LiteLLM | 4000 |
| Grafana (observability) | 3000 |

## The LLM proxy contract

Every project in this scaffold family talks to the same OpenAI-compatible endpoint instead of a specific provider. The backend only needs three variables:

- `LLM_API_BASE` is where LiteLLM lives (`http://litellm:4000` in compose, `http://localhost:4000` bare)
- `LLM_API_KEY` is the proxy master key
- `LLM_MODEL` is a `model_name` from `litellm/litellm_config.yaml`

Provider keys (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `OLLAMA_API_BASE`) are read by LiteLLM, not the app. Swap models by editing the config, not the code.

## Observability

The backend emits metrics, traces, and logs over OTLP to a single `grafana/otel-lgtm` container (OTel Collector + Prometheus + Tempo + Loki + Grafana, with datasources pre-wired). Open Grafana at http://localhost:3000 (anonymous access is on).

What you get automatically:
- **Metrics**: JVM, HTTP server latency and throughput, and the HikariCP pool, via Micrometer.
- **Traces**: every HTTP request is spanned, and the outbound LiteLLM call is a child span, so you can see a `/api/chat` request flow end to end.
- **Logs**: app logs ship to Loki with trace ids attached, so you can jump from a trace to its logs.

Custom metrics ship out of the box as an example (see `chat/ChatService.java`): a `chat.requests` counter, a `chat.latency` timer, and LLM token counters `llm.tokens.prompt` / `llm.tokens.completion` / `llm.tokens.total`, all tagged by `model`. In Grafana, query `llm_tokens_total` to see token spend per model.

The OTLP endpoint is env-driven (`OTEL_METRICS_URL`, `OTEL_TRACES_URL`, `OTEL_EXPORTER_OTLP_ENDPOINT`). Point them at any OTLP collector to swap LGTM for a real backend. The backend also exposes `/actuator/prometheus` if you prefer a pull-based scrape.

Note: `grafana/otel-lgtm` is a dev/demo image. Use a real observability backend in production.

## Make it yours

```sh
./rename.sh --name my-app --package com.acme.myapp
```

That renames the project and Java package and moves the package directory. Then edit `.env` for the database name and any ports you need to change.

## Running without Docker

```sh
# backend (needs a Postgres on localhost:5432 and a LiteLLM on :4000)
cd backend && ./gradlew bootRun

# frontend
cd frontend && npm install && npm run dev
```

## CI

`.github/workflows/ci.yml` runs three jobs on push/PR: backend (`./gradlew spotlessCheck build`), frontend (`npm ci && lint && build`), and a `docker compose build`. The backend tests use Testcontainers, so CI and local `./gradlew build` both need a Docker daemon.
