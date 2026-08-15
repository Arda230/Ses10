# SES10

Production-oriented TypeScript service foundation. This repository currently contains only platform infrastructure and module boundaries; product features deliberately belong in future domain modules.

## Requirements

- Node.js 18.19 or newer
- npm 9.2 or newer

## Quick start

```bash
npm install
cp .env.example .env
npm run dev
```

`GET /health` returns process liveness and `GET /ready` returns service readiness.

## Commands

```bash
npm run dev
npm run build
npm run start
npm run typecheck
npm run format:check
```

See [architecture notes](docs/architecture.md) for the module rules.
