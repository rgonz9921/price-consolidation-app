import { createServer } from 'node:http';

const PORT = Number(process.env.PORT ?? 9001);
const MIN_LATENCY_MS = 50;
const MAX_LATENCY_MS = 150;
const STALE_RATIO = 0.3;
const FRESH_MAX_AGE_MS = 5 * 60_000;
const STALE_MIN_AGE_MS = 16 * 60_000;
const STALE_MAX_AGE_MS = 60 * 60_000;
const HANGING_RESPONSE_MS = 10_000;
const MODES = new Set(['up', 'down', 'timeout', 'error']);

let mode = 'up';

const randomBetween = (min, max) => min + Math.random() * (max - min);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function basePriceFor(sku) {
  let hash = 0;
  for (const char of sku) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  return 50 + (hash % 45_000) / 100;
}

function lastUpdatedNow() {
  const age = Math.random() < STALE_RATIO
    ? randomBetween(STALE_MIN_AGE_MS, STALE_MAX_AGE_MS)
    : randomBetween(0, FRESH_MAX_AGE_MS);
  return new Date(Date.now() - age).toISOString().replace(/\.\d{3}Z$/, 'Z');
}

function sendJson(res, status, body) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(body));
}

async function readJson(req) {
  let raw = '';
  for await (const chunk of req) raw += chunk;
  return JSON.parse(raw || '{}');
}

async function changeMode(req, res) {
  const { mode: requested } = await readJson(req).catch(() => ({}));
  if (!MODES.has(requested)) {
    return sendJson(res, 400, { error: `mode must be one of: ${[...MODES].join(', ')}` });
  }
  mode = requested;
  sendJson(res, 200, { mode });
}

async function sendPrice(res, sku) {
  if (mode === 'down') return sendJson(res, 503, { error: 'service unavailable' });
  if (mode === 'error') return sendJson(res, 500, { error: 'internal error' });

  await sleep(mode === 'timeout' ? HANGING_RESPONSE_MS : randomBetween(MIN_LATENCY_MS, MAX_LATENCY_MS));
  sendJson(res, 200, {
    sku,
    basePrice: basePriceFor(sku),
    currency: 'USD',
    lastUpdated: lastUpdatedNow(),
  });
}

createServer((req, res) => {
  const { pathname } = new URL(req.url, 'http://localhost');
  const priceRoute = pathname.match(/^\/prices\/([^/]+)$/);

  if (req.method === 'GET' && priceRoute) return sendPrice(res, decodeURIComponent(priceRoute[1]));
  if (req.method === 'POST' && pathname === '/__control') return changeMode(req, res);
  if (req.method === 'GET' && pathname === '/health') return sendJson(res, 200, { mode });
  sendJson(res, 404, { error: 'not found' });
}).listen(PORT, () => console.log(`provider-a listening on ${PORT}`));
