# Frontend Timeline

SPA доски плана с вкладками для администратора:

- **Планирование** — доска, фильтр статусов, перенос задачи в модалке
- **Задачи и эпики** — список, создание/правка в модалках
- **Спринты** — список, создание/правка/удаление в модалках

Вход через Keycloak (password grant для локальной демо).

## Перед запуском

Сначала поднимите бэкенд (см. корневой [`README.md`](../README.md), раздел
«Запуск» → шаг 1):

```bash
cd deploy
docker compose up -d --build
```

Gateway должен отвечать на http://localhost:8080, Keycloak — на
http://localhost:8090.

## Локально (Vite)

```bash
npm install
npm run dev
```

Откройте http://localhost:5173 — `/api` проксируется на gateway `:8080`,
Keycloak остаётся на `:8090`.

Демо-логины: `anna.admin` / `admin123` (ADMIN), `petr.dev` / `member123` (MEMBER).

## В Docker

Сервис `frontend` в `deploy/docker-compose.yml` поднимается вместе с бэкендом
на порту 80 и проксирует `/api/` на `api-gateway`. UI:
http://localhost/
