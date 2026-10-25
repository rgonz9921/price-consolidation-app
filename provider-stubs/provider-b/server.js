import { createServer } from 'node:http';

const PORT = Number(process.env.PORT ?? 9002);
const MIN_LATENCY_MS = 400;
const MAX_LATENCY_MS = 900;
const MAX_REQUESTS_PER_WINDOW = 5;
const WINDOW_MS = 1_000;
const MAX_QUOTE_AGE_MS = 60_000;
const COST_PREMIUM = 1.02;
const HANGING_RESPONSE_MS = 10_000;
const MODES = new Set(['up', 'down', 'timeout', 'error']);

let mode = 'up';
const acceptedAt = [];

const randomBetween = (min, max) => min + Math.random() * (max - min);
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function costFor(itemCode) {
  let hash = 0;
  for (const char of itemCode) hash = (hash * 31 + char.charCodeAt(0)) >>> 0;
  const basePrice = 50 + (hash % 45_000) / 100;
  return Math.round(basePrice * COST_PREMIUM * 100) / 100;
}

function retryAfterSeconds(now) {
  while (acceptedAt.length && now - acceptedAt[0] >= WINDOW_MS) acceptedAt.shift();
  if (acceptedAt.length < MAX_REQUESTS_PER_WINDOW) return 0;
  return Math.ceil((WINDOW_MS - (now - acceptedAt[0])) / 1_000);
}

function sendJson(res, status, body, headers = {}) {
  res.writeHead(status, { 'Content-Type': 'application/json', ...headers });
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

async function sendCost(res, itemCode) {
  const now = Date.now();
  const retryAfter = retryAfterSeconds(now);
  if (retryAfter > 0) {
    return sendJson(res, 429, { error: 'rate limit exceeded' }, { 'Retry-After': String(retryAfter) });
  }
  acceptedAt.push(now);

  if (mode === 'down') return sendJson(res, 503, { error: 'service unavailable' });
  if (mode === 'error') return sendJson(res, 500, { error: 'internal error' });

  await sleep(mode === 'timeout' ? HANGING_RESPONSE_MS : randomBetween(MIN_LATENCY_MS, MAX_LATENCY_MS));
  sendJson(res, 200, {
    itemCode,
    cost: costFor(itemCode),
    curr: 'USD',
    timestamp: Math.floor((Date.now() - randomBetween(0, MAX_QUOTE_AGE_MS)) / 1_000),
  });
}

createServer((req, res) => {
  const { pathname } = new URL(req.url, 'http://localhost');
  const costRoute = pathname.match(/^\/items\/([^/]+)\/cost$/);

  if (req.method === 'GET' && costRoute) return sendCost(res, decodeURIComponent(costRoute[1]));
  if (req.method === 'POST' && pathname === '/__control') return changeMode(req, res);
  if (req.method === 'GET' && pathname === '/health') return sendJson(res, 200, { mode });
  sendJson(res, 404, { error: 'not found' });
}).listen(PORT, () => console.log(`provider-b listening on ${PORT}`));
