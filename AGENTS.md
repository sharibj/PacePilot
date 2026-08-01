# AGENTS.md

Guidance for agents working in a clone of this scaffold. Read this first. It tells you how to run the app, the contracts between services, and where to add code.

## What this is

A full-stack template: Spring Boot 3 backend + React/Vite frontend + Postgres + a LiteLLM proxy, orchestrated by Docker Compose. It ships one example CRUD resource (`Item`) and one chat endpoint so the wiring is proven end to end. Build features on top of these patterns.

## Layout

```
backend/    Spring Boot 3, Java 21, Gradle, package com.example.app
frontend/   React 19 + Vite 6 + TypeScript
litellm/    LiteLLM proxy config (model -> provider map)
docker-compose.yml   postgres + litellm + backend + frontend
```

## Run it

```sh
cp .env.example .env
docker compose up --build      # http://localhost:5173
```

Bare metal: `cd backend && ./gradlew bootRun` (needs Postgres on :5432 and LiteLLM on :4000); `cd frontend && npm install && npm run dev`.

## Contracts

**Config is env-driven with localhost defaults.** Never hardcode a host or port in code. Read it from an env var with a `localhost` fallback (see `backend/src/main/resources/application.yml` and `frontend/vite.config.ts`). In compose, services reach each other by name (`postgres`, `litellm`, `backend`).

**LLM access goes through LiteLLM, never a provider SDK.** The backend uses Spring AI's OpenAI client pointed at `LLM_API_BASE`. To change models, edit `litellm/litellm_config.yaml` and set `LLM_MODEL` to a `model_name` there. Do not add provider SDKs to the backend.

## Where to add code

- **A new REST resource**: copy the `item/` package (`Entity`, `Repository`, `Service`, `Controller`, `dto/`) and add a Flyway migration `V2__...sql` under `backend/src/main/resources/db/migration`. `ddl-auto` is `validate`, so schema changes must go through Flyway.
- **A new endpoint's error case**: throw `NotFoundException` (→404) or add a handler in `common/GlobalExceptionHandler.java`. Responses use the `ApiError` shape so the frontend reads `err.message`.
- **A frontend API call**: add a typed module under `frontend/src/shared/api/` using the `api.get/post/...` wrapper in `client.ts`. Don't call `fetch` directly.
- **A new page/component**: add to `frontend/src/` and render from `App.tsx`.
- **Your own metric**: inject `MeterRegistry` and copy the pattern in `chat/ChatService.java`. A counter is `meters.counter("my.metric", "tag", value).increment()`; a timer wraps a call with `Timer.start(meters)` / `sample.stop(meters.timer(...))`. Traces and logs are automatic (every HTTP request is spanned, logs ship to Loki with trace ids), so you rarely need to add those by hand. The OTLP endpoint is env-driven (`OTEL_*` vars), so the same code works against LGTM or any other collector. View everything in Grafana at http://localhost:3000.

## Before you finish

- Backend: `cd backend && ./gradlew spotlessApply build` (Spotless formats; tests use Testcontainers → Docker daemon required).
- Frontend: `cd frontend && npm run lint && npm run build`.
- If you touched compose or Dockerfiles, verify `docker compose up --build` and confirm the UI in a browser. Lint and tests passing is not proof it renders.

## This is a template

Placeholders to replace when adopting: project name `pace-pilot`, Java package `com.example.app`. Run `./rename.sh --name <name> --package <pkg>`, then edit `.env`.
