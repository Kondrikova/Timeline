import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

/**
 * Основной профиль нагрузки: ~95% трафика — чтение доски.
 *
 * SLO: p95 < 300 мс при ~50 RPS (см. docs/architecture.md §16).
 *
 * Запуск:
 *   BASE_URL=http://localhost:8080 TOKEN=<jwt> k6 run k6/board-load.js
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';

const etagHitRate = new Rate('board_etag_304');
const boardLatency = new Trend('board_latency', true);

export const options = {
  scenarios: {
    board_steady: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 20,
      maxVUs: 80,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    board_latency: ['p(95)<300'],
  },
};

let lastEtag = '';

export default function () {
  const headers = {
    Authorization: `Bearer ${TOKEN}`,
  };
  if (lastEtag) {
    headers['If-None-Match'] = lastEtag;
  }

  const res = http.get(`${BASE_URL}/api/v1/timeline/board`, { headers });
  boardLatency.add(res.timings.duration);
  etagHitRate.add(res.status === 304);

  check(res, {
    'board 200 or 304': (r) => r.status === 200 || r.status === 304,
  });

  const etag = res.headers.ETag || res.headers.Etag;
  if (etag) {
    lastEtag = etag;
  }

  sleep(0.01);
}
