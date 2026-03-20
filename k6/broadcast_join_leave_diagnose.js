import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const HOST = (__ENV.HOST || 'https://dynii.deskit.o-r.kr').replace(/\/$/, '');
const API_BASE_URL = (__ENV.API_BASE_URL || `${HOST}/api`).replace(/\/$/, '');

const BROADCAST_ID = (__ENV.BROADCAST_ID || '').trim();
const BROADCAST_IDS = (__ENV.BROADCAST_IDS || '')
  .split(',')
  .map((v) => v.trim())
  .filter(Boolean);

const MODE = (__ENV.MODE || 'join_leave').trim().toLowerCase();
const USE_VIEWER_HEADER = (__ENV.USE_VIEWER_HEADER || 'true').toLowerCase() === 'true';
const VIEWER_ID_PREFIX = (__ENV.VIEWER_ID_PREFIX || 'k6-viewer').trim();
const TRACE_ID_PREFIX = (__ENV.TRACE_ID_PREFIX || 'k6-trace').trim();

const STAY_MIN_SECONDS = Number(__ENV.STAY_MIN_SECONDS || '1');
const STAY_MAX_SECONDS = Number(__ENV.STAY_MAX_SECONDS || '5');
const THINK_SECONDS = Number(__ENV.THINK_SECONDS || '0.1');
const STRICT_JOIN = (__ENV.STRICT_JOIN || 'false').toLowerCase() === 'true';

const START_RATE = Number(__ENV.START_RATE || '5');
const RATE_STEP = Number(__ENV.RATE_STEP || '5');
const STAGES = Number(__ENV.STAGES || '4');
const STAGE_DURATION = __ENV.STAGE_DURATION || '2m';
const PRE_ALLOCATED_VUS = Number(__ENV.PRE_ALLOCATED_VUS || '30');
const MAX_VUS = Number(__ENV.MAX_VUS || '400');

const P95_THRESHOLD_MS = Number(__ENV.P95_THRESHOLD_MS || '1500');
const FAIL_THRESHOLD = Number(__ENV.FAIL_THRESHOLD || '0.05');
const SUCCESS_THRESHOLD = Number(__ENV.SUCCESS_THRESHOLD || '0.95');

const joinFailures = new Counter('join_failures');
const leaveFailures = new Counter('leave_failures');
const joinSuccessRate = new Rate('join_success_rate');
const leaveSuccessRate = new Rate('leave_success_rate');
const scenarioFailed = new Counter('scenario_failed');

const joinDuration = new Trend('join_duration_ms');
const leaveDuration = new Trend('leave_duration_ms');

const join4xx = new Counter('join_4xx');
const join5xx = new Counter('join_5xx');
const leave4xx = new Counter('leave_4xx');
const leave5xx = new Counter('leave_5xx');

function buildStages() {
  const stages = [];
  for (let i = 0; i < STAGES; i += 1) {
    stages.push({
      target: START_RATE + i * RATE_STEP,
      duration: STAGE_DURATION,
    });
  }
  return stages;
}

export const options = {
  scenarios: {
    join_leave_diagnose: {
      executor: 'ramping-arrival-rate',
      startRate: START_RATE,
      timeUnit: '1s',
      preAllocatedVUs: PRE_ALLOCATED_VUS,
      maxVUs: MAX_VUS,
      stages: buildStages(),
    },
  },
  thresholds: {
    http_req_failed: [`rate<${FAIL_THRESHOLD}`],
    http_req_duration: [`p(95)<${P95_THRESHOLD_MS}`],
    join_success_rate: [`rate>${SUCCESS_THRESHOLD}`],
    join_duration_ms: [`p(95)<${P95_THRESHOLD_MS}`],
    leave_duration_ms: [`p(95)<${P95_THRESHOLD_MS}`],
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

function buildTraceId(operation) {
  const nonce = Math.floor(Math.random() * 1e6);
  return `${TRACE_ID_PREFIX}-${operation}-${__VU}-${__ITER}-${Date.now()}-${nonce}`;
}

function classifyStatus(status, isJoin) {
  if (status >= 400 && status < 500) {
    if (isJoin) join4xx.add(1);
    else leave4xx.add(1);
  }
  if (status >= 500) {
    if (isJoin) join5xx.add(1);
    else leave5xx.add(1);
  }
}

function runJoin(broadcastId, viewerId) {
  const traceId = buildTraceId('join');
  const headers = {
    'X-Trace-Id': traceId,
    'X-Perf-Phase': MODE,
  };

  if (USE_VIEWER_HEADER) {
    headers['X-Viewer-Id'] = viewerId;
  }

  const res = http.post(`${API_BASE_URL}/broadcasts/${broadcastId}/join`, null, {
    headers,
    tags: { name: 'broadcast_join', mode: MODE },
  });

  joinDuration.add(res.timings.duration);

  const joinOk = check(res, {
    'join status is 200': (r) => r.status === 200,
    'join returns token': (r) => {
      const data = parseApiResultData(r.body);
      return typeof data === 'string' && data.length > 0;
    },
  });

  joinSuccessRate.add(joinOk);

  if (!joinOk) {
    joinFailures.add(1);
    classifyStatus(res.status, true);

    if (STRICT_JOIN) {
      scenarioFailed.add(1);
      fail(`join failed: status=${res.status}, traceId=${traceId}, body=${res.body}`);
    }
  }

  return { ok: joinOk, traceId, status: res.status };
}

function runLeave(broadcastId, viewerId) {
  const traceId = buildTraceId('leave');
  const headers = {
    'X-Trace-Id': traceId,
    'X-Perf-Phase': MODE,
  };

  if (USE_VIEWER_HEADER) {
    headers['X-Viewer-Id'] = viewerId;
  }

  const res = http.post(
    `${API_BASE_URL}/broadcasts/${broadcastId}/leave?viewerId=${encodeURIComponent(viewerId)}`,
    null,
    {
      headers,
      tags: { name: 'broadcast_leave', mode: MODE },
    }
  );

  leaveDuration.add(res.timings.duration);

  const leaveOk = check(res, {
    'leave status is 200': (r) => r.status === 200,
  });

  leaveSuccessRate.add(leaveOk);

  if (!leaveOk) {
    leaveFailures.add(1);
    classifyStatus(res.status, false);
  }

  return { ok: leaveOk, traceId, status: res.status };
}

export function setup() {
  if (!BROADCAST_ID && BROADCAST_IDS.length === 0) {
    fail('Set BROADCAST_ID or BROADCAST_IDS environment variable.');
  }

  if (!['join_leave', 'join_only', 'leave_only'].includes(MODE)) {
    fail("Set MODE to one of: join_leave, join_only, leave_only.");
  }
}

export default function () {
  const broadcastId = pickBroadcastId();
  const viewerId = buildViewerId();

  if (MODE === 'join_only') {
    runJoin(broadcastId, viewerId);
    sleep(THINK_SECONDS);
    return;
  }

  if (MODE === 'leave_only') {
    runLeave(broadcastId, viewerId);
    sleep(THINK_SECONDS);
    return;
  }

  const joinResult = runJoin(broadcastId, viewerId);
  if (!joinResult.ok) {
    sleep(THINK_SECONDS);
    return;
  }

  sleep(randomBetween(STAY_MIN_SECONDS, STAY_MAX_SECONDS));
  runLeave(broadcastId, viewerId);
  sleep(THINK_SECONDS);
}

export function handleSummary(data) {
  const dropped = data.metrics.dropped_iterations?.values?.count || 0;
  const p95 = data.metrics.http_req_duration?.values?.['p(95)'] || 0;

  return {
    stdout: JSON.stringify(
      {
        mode: MODE,
        rates: {
          startRate: START_RATE,
          rateStep: RATE_STEP,
          stages: STAGES,
          stageDuration: STAGE_DURATION,
        },
        thresholds: {
          p95Ms: P95_THRESHOLD_MS,
          failRate: FAIL_THRESHOLD,
          successRate: SUCCESS_THRESHOLD,
        },
        result: {
          p95Ms: Number(p95.toFixed(2)),
          droppedIterations: dropped,
          maxVUs: data.metrics.vus_max?.values?.max || 0,
        },
      },
      null,
      2
    ),
  };
}
