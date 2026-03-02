import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const HOST = (__ENV.HOST || 'https://dynii.deskit.o-r.kr').replace(/\/$/, '');
const API_BASE_URL = (__ENV.API_BASE_URL || `${HOST}/api`).replace(/\/$/, '');
const TEST_AUTH_SECRET = (__ENV.TEST_AUTH_SECRET || '').trim();

const SELLER_COUNT = Number(__ENV.SELLER_COUNT || '10');
const SELLER_LOGIN_PREFIX = (__ENV.SELLER_LOGIN_PREFIX || 'k6-temp-seller').trim();
const SELLER_NAME_PREFIX = (__ENV.SELLER_NAME_PREFIX || 'K6 Temp Seller').trim();
const START_INDEX = Number(__ENV.SELLER_START_INDEX || '1');

const SCHEDULED_AT = (__ENV.SCHEDULED_AT || '').trim();
const SCHEDULE_OFFSET_MINUTES = Number(__ENV.SCHEDULE_OFFSET_MINUTES || '30');

const THINK_SECONDS = Number(__ENV.THINK_SECONDS || '0');
const PRODUCT_QTY = Number(__ENV.PRODUCT_QTY || '1');
const PRODUCT_PRICE = Number(__ENV.PRODUCT_PRICE || '10000');
const BROADCAST_LAYOUT = (__ENV.BROADCAST_LAYOUT || 'FULL').trim();
const THUMBNAIL_URL = (__ENV.THUMBNAIL_URL || 'https://velog.velcdn.com/images/doyooning/post/fcbabf2e-5f16-435e-93aa-3e456057b0d9/image.jpg').trim();
const WAIT_SCREEN_URL = (__ENV.WAIT_SCREEN_URL || 'https://velog.velcdn.com/images/doyooning/post/2e737a1b-2133-4c6b-9a49-22a26e659e49/image.jpg').trim();
const DEBUG_FAILURES = (__ENV.DEBUG_FAILURES || 'true').toLowerCase() === 'true';
const FAILURE_LOG_LIMIT = Number(__ENV.FAILURE_LOG_LIMIT || '20');

const SUCCESS = new Counter('reserve_success');
const SLOT_FULL = new Counter('reserve_slot_full');
const RESERVATION_LIMIT = new Counter('reserve_limit_exceeded');
const TOO_MANY_REQ = new Counter('reserve_too_many_requests');
const FORBIDDEN = new Counter('reserve_forbidden');
const INVALID_INPUT = new Counter('reserve_invalid_input');
const CATEGORY_NOT_FOUND = new Counter('reserve_category_not_found');
const PRODUCT_NOT_FOUND = new Counter('reserve_product_not_found');
const BROADCAST_TRANSITION_FAIL = new Counter('reserve_broadcast_transition_fail');
const UNKNOWN_ERROR_CODE = new Counter('reserve_unknown_error_code');
const OTHER_FAIL = new Counter('reserve_other_failures');
const SUCCESS_RATE = new Rate('reserve_success_rate');
let failureLogCount = 0;

export const options = {
  scenarios: {
    reserve_create_competition: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.VUS || String(SELLER_COUNT)),
      iterations: Number(__ENV.ITERATIONS || '1'),
      maxDuration: __ENV.MAX_DURATION || '3m',
    },
  },
  thresholds: {
    reserve_success_rate: ['rate>0.1'],
  },
};

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 429 }));

function parseJsonSafe(body) {
  try {
    return JSON.parse(body);
  } catch (_) {
    return null;
  }
}

function toKstLocalDateTime(date) {
  const y = date.getUTCFullYear();
  const m = String(date.getUTCMonth() + 1).padStart(2, '0');
  const d = String(date.getUTCDate()).padStart(2, '0');
  const hh = String(date.getUTCHours()).padStart(2, '0');
  const mm = String(date.getUTCMinutes()).padStart(2, '0');
  const ss = String(date.getUTCSeconds()).padStart(2, '0');
  return `${y}-${m}-${d} ${hh}:${mm}:${ss}`;
}

function computeScheduledAtKst() {
  if (SCHEDULED_AT) return SCHEDULED_AT;

  // Build time in KST clock, rounded to next 30-minute slot, then push ahead.
  const nowKst = new Date(Date.now() + 9 * 60 * 60 * 1000);
  nowKst.setUTCSeconds(0, 0);
  const min = nowKst.getUTCMinutes();
  const toNextSlot = min < 30 ? 30 - min : 60 - min;
  const totalAhead = toNextSlot + Math.max(1, SCHEDULE_OFFSET_MINUTES);
  nowKst.setUTCMinutes(nowKst.getUTCMinutes() + totalAhead);
  return toKstLocalDateTime(nowKst);
}

function bootstrapSellers(secretHeader) {
  const url = `${API_BASE_URL}/internal/test-auth/sellers/bootstrap`;
  const payload = {
    count: SELLER_COUNT,
    startIndex: START_INDEX,
    loginPrefix: SELLER_LOGIN_PREFIX,
    namePrefix: SELLER_NAME_PREFIX,
    createProduct: true,
  };

  const res = http.post(url, JSON.stringify(payload), {
    headers: {
      'Content-Type': 'application/json',
      'X-Test-Auth-Secret': secretHeader,
    },
    tags: { name: 'test_auth_bootstrap_sellers' },
  });

  const ok = check(res, {
    'bootstrap sellers 200': (r) => r.status === 200,
  });
  if (!ok) {
    fail(`bootstrap sellers failed: status=${res.status}, body=${res.body}`);
  }

  const json = parseJsonSafe(res.body);
  if (!json || !Array.isArray(json.sellers) || json.sellers.length === 0) {
    fail(`bootstrap sellers invalid response: body=${res.body}`);
  }
  return json.sellers;
}

function issueSellerToken(secretHeader, seller) {
  const res = http.post(
    `${API_BASE_URL}/internal/test-auth/seller-token`,
    JSON.stringify({ sellerId: seller.sellerId }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Test-Auth-Secret': secretHeader,
      },
      tags: { name: 'test_auth_issue_seller_token' },
    }
  );

  const ok = check(res, {
    'issue token 200': (r) => r.status === 200,
  });
  if (!ok) {
    fail(`issue token failed: status=${res.status}, body=${res.body}`);
  }

  const json = parseJsonSafe(res.body);
  if (!json || !json.accessToken) {
    fail(`issue token invalid response: body=${res.body}`);
  }
  return json.accessToken;
}

function fetchCategoryId() {
  const res = http.get(`${API_BASE_URL}/categories`, { tags: { name: 'categories_list' } });
  const ok = check(res, {
    'categories 200': (r) => r.status === 200,
  });
  if (!ok) {
    fail(`categories failed: status=${res.status}, body=${res.body}`);
  }
  const json = parseJsonSafe(res.body);
  const first = json?.data?.[0];
  const categoryId = first?.categoryId ?? first?.id;
  if (!categoryId) {
    fail(`no category found: body=${res.body}`);
  }
  return categoryId;
}

export function setup() {
  if (!TEST_AUTH_SECRET) {
    fail('Set TEST_AUTH_SECRET env var.');
  }

  const secret = TEST_AUTH_SECRET;
  const sellers = bootstrapSellers(secret);
  const categoryId = fetchCategoryId();
  const scheduledAt = computeScheduledAtKst();

  const sellerSessions = sellers.map((seller) => {
    if (!seller.productId) {
      fail(`seller has no productId: ${JSON.stringify(seller)}`);
    }
    const accessToken = issueSellerToken(secret, seller);
    return {
      sellerId: seller.sellerId,
      loginId: seller.loginId,
      productId: seller.productId,
      accessToken,
    };
  });

  return { sellerSessions, categoryId, scheduledAt };
}

function pickSellerSession(data) {
  return data.sellerSessions[(__VU - 1) % data.sellerSessions.length];
}

function reservePayload(data, seller) {
  const shortNonce = String(Date.now()).slice(-6);
  return {
    title: `k6r-${seller.sellerId}-${__VU}-${shortNonce}`,
    notice: 'k6 reservation competition test',
    categoryId: data.categoryId,
    scheduledAt: data.scheduledAt,
    thumbnailUrl: THUMBNAIL_URL,
    waitScreenUrl: WAIT_SCREEN_URL,
    broadcastLayout: BROADCAST_LAYOUT,
    products: [
      {
        productId: seller.productId,
        bpPrice: PRODUCT_PRICE,
        bpQuantity: PRODUCT_QTY,
      },
    ],
    qcards: [],
  };
}

function classifyFailure(res, resBody, seller, payload) {
  const code = resBody?.error?.code;
  if (code === 'B005') {
    SLOT_FULL.add(1);
    return;
  }
  if (code === 'B004') {
    RESERVATION_LIMIT.add(1);
    return;
  }
  if (code === 'SY001') {
    TOO_MANY_REQ.add(1);
    return;
  }
  if (code === 'C003') {
    FORBIDDEN.add(1);
    return;
  }
  if (code === 'C001') {
    INVALID_INPUT.add(1);
    return;
  }
  if (code === 'CT001') {
    CATEGORY_NOT_FOUND.add(1);
    return;
  }
  if (code === 'P001') {
    PRODUCT_NOT_FOUND.add(1);
    return;
  }
  if (code === 'B006') {
    BROADCAST_TRANSITION_FAIL.add(1);
    return;
  }
  if (code) {
    UNKNOWN_ERROR_CODE.add(1);
  }
  OTHER_FAIL.add(1);

  if (DEBUG_FAILURES && failureLogCount < FAILURE_LOG_LIMIT) {
    failureLogCount += 1;
    const message = resBody?.error?.message || 'unknown_error_message';
    console.error(
      `[reserve-failure] status=${res.status} code=${code || 'none'} message=${message} sellerId=${seller?.sellerId} productId=${seller?.productId} scheduledAt=${payload?.scheduledAt} body=${res.body}`
    );
  }
}

export default function (data) {
  const seller = pickSellerSession(data);
  const payload = reservePayload(data, seller);

  const res = http.post(`${API_BASE_URL}/seller/broadcasts`, JSON.stringify(payload), {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${seller.accessToken}`,
    },
    tags: { name: 'seller_broadcast_create' },
  });

  const body = parseJsonSafe(res.body);
  const success = res.status === 200 && body?.success === true && typeof body?.data === 'number';
  SUCCESS_RATE.add(success);

  check(res, {
    'reserve status in expected range': (r) => r.status === 200 || r.status === 400 || r.status === 403 || r.status === 429,
  });

  if (success) {
    SUCCESS.add(1);
  } else {
    classifyFailure(res, body, seller, payload);
  }

  if (THINK_SECONDS > 0) {
    sleep(THINK_SECONDS);
  }
}
