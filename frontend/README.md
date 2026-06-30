# Frontend — fork NetworckScaner (WISLA ARM)

Angular 21 · PrimeNG

## Run

```bash
npm ci
npm start
```

API: `http://localhost:8081` (`src/environments/environment.ts`).

## WISLA ARM mode

`environment.hideNetworkScannerFeatures: true` — скрыты scan/topology; стартовая страница `/monitoring` («АРМ»).

## Tests

```bash
npm test -- --watch=false
```

E2E: `../tests/e2e/` (Playwright).
