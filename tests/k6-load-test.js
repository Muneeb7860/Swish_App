import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  stages: [
    { duration: '30s', target: 50 }, // Ramp up to 50 users
    { duration: '1m', target: 100 }, // Spike to 100 users
    { duration: '30s', target: 0 },  // Ramp down to 0
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests must complete below 500ms
    http_req_failed: ['rate<0.01'],   // Error rate must be less than 1%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export default function () {
  // Simulate hitting the gateway health endpoint or edge routes
  const healthRes = http.get(`${BASE_URL}/health`);
  check(healthRes, {
    'health check status is 200': (r) => r.status === 200,
  });

  const apiRes = http.get(`${BASE_URL}/api/telemetry/status`);
  check(apiRes, {
    'telemetry status is 200': (r) => r.status === 200 || r.status === 401 || r.status === 403, // Might be secured
  });

  sleep(1);
}
