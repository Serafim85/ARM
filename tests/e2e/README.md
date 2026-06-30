# E2E tests — WISLA АРМ

**Отдельный Playwright-проект** для UI и сквозных сценариев (не `frontend/*.spec.ts` unit).

## Stack

- Playwright + TypeScript
- Chromium (default)

## Prerequisites

```bash
# 1. Start full stack (when infra/ exists)
docker compose -f infra/docker-compose.yml up -d

# 2. Install deps + browser
cd tests/e2e
npm ci
npm run install:browsers
```

## Run

```bash
cd tests/e2e

# all specs (most MVP specs are skipped until UI exists)
npm test

# smoke only
npm run test:smoke

# with full stack (DB + backend + frontend)
E2E_STACK_UP=1 E2E_BASE_URL=http://localhost:3000 BACKEND_BASE_URL=http://localhost:8081 npm run test:smoke

# headed debug
npx playwright test --ui
```

## Environment

| Variable | Default | Purpose |
|---|---|---|
| `E2E_BASE_URL` | `http://localhost:3000` | Angular dev (`ng serve`) |
| `BACKEND_BASE_URL` | `http://localhost:8081` | API setup / seed (profile `wisla-arm`) |
| `E2E_USER` / `E2E_PASSWORD` | `admin@example.com` / `password` | Login specs |
| `E2E_STACK_UP` | unset | Set `1` to force-run stack-dependent smoke |
| `AGENT_INGEST_API_KEY` | `dev-arm-ingest-key` | Ingest precondition in E2E |

## MVP specs (`tests/smoke/`)

| File | Scenario |
|---|---|
| `login.spec.ts` | Local login |
| `workstations-list.spec.ts` | АРМ после ingest |
| `arm-card.spec.ts` | Графики CPU/RAM/диск |
| `smoke.spec.ts` | Frontend HTTP status |

Fixtures: `tests/fixtures/ingest-batch-*.json`

## When to add specs here

- User-visible flow (login, navigation, card)
- Cross-layer: API seed → UI assert

Pure component logic → `frontend/**/*.spec.ts`.

See `docs/TEST-COVERAGE.md`.
