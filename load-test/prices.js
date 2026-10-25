import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://backend:8080';
const PROFILE = __ENV.PROFILE || 'hot';
const PROVIDERS = ['AUTO', 'PROVIDER_A', 'PROVIDER_B'];

const PROFILES = {
  hot: { rate: 200, skus: [], gatesSla: true },
  mixed: { rate: 200, skus: generatedSkus('LT', 50), gatesSla: true },
  worst: { rate: 10, skus: generatedSkus('WC', 600), gatesSla: false },
};
const HOT_SKUS = ['ABC-123', 'XYZ-789', 'LMN-456'];
const profile = PROFILES[PROFILE];

const latencyBySource = {
  CACHE: new Trend('latency_cache', true),
  PROVIDER_A: new Trend('latency_provider_a', true),
  PROVIDER_B: new Trend('latency_provider_b', true),
  FALLBACK_CACHE: new Trend('latency_fallback_cache', true),
};
const priceQueryLatency = new Trend('price_query_latency', true);
const unavailable = new Counter('responses_503');

export const options = {
  scenarios: {
    [PROFILE]: {
      executor: 'constant-arrival-rate',
      rate: profile.rate,
      timeUnit: '1s',
      duration: __ENV.DURATION || '60s',
      preAllocatedVUs: 50,
      maxVUs: 200,
    },
  },
  thresholds: profile.gatesSla
    ? { price_query_latency: ['p(95)<800'], checks: ['rate>0.99'] }
    : {},
};

function generatedSkus(prefix, count) {
  return Array.from({ length: count }, (_, i) => `${prefix}-${String(i + 1).padStart(3, '0')}`);
}

function ruleFor(sku, index) {
  const preferredProvider = PROFILE === 'worst' ? 'PROVIDER_B' : PROVIDERS[index % PROVIDERS.length];
  return JSON.stringify({ sku, name: `Carga ${sku}`, marginPercentage: 10, preferredProvider });
}

export function setup() {
  const params = { headers: { 'Content-Type': 'application/json' } };
  profile.skus.forEach((sku, index) => {
    http.post(`${BASE_URL}/api/v1/product-rules`, ruleFor(sku, index), params);
  });
}

export function teardown() {
  profile.skus.forEach((sku) => http.del(`${BASE_URL}/api/v1/product-rules/${sku}`));
}

function nextSku() {
  if (PROFILE === 'hot') {
    return HOT_SKUS[Math.floor(Math.random() * HOT_SKUS.length)];
  }
  if (PROFILE === 'worst') {
    return profile.skus[exec.scenario.iterationInTest % profile.skus.length];
  }
  return profile.skus[Math.floor(Math.random() * profile.skus.length)];
}

export default function () {
  const response = http.get(`${BASE_URL}/api/v1/prices?sku=${nextSku()}`, {
    tags: { name: 'GET /api/v1/prices' },
  });
  priceQueryLatency.add(response.timings.duration);
  if (response.status === 200) {
    latencyBySource[response.json('source')].add(response.timings.duration);
  } else if (response.status === 503) {
    unavailable.add(1);
  }
  check(response, { 'status is 200': (r) => r.status === 200 });
}
