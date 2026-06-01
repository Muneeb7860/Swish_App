import http from 'k6/http';
import { check, sleep } from 'k6';

// k6 Load Profile: 3-Stage Stress Test
export const options = {
  stages: [
    { duration: '30s', target: 50 },  // Ramp-up to 50 virtual users
    { duration: '1m', target: 200 },  // Stress spike to 200 virtual users
    { duration: '30s', target: 0 },   // Ramp-down to 0
  ],
  thresholds: {
    // 95% of API requests must complete in under 200ms
    http_req_duration: ['p(95)<200'],
    // 99% of API requests must complete in under 500ms
    'http_req_duration{name:Checkout}': ['p(99)<500'],
    // Request failure rate must be under 0.1%
    http_req_failed: ['rate<0.001'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'https://localhost';

export default function () {
  const headers = { 'Content-Type': 'application/json' };

  // ─── Scenario 1: Catalog Browsing (GET /api/customer/catalog) ─────────────
  const browseRes = http.get(`${BASE_URL}/api/customer/catalog`, {
    headers: headers,
    tags: { name: 'BrowseCatalog' }
  });
  check(browseRes, {
    'catalog status is 200': (r) => r.status === 200,
    'catalog returns items': (r) => r.json().length > 0,
  });
  sleep(1);

  // ─── Scenario 2: Order Checkout (POST /api/orders) ────────────────────────
  const checkoutPayload = JSON.stringify({
    customerId: 'CUST-001',
    items: [
      {
        itemId: 'ITEM-001',
        quantity: 2
      }
    ],
    paymentMethod: 'wallet',
    tipAmount: 2.50,
    bagsReturned: 0
  });
  const checkoutRes = http.post(`${BASE_URL}/api/orders`, checkoutPayload, {
    headers: headers,
    tags: { name: 'Checkout' }
  });
  check(checkoutRes, {
    'checkout status is 200': (r) => r.status === 200,
    'order receipt created': (r) => r.json().orderId !== undefined,
  });
  sleep(2);
}
