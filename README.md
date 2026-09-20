## Сервис долгосрочного планирования на команду (Timeline)

Инструмент планирования работ команды на горизонт нескольких месяцев: задачи с
оценками по ролям в SP, спринты, отпуска, ёмкость ролей с учётом отпусков, связи
между задачами и каскадный пересчёт плана.

## Документация

- [Архитектура решения](docs/architecture.md) — декомпозиция на микросервисы,
  диаграммы C4, модель данных, межсервисное взаимодействие, консистентность,
  безопасность, наблюдаемость, план реализации и реестр архитектурных решений.

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
deploy                docker-compose, realm Keycloak, инициализация Postgres
```

## Запуск

Требуются Docker и Docker Compose. JDK локально не нужен: сборка идёт внутри
образа.

```bash
cd deploy
docker compose up --build
```

Поднимаются Postgres, Kafka (KRaft), Redis, Keycloak с импортом realm, а также
`team-service` и `api-gateway`.

| Компонент | Адрес |
|---|---|
| API Gateway | http://localhost:8080 |
| team-service | http://localhost:8082 |
| Keycloak | http://localhost:8090 (admin/admin) |

Преднастроенные пользователи realm `timeline`:

| Логин | Пароль | Роль |
|---|---|---|
| `anna.admin` | `admin123` | ADMIN |
| `petr.dev` | `member123` | MEMBER |
| `olga.qa` | `member123` | MEMBER |

## Проверка сценария

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
| Саги удаления задачи, DLQ | не начаты (сага удаления спринта есть) |
| Наблюдаемость | базовая: метрики и health |
| Postman, k6, фронтенд | не начаты |
