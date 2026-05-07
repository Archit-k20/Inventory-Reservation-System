import http from 'k6/http';
import { check, sleep } from 'k6';
import { randomString } from 'https://jslib.k6.io/k6-utils/1.2.0/index.js';

export const options = {
  stages: [
    { duration: '30s', target: 50 }, // Ramp up to 50 users
    { duration: '1m', target: 50 },  // Stay at 50 users for 1 min
    { duration: '30s', target: 0 },  // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500', 'p(99)<1000'], // 95% of requests must complete below 0.5s, 99% below 1s
    http_req_failed: ['rate<0.01'], // less than 1% errors
  },
};

export default function () {
  const url = 'http://localhost:8080/reservations';
  const payload = JSON.stringify({
    productId: '8e6f393b-a6eb-4277-a449-faed76c6432e', // Replace with a valid test product ID
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
    'is status 201': (r) => r.status === 201,
    'is status 409': (r) => r.status === 409, // 409 means insufficient stock, which is an expected business case if running out
  });

  sleep(1);
}
