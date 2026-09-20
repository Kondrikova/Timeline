import http from 'k6/http';
import { check, sleep } from 'k6';
import { Trend } from 'k6/metrics';

/**
 * Смешанный профиль: 95% board + 4% move preview + 1% recalculate.
 *
 * Переменные:
 *   BASE_URL, TOKEN, TASK_ID, TO_SPRINT_ID
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const TASK_ID = __ENV.TASK_ID || '';
const TO_SPRINT_ID = __ENV.TO_SPRINT_ID || '';

const boardLatency = new Trend('board_latency', true);
const moveLatency = new Trend('move_preview_latency', true);

export const options = {
  scenarios: {
    mixed: {
      executor: 'constant-arrival-rate',
      rate: 50,
      timeUnit: '1s',
      duration: '2m',
      preAllocatedVUs: 25,
      maxVUs: 100,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.02'],
    board_latency: ['p(95)<300'],
    move_preview_latency: ['p(95)<1000'],
  },
};

const auth = { Authorization: `Bearer ${TOKEN}`, 'Content-Type': 'application/json' };

export default function () {
  const roll = Math.random();

  if (roll < 0.95) {
    const res = http.get(`${BASE_URL}/api/v1/timeline/board`, { headers: auth });
    boardLatency.add(res.timings.duration);
    check(res, { 'board ok': (r) => r.status === 200 || r.status === 304 });
  }
  else if (roll < 0.99 && TASK_ID && TO_SPRINT_ID) {
    const res = http.post(
      `${BASE_URL}/api/v1/plan/tasks/${TASK_ID}/move`,
      JSON.stringify({ toSprintId: TO_SPRINT_ID, preview: true }),
      { headers: auth },
    );
    moveLatency.add(res.timings.duration);
    check(res, { 'move preview ok': (r) => r.status === 200 });
  }
  else {
    const res = http.post(`${BASE_URL}/api/v1/plan/recalculate`, null, { headers: auth });
    check(res, { 'recalculate ok': (r) => r.status === 204 || r.status === 200 });
  }

  sleep(0.01);
}
