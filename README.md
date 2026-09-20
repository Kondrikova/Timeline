## Сервис долгосрочного планирования на команду (Timeline)

Инструмент планирования работ команды на горизонт нескольких месяцев: задачи с
оценками по ролям в SP, спринты, отпуска, ёмкость ролей с учётом отпусков, связи
между задачами и каскадный пересчёт плана.

## Документация

- [Архитектура решения](docs/architecture.md) — декомпозиция на микросервисы,
  диаграммы C4, модель данных, межсервисное взаимодействие, консистентность,
  безопасность, наблюдаемость, план реализации и реестр архитектурных решений.
- [Нагрузочное тестирование](docs/load-testing.md) — SLO и шаблон результатов k6.

## Состав репозитория

```
libs/common-events    конверт доменного события, реестр топиков
libs/common-outbox    transactional outbox, идемпотентный потребитель
libs/common-web       ProblemDetail, корреляция запросов, ресурс-сервер
services/api-gateway  единая точка входа: JWT, маршруты, rate limit, CORS
services/team-service сотрудники, дисциплины с velocity, отпуска
services/schedule-service спринты, производственный календарь, сага удаления
services/backlog-service  эпики, задачи, оценки в SP, связи трёх типов
services/planning-service аллокации, ёмкость, каскад, конфликты
services/timeline-bff     проекция доски (MongoDB) и SSE
frontend              SPA доски (nginx)
k6                    нагрузочные сценарии
postman               коллекция сценариев защиты
deploy                docker-compose, observability, Keycloak, Postgres
```

## Запуск

Нужны Docker и Docker Compose. JDK локально не обязателен: бэкенд собирается
внутри образов.

### 1. Бэкенд

Из каталога `deploy` поднимите инфраструктуру и сервисы (Postgres, Kafka,
Keycloak, gateway, team/schedule/backlog/planning/bff). Вместе с ними стартует
и контейнер `frontend` на порту 80 — его можно использовать сразу как UI
(см. шаг 3) или поднять SPA отдельно через Vite (шаг 2).

**Только приложение** (без Grafana/трейсов; OTEL-агент выключен):

```bash
cd deploy
docker compose up -d --build
```

**Приложение + наблюдаемость** (Prometheus, Grafana, Tempo, OTel-агент):

```bash
cd deploy
docker compose -f docker-compose.yml -f docker-compose.observability.yml up -d --build --force-recreate
```

Дождитесь healthy у Keycloak и gateway (`docker compose ps`). Gateway:
[http://localhost:8080](http://localhost:8080).

Если в логах сервисов сыпется `UnknownHostException: otel-collector`, вы на
базовом compose со старым агентом в контейнере — пересоберите:

```bash
cd deploy
docker compose up -d --build --force-recreate
```

### 2. Фронтенд (опционально, для разработки)

Контейнерный UI уже на [http://localhost/](http://localhost/) после шага 1.
Для hot-reload поднимите Vite **после** бэкенда:

```bash
cd frontend
npm install
npm run dev
```

Откройте [http://localhost:5173](http://localhost:5173) — `/api` проксируется
на gateway `:8080`. Подробнее: [`frontend/README.md`](frontend/README.md).

### 3. Открыть UI

| Вариант | Адрес |
|---|---|
| UI из Docker (после шага 1) | [http://localhost/](http://localhost/) |
| UI через Vite (после шага 2) | [http://localhost:5173](http://localhost:5173) |

Вход в приложении — через Keycloak (realm `timeline`):

| Логин | Пароль | Роль |
|---|---|---|
| `anna.admin` | `admin123` | ADMIN |
| `petr.dev` | `member123` | MEMBER |
| `olga.qa` | `member123` | MEMBER |

Keycloak admin-консоль: [http://localhost:8090](http://localhost:8090)
(`admin` / `admin`).

### 4. Открыть Grafana

Grafana есть **только** при запуске с observability-оверлеем (команда из шага 1
с двумя `-f`). Без оверлея контейнер Grafana не стартует.

1. Проверьте контейнер:

```bash
cd deploy
docker compose -f docker-compose.yml -f docker-compose.observability.yml ps grafana
```

2. Откройте [http://localhost:3000](http://localhost:3000)

3. Войдите: логин `admin`, пароль `admin`  
   (анонимный просмотр с ролью Viewer тоже включён — дашборды можно смотреть без входа).

4. Дашборд: **Dashboards** → папка **Timeline** → **Timeline Overview**.

Источники данных уже провижены: **Prometheus** (`:9090`) и **Tempo** (`:3200`).

Остановить только наблюдаемость, оставив приложение:

```bash
cd deploy
docker compose -f docker-compose.yml -f docker-compose.observability.yml stop grafana prometheus tempo otel-collector
```

| Компонент | Адрес |
|---|---|
| UI (Docker) | http://localhost/ |
| UI (Vite) | http://localhost:5173 |
| API Gateway | http://localhost:8080 |
| Keycloak | http://localhost:8090 (admin/admin) |
| Grafana | http://localhost:3000 (admin/admin) |
| Prometheus | http://localhost:9090 |

## Проверка сценария

Коллекция Postman: [`postman/Timeline.postman_collection.json`](postman/Timeline.postman_collection.json)
(папки по сценариям 0–5 + саги, OIDC password grant, тесты на шагах).

Нагрузка k6: [`k6/`](k6/) — см. `k6/README.md`.

Получить токен:

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8090/realms/timeline/protocol/openid-connect/token \
  -d grant_type=password -d client_id=timeline-web \
  -d username=anna.admin -d password=admin123 | jq -r .access_token)
```

Завести роль и сотрудника (требуется ADMIN):

```bash
curl -X POST http://localhost:8080/api/v1/disciplines \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"code":"BACKEND","name":"Разработка","velocitySpPerSprint":20}'

curl -X POST http://localhost:8080/api/v1/team/members \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"userId":"22222222-2222-2222-2222-222222222222","fullName":"Пётр",
       "disciplineId":"<id роли>","lead":false,"activeFrom":"2026-01-01"}'
```

Сотрудник корректирует свой отпуск:

```
TOKEN=$(curl -s -X POST \
  http://localhost:8090/realms/timeline/protocol/openid-connect/token \
  -d grant_type=password -d client_id=timeline-web \
  -d username=petr.dev -d password=member123 | jq -r .access_token)

curl -X POST http://localhost:8080/api/v1/team/me/vacations \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"startDate":"2026-07-01","endDate":"2026-07-14","type":"VACATION"}'
```

Повторный запрос на пересекающийся период вернёт `409` с кодом
`VACATION_OVERLAP`, а попытка изменить чужой отпуск — `403` с кодом
`NOT_OWN_VACATION`.

Доска плана (один запрос) и поток обновлений:

```bash
curl -s http://localhost:8080/api/v1/timeline/board \
  -H "Authorization: Bearer $TOKEN" | jq '.conflictsSummary,.sprints[0].capacity'

# SSE: события board-updated приходят после пересчёта плана
curl -N http://localhost:8080/api/v1/timeline/stream \
  -H "Authorization: Bearer $TOKEN"
```

## Сборка и тесты

```bash
./gradlew build
```

Модульные тесты выполняются всегда. Интеграционные поднимают Postgres через
Testcontainers и проверяют миграции вместе с ограничениями уровня СУБД; без
запущенного Docker они пропускаются, а не падают.

## Статус реализации

| Этап | Состояние |
|---|---|
| Каркас, окружение, шлюз | готово |
| `team-service` | готово |
| `schedule-service` | готово |
| `backlog-service` | готово |
| `planning-service` | готово |
| `timeline-bff` | готово |
| Саги удаления (спринт и задача), retry/DLT | готово |
| Наблюдаемость (Prometheus, Grafana, Tempo, OTel) | готово |
| Postman | готово |
| k6 | готово (`k6/`, `docs/load-testing.md`) |
| Фронтенд | готово (`frontend/`) |
