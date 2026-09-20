# Frontend Timeline

SPA доски плана: вход через Keycloak (password grant для локальной демо), таблица
эпик × спринт, фильтр по статусу, preview/apply переноса задачи, обновление по SSE.

## Локально

Нужен запущенный backend (`deploy/docker compose up`).

```bash
npm install
npm run dev
```

Открыть http://localhost:5173 — API проксируется на gateway `:8080`, Keycloak
остаётся на `:8090`.

## В Docker

Сервис `frontend` в `deploy/docker-compose.yml` слушает порт 80 и проксирует
`/api/` на `api-gateway`.
