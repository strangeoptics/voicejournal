# Voice Journal Frontend

A lightweight web interface to browse and edit journal entries served by the Android app's embedded Ktor REST API.

## Prerequisites

- Node.js and npm must be installed.
- The Android backend REST API must be running on port `8080` (e.g. `http://localhost:8080` via `adb forward tcp:8080 tcp:8080` or the phone's LAN IP).

## Installation

```bash
cd web
npm install
```

## Running the application

```bash
npm start
```

Open `http://localhost:8081` in your browser.

## Keeping the REST API in sync

The shared OpenAPI contract lives in [`../api/openapi.yaml`](../api/openapi.yaml). Whenever the Android REST API changes:

```bash
npm run sync-api   # Regenerates api-types.d.ts from ../api/openapi.yaml
npm run typecheck  # Type-checks api-client.js and script.js via JSDoc + tsc --noEmit
npm run check      # Runs both sync-api and typecheck
```
