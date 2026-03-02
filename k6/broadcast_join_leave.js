import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const HOST = (__ENV.HOST || 'https://dynii.deskit.o-r.kr').replace(/\/$/, '');
const API_BASE_URL = (__ENV.API_BASE_URL || `${HOST}/api`).replace(/\/$/, '');

const BROADCAST_ID = (__ENV.BROADCAST_ID || '').trim();
const BROADCAST_IDS = (__ENV.BROADCAST_IDS || '')
  .split(',')
  .map((v) => v.trim())
  .filter(Boolean);

const VIEWER_ID_PREFIX = (__ENV.VIEWER_ID_PREFIX || 'k6-viewer').trim();
const STAY_MIN_SECONDS = Number(__ENV.STAY_MIN_SECONDS || '1');
const STAY_MAX_SECONDS = Number(__ENV.STAY_MAX_SECONDS || '5');
const THINK_SECONDS = Number(__ENV.THINK_SECONDS || '0.2');
const USE_VIEWER_HEADER = (__ENV.USE_VIEWER_HEADER || 'true').toLowerCase() === 'true';
const STRICT_JOIN = (__ENV.STRICT_JOIN || 'true').toLowerCase() === 'true';

const RATE = Number(__ENV.RATE || '20');
const DURATION = __ENV.DURATION || '2m';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || '50');
const MAX_VUS = Number(__ENV.MAX_VUS || '300');

const joinFailures = new Counter('join_failures');
const leaveFailures = new Counter('leave_failures');
const joinSuccessRate = new Rate('join_success_rate');
const leaveSuccessRate = new Rate('leave_success_rate');

export const options = {
  scenarios: {
    join_leave_storm: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1500'],
    join_success_rate: ['rate>0.95'],
    leave_success_rate: ['rate>0.95'],
  },
};

function parseApiResultData(body) {
  try {
    const json = JSON.parse(body);
    return json?.data;
  } catch (_) {
    return null;
  }
}

function pickBroadcastId() {
  if (BROADCAST_IDS.length > 0) {
    return BROADCAST_IDS[(__ITER + __VU - 1) % BROADCAST_IDS.length];
  }
  return BROADCAST_ID;
}

function randomBetween(min, max) {
  if (max <= min) return min;
  return min + Math.random() * (max - min);
}

function buildViewerId() {
  const nonce = Math.floor(Math.random() * 1e9);
  return `${VIEWER_ID_PREFIX}-${__VU}-${__ITER}-${Date.now()}-${nonce}`;
}

export function setup() {
  if (!BROADCAST_ID && BROADCAST_IDS.length === 0) {
    fail('Set BROADCAST_ID or BROADCAST_IDS environment variable.');
  }
}

export default function () {
  const broadcastId = pickBroadcastId();
  const viewerId = buildViewerId();

  const joinHeaders = USE_VIEWER_HEADER ? { 'X-Viewer-Id': viewerId } : {};
  const joinRes = http.post(`${API_BASE_URL}/broadcasts/${broadcastId}/join`, null, {
    headers: joinHeaders,
    tags: { name: 'broadcast_join' },
  });

  const joinOk = check(joinRes, {
    'join status is 200': (r) => r.status === 200,
    'join returns token': (r) => {
      const data = parseApiResultData(r.body);
      return typeof data === 'string' && data.length > 0;
    },
  });
  joinSuccessRate.add(joinOk);

  if (!joinOk) {
    joinFailures.add(1);
    if (STRICT_JOIN) {
      fail(`join failed: status=${joinRes.status}, body=${joinRes.body}`);
    }
    sleep(THINK_SECONDS);
    return;
  }

  sleep(randomBetween(STAY_MIN_SECONDS, STAY_MAX_SECONDS));

  const leaveHeaders = USE_VIEWER_HEADER ? { 'X-Viewer-Id': viewerId } : {};
  const leaveRes = http.post(
    `${API_BASE_URL}/broadcasts/${broadcastId}/leave?viewerId=${encodeURIComponent(viewerId)}`,
    null,
    {
      headers: leaveHeaders,
      tags: { name: 'broadcast_leave' },
    }
  );

  const leaveOk = check(leaveRes, {
    'leave status is 200': (r) => r.status === 200,
  });
  leaveSuccessRate.add(leaveOk);

  if (!leaveOk) {
    leaveFailures.add(1);
  }

  sleep(THINK_SECONDS);
}
