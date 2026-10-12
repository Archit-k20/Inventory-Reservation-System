import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

http.setResponseCallback(http.expectedStatuses(201, 409));

export const options = {
  vus: Number(__ENV.VUS || 50),
  duration: __ENV.DURATION || '2m',
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'], // 95% of requests must complete below 0.5s, 99% below 1s
    http_req_failed: ['rate<0.01'], // unexpected responses only; stock conflicts are expected
    checks: ['rate==1'],
  },
};

export function setup() {
  if (!__ENV.PRODUCT_ID) throw new Error('Set PRODUCT_ID to a product created for this load test.');
}

export default function () {
  const url = `${__ENV.BASE_URL || 'http://localhost:8080'}/reservations`;
  const payload = JSON.stringify({
    productId: __ENV.PRODUCT_ID,
    userId: 'user-' + randomString(8),
    quantity: 1,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': randomString(16),
    },
  };

  const res = http.post(url, payload, params);

  check(res, {
    'reserved or stock conflict': (r) => r.status === 201 || r.status === 409,
  });

  sleep(1);
}
