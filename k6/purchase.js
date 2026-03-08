import http from 'k6/http';
import { check, fail, sleep } from 'k6';
import { Counter } from 'k6/metrics';

const RAW_HOST = (__ENV.HOST || 'https://dynii.deskit.o-r.kr').replace(/\/$/, '');
const HOST = RAW_HOST.replace(/\/api$/, '');
const API_BASE_URL = (__ENV.API_BASE_URL || `${HOST}/api`).replace(/\/$/, '');

const TEST_AUTH_SECRET = (__ENV.TEST_AUTH_SECRET || '').trim();
const BUYER_COUNT = Number(__ENV.BUYER_COUNT || '10');
const THINK_SECONDS = Number(__ENV.THINK_SECONDS || '0');
const AUTH_DEBUG = (__ENV.AUTH_DEBUG || 'true').toLowerCase() === 'true';
const DEBUG_ORDER_FAILURES = (__ENV.DEBUG_ORDER_FAILURES || 'true').toLowerCase() === 'true';
const ORDER_FAILURE_LOG_LIMIT = Number(__ENV.ORDER_FAILURE_LOG_LIMIT || '20');

const PRICE = Number(__ENV.PRICE || '10000');
const PRODUCT_ID = (__ENV.PRODUCT_ID || '').trim();
const PAYMENT_MODE = (__ENV.PAYMENT_MODE || 'create_only').trim().toLowerCase();
const PAYMENT_KEY_PREFIX = (__ENV.PAYMENT_KEY_PREFIX || 'k6-purchase').trim();
const POSTCODE = (__ENV.POSTCODE || '12345').trim();
const RECEIVER_PREFIX = (__ENV.RECEIVER_PREFIX || 'k6-buyer').trim();
const ADDRESS = (__ENV.ADDRESS || 'k6 load test address').trim();

const MEMBER_TOKENS = (__ENV.MEMBER_TOKENS || '')
  .split(',')
  .map((v) => v.trim())
  .filter(Boolean);

const FINAL_SUCCESS = new Counter('purchase_final_success');
const ORDER_CREATE_SUCCESS = new Counter('purchase_order_create_success');
const PAYMENT_CONFIRM_SUCCESS = new Counter('purchase_payment_confirm_success');

const ORDER_CREATE_FAIL = new Counter('purchase_order_create_fail');
const INSUFFICIENT_STOCK = new Counter('purchase_insufficient_stock');
const UNAUTHORIZED = new Counter('purchase_unauthorized');
const UNAUTHORIZED_401 = new Counter('purchase_unauthorized_401');
const FORBIDDEN_403 = new Counter('purchase_forbidden_403');
const PAYMENT_CONFIRM_FAIL = new Counter('purchase_payment_confirm_fail');
const OTHER_FAIL = new Counter('purchase_other_fail');
let orderFailureLogCount = 0;

export const options = {
  scenarios: {
    purchase_competition: {
      executor: 'per-vu-iterations',
      vus: Number(__ENV.VUS || String(BUYER_COUNT)),
      iterations: Number(__ENV.ITERATIONS || '1'),
      maxDuration: __ENV.MAX_DURATION || '2m',
    },
  },
  thresholds: {
    purchase_final_success: ['count==1'],
  },
};

http.setResponseCallback(http.expectedStatuses({ min: 200, max: 499 }));

function parseJsonSafe(body) {
  try {
    return JSON.parse(body);
  } catch (_) {
    return null;
  }
}

function parseFirstErrorMessage(body) {
  if (!body) return '';
  if (typeof body === 'string') return body;
  if (typeof body.message === 'string') return body.message;
  if (typeof body.error === 'string') return body.error;
  if (typeof body.error?.message === 'string') return body.error.message;
  return '';
}

function truncate(text, maxLen = 220) {
  if (typeof text !== 'string') return '';
  if (text.length <= maxLen) return text;
  return `${text.slice(0, maxLen)}...`;
}

function authProbe(secretHeader) {
  if (!AUTH_DEBUG || !secretHeader) return;

  const sellerBootstrapUrl = `${API_BASE_URL}/internal/test-auth/sellers/bootstrap`;
  const sellerBootstrapRes = http.post(
    sellerBootstrapUrl,
    JSON.stringify({
      count: 1,
      startIndex: 9091,
      loginPrefix: 'k6-probe-seller',
      namePrefix: 'K6 Probe Seller',
      createProduct: false,
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Test-Auth-Secret': secretHeader,
      },
      tags: { name: 'test_auth_probe_seller_bootstrap' },
    }
  );
  console.log(
    `[auth-probe] name=seller-bootstrap status=${sellerBootstrapRes.status} url=${sellerBootstrapUrl} body=${truncate(sellerBootstrapRes.body)}`
  );

  const sellerBootstrapJson = parseJsonSafe(sellerBootstrapRes.body);
  const probeSellerId = sellerBootstrapJson?.sellers?.[0]?.sellerId;
  if (probeSellerId) {
    const sellerTokenUrl = `${API_BASE_URL}/internal/test-auth/seller-token`;
    const sellerTokenRes = http.post(
      sellerTokenUrl,
      JSON.stringify({ sellerId: probeSellerId }),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Test-Auth-Secret': secretHeader,
        },
        tags: { name: 'test_auth_probe_seller_token' },
      }
    );
    console.log(
      `[auth-probe] name=seller-token status=${sellerTokenRes.status} url=${sellerTokenUrl} body=${truncate(sellerTokenRes.body)}`
    );
  }

  const memberBootstrapUrl = `${API_BASE_URL}/internal/test-auth/members/bootstrap`;
  const memberBootstrapRes = http.post(
    memberBootstrapUrl,
    JSON.stringify({
      count: 1,
      startIndex: 19091,
      loginPrefix: 'k6-probe-member',
      namePrefix: 'K6 Probe Member',
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Test-Auth-Secret': secretHeader,
      },
      tags: { name: 'test_auth_probe_member_bootstrap' },
    }
  );
  console.log(
    `[auth-probe] name=member-bootstrap status=${memberBootstrapRes.status} url=${memberBootstrapUrl} body=${truncate(memberBootstrapRes.body)}`
  );
}

function bootstrapSeller(secretHeader) {
  const payload = {
    count: 1,
    startIndex: Number(__ENV.SELLER_START_INDEX || '9001'),
    loginPrefix: (__ENV.SELLER_LOGIN_PREFIX || 'k6-purchase-seller').trim(),
    namePrefix: (__ENV.SELLER_NAME_PREFIX || 'K6 Purchase Seller').trim(),
    createProduct: false,
  };

  const res = http.post(
    `${API_BASE_URL}/internal/test-auth/sellers/bootstrap`,
    JSON.stringify(payload),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Test-Auth-Secret': secretHeader,
      },
      tags: { name: 'test_auth_bootstrap_sellers' },
    }
  );

  if (!check(res, { 'bootstrap seller 200': (r) => r.status === 200 })) {
    fail(`bootstrap seller failed: status=${res.status}, body=${res.body}`);
  }

  const json = parseJsonSafe(res.body);
  const seller = json?.sellers?.[0];
  if (!seller?.sellerId) {
    fail(`bootstrap seller invalid response: body=${res.body}`);
  }
  return seller.sellerId;
}

function bootstrapMembers(secretHeader) {
  const endpoints = [
    `${API_BASE_URL}/internal/test-auth/members/bootstrap`,
    `${HOST}/internal/test-auth/members/bootstrap`,
  ];
  const payload = {
    count: BUYER_COUNT,
    startIndex: Number(__ENV.MEMBER_START_INDEX || '10001'),
    loginPrefix: (__ENV.MEMBER_LOGIN_PREFIX || 'k6-purchase-member').trim(),
    namePrefix: (__ENV.MEMBER_NAME_PREFIX || 'K6 Purchase Member').trim(),
  };

  let endpoint = endpoints[0];
  let res = null;
  const traces = [];
  for (let i = 0; i < endpoints.length; i += 1) {
    endpoint = endpoints[i];
    res = http.post(
      endpoint,
      JSON.stringify(payload),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Test-Auth-Secret': secretHeader,
        },
        tags: { name: i === 0 ? 'test_auth_bootstrap_members' : 'test_auth_bootstrap_members_fallback' },
      }
    );
    traces.push(`try${i + 1}=${endpoint}:${res.status}`);
    if (res.status === 200) break;
    if (res.status !== 401 && res.status !== 404 && res.status !== 405) break;
  }

  if (!check(res, { 'bootstrap members 200': (r) => r.status === 200 })) {
    fail(
      `bootstrap members failed: ${traces.join(', ')}, finalUrl=${endpoint}, finalStatus=${res.status}, finalBody=${res.body}`
    );
  }

  const json = parseJsonSafe(res.body);
  const members = json?.members;
  if (!Array.isArray(members) || members.length < BUYER_COUNT) {
    fail(`bootstrap members invalid response: body=${res.body}`);
  }
  return members;
}

function issueSellerToken(secretHeader, sellerId) {
  const res = http.post(
    `${API_BASE_URL}/internal/test-auth/seller-token`,
    JSON.stringify({ sellerId }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-Test-Auth-Secret': secretHeader,
      },
      tags: { name: 'test_auth_issue_seller_token' },
    }
  );

  if (!check(res, { 'issue seller token 200': (r) => r.status === 200 })) {
    fail(`issue seller token failed: status=${res.status}, body=${res.body}`);
  }

  const json = parseJsonSafe(res.body);
  const accessToken = json?.accessToken;
  if (!accessToken) {
    fail(`issue seller token invalid response: body=${res.body}`);
  }
  return accessToken;
}

function issueMemberToken(secretHeader, memberId) {
  const endpoints = [
    `${API_BASE_URL}/internal/test-auth/member-token`,
    `${HOST}/internal/test-auth/member-token`,
  ];
  let endpoint = endpoints[0];
  let res = null;
  const traces = [];
  for (let i = 0; i < endpoints.length; i += 1) {
    endpoint = endpoints[i];
    res = http.post(
      endpoint,
      JSON.stringify({ memberId }),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Test-Auth-Secret': secretHeader,
        },
        tags: { name: i === 0 ? 'test_auth_issue_member_token' : 'test_auth_issue_member_token_fallback' },
      }
    );
    traces.push(`try${i + 1}=${endpoint}:${res.status}`);
    if (res.status === 200) break;
    if (res.status !== 401 && res.status !== 404 && res.status !== 405) break;
  }

  if (!check(res, { 'issue member token 200': (r) => r.status === 200 })) {
    fail(
      `issue member token failed: ${traces.join(', ')}, finalUrl=${endpoint}, finalStatus=${res.status}, finalBody=${res.body}`
    );
  }

  const json = parseJsonSafe(res.body);
  if (json?.role && json.role !== 'ROLE_MEMBER') {
    fail(`issue member token role mismatch: expected=ROLE_MEMBER, actual=${json.role}, body=${res.body}`);
  }
  const accessToken = json?.accessToken;
  if (!accessToken) {
    fail(`issue member token invalid response: body=${res.body}`);
  }
  return accessToken;
}

function validateMemberToken(memberToken, index) {
  const res = http.get(`${API_BASE_URL}/my/member-id`, {
    headers: {
      Authorization: `Bearer ${memberToken}`,
    },
    tags: { name: 'validate_member_token' },
  });

  const ok = res.status === 200;
  if (!ok) {
    fail(
      `member token validation failed: idx=${index} status=${res.status} body=${res.body} server=${res.headers?.Server || ''} via=${res.headers?.Via || ''}`
    );
  }
}

function createStockOneProduct(sellerAccessToken) {
  const nonce = `${Date.now()}-${Math.floor(Math.random() * 100000)}`;
  const payload = {
    product_name: `k6-purchase-${nonce}`,
    short_desc: 'k6 concurrency purchase test',
    detail_html: '<p>k6 concurrency purchase test</p>',
    price: PRICE,
    stock_qty: 1,
    cost_price: Math.max(0, PRICE - 1000),
  };

  const createRes = http.post(`${API_BASE_URL}/seller/products`, JSON.stringify(payload), {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${sellerAccessToken}`,
    },
    tags: { name: 'seller_product_create' },
  });

  if (!check(createRes, { 'create product 200': (r) => r.status === 200 })) {
    fail(`create product failed: status=${createRes.status}, body=${createRes.body}`);
  }

  const createBody = parseJsonSafe(createRes.body);
  const productId = createBody?.product_id;
  if (!productId) {
    fail(`create product invalid response: body=${createRes.body}`);
  }

  const readyRes = http.patch(
    `${API_BASE_URL}/seller/products/${productId}/status`,
    JSON.stringify({ status: 'READY' }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${sellerAccessToken}`,
      },
      tags: { name: 'seller_product_status_ready' },
    }
  );
  if (!check(readyRes, { 'product status READY 200': (r) => r.status === 200 })) {
    fail(`status READY failed: status=${readyRes.status}, body=${readyRes.body}`);
  }

  const onSaleRes = http.patch(
    `${API_BASE_URL}/seller/products/${productId}/status`,
    JSON.stringify({ status: 'ON_SALE' }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${sellerAccessToken}`,
      },
      tags: { name: 'seller_product_status_on_sale' },
    }
  );
  if (!check(onSaleRes, { 'product status ON_SALE 200': (r) => r.status === 200 })) {
    fail(`status ON_SALE failed: status=${onSaleRes.status}, body=${onSaleRes.body}`);
  }

  return productId;
}

export function setup() {
  if (BUYER_COUNT <= 0) {
    fail('BUYER_COUNT must be >= 1');
  }
  if (PAYMENT_MODE !== 'create_only' && PAYMENT_MODE !== 'confirm') {
    fail("PAYMENT_MODE must be 'create_only' or 'confirm'");
  }
  if (MEMBER_TOKENS.length > 0 && MEMBER_TOKENS.length < BUYER_COUNT) {
    fail(`MEMBER_TOKENS must include at least BUYER_COUNT(${BUYER_COUNT}) tokens`);
  }

  const useAutoMemberTokens = MEMBER_TOKENS.length === 0;
  if (useAutoMemberTokens && !TEST_AUTH_SECRET) {
    fail('Set TEST_AUTH_SECRET when MEMBER_TOKENS is not provided.');
  }

  authProbe(TEST_AUTH_SECRET);

  if (PRODUCT_ID) {
    if (!useAutoMemberTokens) {
      return { productId: Number(PRODUCT_ID), memberTokens: MEMBER_TOKENS };
    }
    const members = bootstrapMembers(TEST_AUTH_SECRET);
    if (!members.every((m) => m.role === 'ROLE_MEMBER')) {
      fail(
        `bootstrap returned non-member roles. currentRoles=${members.map((m) => m.role).join(',')} (likely server does not support member test-auth bootstrap yet). use MEMBER_TOKENS or deploy member bootstrap/token support.`
      );
    }
    const memberTokens = members
      .slice(0, BUYER_COUNT)
      .map((member) => issueMemberToken(TEST_AUTH_SECRET, member.memberId));
    memberTokens.forEach((token, idx) => validateMemberToken(token, idx));
    return { productId: Number(PRODUCT_ID), memberTokens };
  }

  if (!TEST_AUTH_SECRET) {
    fail('Set PRODUCT_ID or TEST_AUTH_SECRET env var.');
  }

  const members = useAutoMemberTokens ? bootstrapMembers(TEST_AUTH_SECRET) : [];
  if (useAutoMemberTokens && !members.every((m) => m.role === 'ROLE_MEMBER')) {
    fail(
      `bootstrap returned non-member roles. currentRoles=${members.map((m) => m.role).join(',')} (likely server does not support member test-auth bootstrap yet). use MEMBER_TOKENS or deploy member bootstrap/token support.`
    );
  }
  const memberTokens = useAutoMemberTokens
    ? members.slice(0, BUYER_COUNT).map((member) => issueMemberToken(TEST_AUTH_SECRET, member.memberId))
    : MEMBER_TOKENS;
  if (useAutoMemberTokens) {
    memberTokens.forEach((token, idx) => validateMemberToken(token, idx));
  }

  const sellerId = bootstrapSeller(TEST_AUTH_SECRET);
  const sellerAccessToken = issueSellerToken(TEST_AUTH_SECRET, sellerId);
  const productId = createStockOneProduct(sellerAccessToken);
  return { productId, memberTokens };
}

function pickBuyerToken(data) {
  return data.memberTokens[(__VU - 1) % BUYER_COUNT];
}

function createOrder(productId, buyerToken) {
  const payload = {
    items: [
      {
        product_id: productId,
        quantity: 1,
      },
    ],
    receiver: `${RECEIVER_PREFIX}-${__VU}`,
    postcode: POSTCODE,
    addr_detail: `${ADDRESS}-${__VU}`,
    is_default: false,
  };

  return http.post(`${API_BASE_URL}/orders`, JSON.stringify(payload), {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${buyerToken}`,
    },
    tags: { name: 'order_create' },
  });
}

function confirmPayment(orderId, orderAmount, buyerToken) {
  const paymentKey = `${PAYMENT_KEY_PREFIX}-${__VU}-${__ITER}-${Date.now()}`;
  const payload = {
    paymentKey,
    orderId: String(orderId),
    amount: Number(orderAmount),
  };

  return http.post(`${API_BASE_URL}/payments/toss/confirm`, JSON.stringify(payload), {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${buyerToken}`,
    },
    tags: { name: 'payment_confirm' },
  });
}

export default function (data) {
  const productId = data.productId;
  const buyerToken = pickBuyerToken(data);

  const orderRes = createOrder(productId, buyerToken);
  const orderBody = parseJsonSafe(orderRes.body);

  if (orderRes.status === 200 && orderBody?.order_id) {
    ORDER_CREATE_SUCCESS.add(1);

    if (PAYMENT_MODE === 'create_only') {
      FINAL_SUCCESS.add(1);
    } else {
      const confirmRes = confirmPayment(orderBody.order_id, orderBody.order_amount, buyerToken);
      const confirmBody = parseJsonSafe(confirmRes.body);
      const paid = confirmRes.status === 200;

      if (paid) {
        PAYMENT_CONFIRM_SUCCESS.add(1);
        FINAL_SUCCESS.add(1);
      } else {
        PAYMENT_CONFIRM_FAIL.add(1);
        const msg = parseFirstErrorMessage(confirmBody) || confirmRes.body;
        console.error(
          `[payment-confirm-fail] status=${confirmRes.status} orderId=${orderBody.order_id} msg=${msg}`
        );
      }
    }
  } else {
    ORDER_CREATE_FAIL.add(1);
    const msg = parseFirstErrorMessage(orderBody) || orderRes.body;
    if (orderRes.status === 409 && msg.includes('insufficient stock')) {
      INSUFFICIENT_STOCK.add(1);
    } else if (orderRes.status === 401) {
      UNAUTHORIZED.add(1);
      UNAUTHORIZED_401.add(1);
    } else if (orderRes.status === 403) {
      UNAUTHORIZED.add(1);
      FORBIDDEN_403.add(1);
    } else {
      OTHER_FAIL.add(1);
    }

    if (DEBUG_ORDER_FAILURES && orderFailureLogCount < ORDER_FAILURE_LOG_LIMIT) {
      orderFailureLogCount += 1;
      console.error(
        `[order-create-fail] status=${orderRes.status} vu=${__VU} iter=${__ITER} server=${orderRes.headers?.Server || ''} via=${orderRes.headers?.Via || ''} msg=${truncate(msg, 260)} raw=${truncate(orderRes.body, 260)}`
      );
    }
  }

  if (THINK_SECONDS > 0) {
    sleep(THINK_SECONDS);
  }
}
