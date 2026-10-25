import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';
const HOT_SKUS = ['ABC-123', 'XYZ-789', 'LMN-456'];

export const options = {
  scenarios: {
    bursts_of_repeated_skus: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 200),
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<800'],
    checks: ['rate>0.99'],
  },
};

export default function () {
  const sku = HOT_SKUS[Math.floor(Math.random() * HOT_SKUS.length)];
  const response = http.get(`${BASE_URL}/api/v1/prices?sku=${sku}`, {
    tags: { name: 'GET /api/v1/prices' },
  });
  check(response, {
    'status is 200': (r) => r.status === 200,
    'has final price': (r) => r.status === 200 && r.json('finalPrice') !== null,
  });
}
