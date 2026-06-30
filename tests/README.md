# tests/

Тесты разделены на **три уровня** — см. `docs/testing.md` и `docs/TEST-COVERAGE.md`.

| Проект | Путь | Назначение |
|---|---|---|
| Unit | `agent/`, `backend/`, `frontend/` | Быстрые тесты рядом с кодом |
| **Integration** | `tests/integration/` | Отдельный Maven: API + DB |
| **E2E** | `tests/e2e/` | Отдельный Playwright: UI + stack |
| Fixtures | `tests/fixtures/` | JSON batches, shared data |

## Быстрый старт

```bash
./tests/run-all.sh --unit          # когда код появится
./tests/run-all.sh --integration   # mvn в tests/integration/
./tests/run-all.sh --e2e           # npm в tests/e2e/ (нужен stack)
./tests/run-all.sh --all
```

## Phase gates

- **MVP done:** unit + integration green; E2E smoke green (`docs/PHASES.md`)
- **v1 done:** + policy, LDAP, reports specs
