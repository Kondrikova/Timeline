# Frontend Timeline

SPA доски плана с вкладками для администратора:

- **Планирование** — доска, фильтр статусов, перенос задачи в модалке
- **Задачи и эпики** — список, создание/правка в модалках
- **Спринты** — список, создание/правка/удаление в модалках

Вход через Keycloak (password grant для локальной демо).

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
