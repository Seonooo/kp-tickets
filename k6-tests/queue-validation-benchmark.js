/**
 * Queue Service Validation Benchmark
 *
 * 목적: Queue Service /validate 엔드포인트 응답 시간 측정
 *
 * 측정 항목:
 * - P50, P95, P99 응답 시간
 * - 최대 처리 가능 TPS
 * - 오류율
 *
 * 테스트 시나리오:
 * 1. Warmup: 100 req/sec for 10s
 * 2. Low Load: 100 req/sec for 30s (baseline 측정)
 * 3. Medium Load: 500 req/sec for 30s (목표 부하)
 * 4. High Load: 1000 req/sec for 30s (용량 테스트)
 * 5. Peak Load: 2000 req/sec for 30s (한계 테스트)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// ============================================
// Configuration
// ============================================

const QUEUE_URL = __ENV.QUEUE_URL || 'http://queue-service:8081';
const CONCERT_ID = '1';

// Custom Metrics
const validationDuration = new Trend('validation_duration', true);
const validationSuccessRate = new Rate('validation_success_rate');
const validationFailures = new Counter('validation_failures');

// ============================================
// K6 Options
// ============================================

export const options = {
  scenarios: {
    // 1. Warmup
    warmup: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '10s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      startTime: '0s',
      tags: { scenario: 'warmup' },
    },

    // 2. Low Load Baseline
    low_load: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 50,
      maxVUs: 200,
      startTime: '10s',
      tags: { scenario: 'low_load' },
    },

    // 3. Medium Load (목표 부하)
    medium_load: {
      executor: 'constant-arrival-rate',
      rate: 500,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
      startTime: '40s',
      tags: { scenario: 'medium_load' },
    },

    // 4. High Load
    high_load: {
      executor: 'constant-arrival-rate',
      rate: 1000,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 400,
      maxVUs: 2000,
      startTime: '70s',
      tags: { scenario: 'high_load' },
    },

    // 5. Peak Load
    peak_load: {
      executor: 'constant-arrival-rate',
      rate: 2000,
      timeUnit: '1s',
      duration: '30s',
      preAllocatedVUs: 800,
      maxVUs: 3000,
      startTime: '100s',
      tags: { scenario: 'peak_load' },
    },
  },

  thresholds: {
    // 전체 메트릭
    'http_req_duration': ['p(50)<50', 'p(95)<100', 'p(99)<200'],
    'http_req_failed': ['rate<0.01'],  // 1% 이하 실패율

    // 시나리오별 메트릭
    'http_req_duration{scenario:low_load}': ['p(95)<50'],
    'http_req_duration{scenario:medium_load}': ['p(95)<100'],
    'http_req_duration{scenario:high_load}': ['p(95)<200'],
    'http_req_duration{scenario:peak_load}': ['p(95)<500'],
  },
};

// ============================================
// Test Setup
// ============================================

export function setup() {
  console.log('============================================');
  console.log('Queue Service Validation Benchmark');
  console.log('============================================');
  console.log(`Target: ${QUEUE_URL}/api/v1/queue/validate`);
  console.log('');

  // Queue에 토큰 생성 (사전 준비)
  const enterUrl = `${QUEUE_URL}/api/v1/queue/enter`;

  // 1000개 토큰 생성
  console.log('Creating 1000 test tokens...');
  const tokens = [];

  for (let i = 1; i <= 1000; i++) {
    const enterRes = http.post(enterUrl, JSON.stringify({
      concertId: CONCERT_ID,
      userId: i.toString(),
    }), {
      headers: { 'Content-Type': 'application/json' },
    });

    if (enterRes.status === 201) {
      const body = JSON.parse(enterRes.body);
      tokens.push({
        userId: i.toString(),
        token: body.data.token,  // ApiResponse 구조: body.data.token
      });
    }

    if (i % 100 === 0) {
      console.log(`  Created ${i} tokens...`);
    }
  }

  console.log(`Created ${tokens.length} tokens`);
  console.log('');

  return { tokens };
}

// ============================================
// Main Test Function
// ============================================

export default function(data) {
  // 랜덤하게 토큰 선택
  const tokenData = data.tokens[Math.floor(Math.random() * data.tokens.length)];

  const payload = JSON.stringify({
    concertId: CONCERT_ID,
    userId: tokenData.userId,
    token: tokenData.token,
  });

  const params = {
    headers: {
      'Content-Type': 'application/json',
    },
  };

  // Validation 요청
  const startTime = Date.now();
  const res = http.post(`${QUEUE_URL}/api/v1/queue/validate`, payload, params);
  const duration = Date.now() - startTime;

  // Custom Metrics
  validationDuration.add(duration);

  // Checks
  const success = check(res, {
    'validation status 200 or 401': (r) => r.status === 200 || r.status === 401,
  });

  validationSuccessRate.add(success);
  if (!success) {
    validationFailures.add(1);
    console.log(`Validation failed: status=${res.status}, body=${res.body}`);
  }
}

// ============================================
// Teardown
// ============================================

export function teardown(data) {
  console.log('');
  console.log('============================================');
  console.log('Benchmark Complete!');
  console.log('============================================');
  console.log('');
  console.log('📊 Key Metrics:');
  console.log('  - Check validation_duration for P50/P95/P99');
  console.log('  - Compare across scenarios (low/medium/high/peak)');
  console.log('');
  console.log('🎯 Expected Results:');
  console.log('  - P95 < 100ms at 500 req/sec (medium_load)');
  console.log('  - Degradation pattern at higher loads');
  console.log('');
  console.log('📈 Use these values for Bulkhead calculation:');
  console.log('  Bulkhead = Target TPS × P99 Duration (seconds)');
  console.log('');
}
