# Нагрузочные сценарии k6

Скрипты соответствуют профилю из `docs/architecture.md` §16.

## Подготовка

```bash
TOKEN=$(curl -s -X POST \
  http://localhost:8090/realms/timeline/protocol/openid-connect/token \
  -d grant_type=password -d client_id=timeline-web \
  -d username=anna.admin -d password=admin123 | jq -r .access_token)
```

## Прогоны

```bash
# ~50 RPS на доску, SLO p95 < 300 мс
BASE_URL=http://localhost:8080 TOKEN=$TOKEN k6 run k6/board-load.js

# смешанный: board + move preview + recalculate
BASE_URL=http://localhost:8080 TOKEN=$TOKEN \
  TASK_ID=<uuid> TO_SPRINT_ID=<uuid> \
  k6 run k6/mixed.js
```

Результаты и выводы — в `docs/load-testing.md`.
