/**
 * Queue E2E Circulation Test (Phase 4 - E2E)
 *
 * 목적: 실제 예매 플로우에서 Queue 순환 검증
 * 시나리오: 대기열 진입 → 활성화 → 좌석 조회 → 예약 → 결제 → Queue 자동 제거
 *
 * 테스트 플로우:
 *   1. 대기열 진입 (POST /api/v1/queue/enter)
 *   2. 활성화 대기 (GET /api/v1/queue/status 폴링)
 *   3. 좌석 조회 (GET /api/v1/schedules/{id}/seats + Queue Token 검증)
 *   4. 좌석 예약 (POST /api/v1/reservations + Queue Token 검증)
 *   5. 결제 처리 (POST /api/v1/payments)
 *   6. Queue 자동 제거 (Kafka: booking.payment.completed → Queue Service)
 *
 * 측정 메트릭:
 *   [E2E 메트릭]
 *   - e2e.total.duration: 전체 E2E 소요 시간 (진입 → 결제 완료)
 *   - e2e.booking.duration: 예매 단계 소요 시간 (활성화 → 결제 완료)
 *   - queue.activation.wait.time: 활성화 대기 시간
 *
 *   [API 메트릭]
 *   - http_req_duration{step:enter}: 대기열 진입 응답 시간
 *   - http_req_duration{step:poll}: 폴링 응답 시간
 *   - http_req_duration{step:seats}: 좌석 조회 응답 시간
 *   - http_req_duration{step:reserve}: 예약 응답 시간
 *   - http_req_duration{step:payment}: 결제 응답 시간
 *
 *   [성공률 메트릭]
 *   - booking.success.rate: 전체 예매 성공률
 *   - payment.success.rate: 결제 성공률
 *
 * 성공 기준:
 *   - 대기열 진입 P95 < 200ms
 *   - 폴링 P95 < 100ms
 *   - 좌석 조회 P95 < 500ms
 *   - 예약 P95 < 1000ms
 *   - 결제 P95 < 2000ms
 *   - E2E 성공률 > 95%
 *   - Active Queue max-size 유지 (50,000)
 *   - Queue 순환 안정 (Entry Rate ≈ Exit Rate)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { randomIntBetween } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

// ============================================
// 설정
// ============================================

// Service URLs
const BASE_URL_QUEUE = __ENV.QUEUE_URL || 'http://queue-service:8081';
const BASE_URL_CORE = __ENV.CORE_URL || 'http://core-service:8080';

// 테스트 대상
const CONCERT_ID = __ENV.CONCERT_ID || '1';
const SCHEDULE_ID = __ENV.SCHEDULE_ID || '1';

// ============================================
// K6 옵션 (E2E - 규모 축소)
// ============================================

export const options = {
  scenarios: {
    // Warmup: 100/sec for 10s = 1,000 users
    warmup: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '10s',
      preAllocatedVUs: 500,
      maxVUs: 1000,
      startTime: '0s',
      gracefulStop: '5m',
    },
    // Peak: 100/sec for 60s = 6,000 users
    peak: {
      executor: 'constant-arrival-rate',
      rate: 100,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 500,
      maxVUs: 1000,
      startTime: '10s',
      gracefulStop: '5m',
    },
  },

  // E2E 테스트 임계값 (더 관대하게 설정)
  thresholds: {
    'http_req_duration{step:enter}': ['p(95)<200'],       // Queue 진입
    'http_req_duration{step:poll}': ['p(95)<100'],        // Queue 폴링
    'http_req_duration{step:seats}': ['p(95)<500'],       // 좌석 조회
    'http_req_duration{step:reserve}': ['p(95)<1000'],    // 좌석 예약
    'http_req_duration{step:payment}': ['p(95)<2000'],    // 결제 처리
    'activation_wait_time': ['p(95)<120000'],              // 활성화 2분 이내
    'e2e_total_duration': ['p(95)<180000'],                // E2E 전체 3분 이내
    'booking_success_rate': ['rate>0.95'],                 // 예매 성공률 95% 이상
  },

  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ============================================
// 커스텀 메트릭
// ============================================

// 활성화 대기 시간 (WAITING → READY/ACTIVE)
const activationWaitTime = new Trend('activation_wait_time');

// E2E 전체 소요 시간 (진입 → 결제 완료)
const e2eTotalDuration = new Trend('e2e_total_duration');

// 예매 단계 소요 시간 (활성화 → 결제 완료)
const bookingDuration = new Trend('booking_duration');

// 예매 성공률
const bookingSuccessRate = new Rate('booking_success_rate');

// 단계별 실패 카운터
const queueEntryFailures = new Counter('queue_entry_failures');
const activationTimeouts = new Counter('activation_timeouts');
const seatsQueryFailures = new Counter('seats_query_failures');
const reservationFailures = new Counter('reservation_failures');
const paymentFailures = new Counter('payment_failures');

// ============================================
// 유틸리티 함수
// ============================================

function randomUserId() {
  // Core Service expects numeric userId (Long type)
  // Generate unique numeric ID: timestamp + random 4 digits
  return Date.now() * 10000 + randomIntBetween(1, 9999);
}

// ============================================
// 메인 테스트 함수 (E2E)
// ============================================

export default function () {
  const userId = randomUserId().toString();  // Convert to string for consistency
  const e2eStartTime = Date.now();

  // ========================================
  // 1. 대기열 진입
  // ========================================
  const enterPayload = JSON.stringify({
    concertId: CONCERT_ID,
    userId: userId,
  });

  const enterParams = {
    headers: { 'Content-Type': 'application/json' },
    tags: { step: 'enter' },
  };

  const enterResponse = http.post(
    `${BASE_URL_QUEUE}/api/v1/queue/enter`,
    enterPayload,
    enterParams
  );

  const enterSuccess = check(enterResponse, {
    'queue entry: status 201': (r) => r.status === 201,
    'queue entry: has position': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.position !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  if (!enterSuccess) {
    queueEntryFailures.add(1);
    bookingSuccessRate.add(0);
    console.error(`[${userId}] Queue entry failed: status=${enterResponse.status}`);
    return;
  }

  // ========================================
  // 2. 활성화 대기 (폴링)
  // ========================================
  let activated = false;
  let pollCount = 0;
  const maxPolls = 120;  // 최대 2분 대기
  const pollInterval = 1;
  const pollStartTime = Date.now();
  let queueToken = null;

  const pollParams = {
    tags: { step: 'poll' },
  };

  while (!activated && pollCount < maxPolls) {
    sleep(pollInterval);

    const statusResponse = http.get(
      `${BASE_URL_QUEUE}/api/v1/queue/status?concertId=${CONCERT_ID}&userId=${userId}`,
      pollParams
    );

    const statusCheck = check(statusResponse, {
      'queue poll: status 200': (r) => r.status === 200,
    });

    if (statusCheck) {
      try {
        const body = JSON.parse(statusResponse.body);
        if (body.data && (body.data.status === 'READY' || body.data.status === 'ACTIVE')) {
          activated = true;
          queueToken = body.data.token;  // 실제 토큰 추출
          const waitTime = Date.now() - pollStartTime;
          activationWaitTime.add(waitTime);
        }
      } catch (e) {
        // JSON 파싱 실패 무시
      }
    }

    pollCount++;
  }

  if (!activated || !queueToken) {
    activationTimeouts.add(1);
    bookingSuccessRate.add(0);
    console.error(`[${userId}] Activation timeout or no token after ${maxPolls}s`);
    return;
  }

  const bookingStartTime = Date.now();

  // ========================================
  // 3. 좌석 조회
  // ========================================
  const seatsParams = {
    headers: {
      'X-User-Id': userId,
      'X-Queue-Token': queueToken,
    },
    tags: { step: 'seats' },
  };

  const seatsResponse = http.get(
    `${BASE_URL_CORE}/api/v1/schedules/${SCHEDULE_ID}/seats`,
    seatsParams
  );

  const seatsSuccess = check(seatsResponse, {
    'seats query: status 200': (r) => r.status === 200,
    'seats query: has data': (r) => {
      try {
        const body = JSON.parse(r.body);
        return Array.isArray(body) && body.length > 0;
      } catch (e) {
        return false;
      }
    },
  });

  if (!seatsSuccess) {
    seatsQueryFailures.add(1);
    bookingSuccessRate.add(0);
    console.error(`[${userId}] Seats query failed: status=${seatsResponse.status}`);
    return;
  }

  // 사용 가능한 좌석 중 랜덤 선택
  let seatId;
  try {
    const seats = JSON.parse(seatsResponse.body);
    const availableSeats = seats.filter(s => s.status === 'AVAILABLE');
    if (availableSeats.length === 0) {
      console.error(`[${userId}] No available seats`);
      bookingSuccessRate.add(0);
      return;
    }
    seatId = availableSeats[randomIntBetween(0, availableSeats.length - 1)].seatId;
  } catch (e) {
    console.error(`[${userId}] Failed to parse seats: ${e}`);
    bookingSuccessRate.add(0);
    return;
  }

  // ========================================
  // 4. 좌석 예약
  // ========================================
  const reservePayload = JSON.stringify({
    scheduleId: parseInt(SCHEDULE_ID),
    seatId: seatId,
  });

  const reserveParams = {
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': userId,
      'X-Queue-Token': queueToken,
    },
    tags: { step: 'reserve' },
  };

  const reserveResponse = http.post(
    `${BASE_URL_CORE}/api/v1/reservations`,
    reservePayload,
    reserveParams
  );

  const reserveSuccess = check(reserveResponse, {
    'reservation: status 201': (r) => r.status === 201,
    'reservation: has reservationId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.reservationId !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  if (!reserveSuccess) {
    reservationFailures.add(1);
    bookingSuccessRate.add(0);
    console.error(`[${userId}] Reservation failed: status=${reserveResponse.status}`);
    return;
  }

  let reservationId;
  try {
    const body = JSON.parse(reserveResponse.body);
    reservationId = body.data.reservationId;
  } catch (e) {
    console.error(`[${userId}] Failed to parse reservation: ${e}`);
    bookingSuccessRate.add(0);
    return;
  }

  // ========================================
  // 5. 결제 처리
  // ========================================
  const paymentPayload = JSON.stringify({
    reservationId: reservationId,
    userId: userId,
    amount: 50000,  // 고정 금액
    paymentMethod: 'CREDIT_CARD',
    concertId: CONCERT_ID,
  });

  const paymentParams = {
    headers: { 'Content-Type': 'application/json' },
    tags: { step: 'payment' },
  };

  const paymentResponse = http.post(
    `${BASE_URL_CORE}/api/v1/payments`,
    paymentPayload,
    paymentParams
  );

  const paymentSuccess = check(paymentResponse, {
    'payment: status 201': (r) => r.status === 201,
    'payment: has paymentId': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.paymentId !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  if (!paymentSuccess) {
    paymentFailures.add(1);
    bookingSuccessRate.add(0);
    console.error(`[${userId}] Payment failed: status=${paymentResponse.status}`);
    return;
  }

  // ========================================
  // 6. 성공 메트릭 기록
  // ========================================
  bookingSuccessRate.add(1);

  const bookingTime = Date.now() - bookingStartTime;
  bookingDuration.add(bookingTime);

  const e2eTime = Date.now() - e2eStartTime;
  e2eTotalDuration.add(e2eTime);

  // Note: Queue 제거는 Kafka 이벤트를 통해 자동으로 처리됨
  // (Core Service가 payment.completed 이벤트 발행 → Queue Service가 구독하여 제거)
}

// ============================================
// 테스트 셋업
// ============================================

export function setup() {
  console.log('='.repeat(60));
  console.log('Queue E2E Circulation Test (Phase 4)');
  console.log('='.repeat(60));
  console.log(`Queue Service: ${BASE_URL_QUEUE}`);
  console.log(`Core Service: ${BASE_URL_CORE}`);
  console.log(`Concert ID: ${CONCERT_ID}`);
  console.log(`Schedule ID: ${SCHEDULE_ID}`);
  console.log('');
  console.log('Test Flow:');
  console.log('  1. Enter Queue (POST /api/v1/queue/enter)');
  console.log('  2. Poll Until Active (GET /api/v1/queue/status)');
  console.log('  3. Query Seats (GET /api/v1/schedules/{id}/seats)');
  console.log('  4. Reserve Seat (POST /api/v1/reservations)');
  console.log('  5. Process Payment (POST /api/v1/payments)');
  console.log('  6. Auto Queue Removal (Kafka: booking.payment.completed)');
  console.log('');
  console.log('Test Scale:');
  console.log('  Warmup: 10s, 100 users/sec (1,000 users)');
  console.log('  Peak: 60s, 500 users/sec (30,000 users)');
  console.log('  Total: ~31,000 users');
  console.log('');
  console.log('Success Criteria:');
  console.log('  - Queue Entry P95 < 200ms');
  console.log('  - Queue Poll P95 < 100ms');
  console.log('  - Seats Query P95 < 500ms');
  console.log('  - Reservation P95 < 1000ms');
  console.log('  - Payment P95 < 2000ms');
  console.log('  - E2E Success Rate > 95%');
  console.log('  - Active Queue Max: 50,000');
  console.log('='.repeat(60));

  // Health checks
  const queueHealth = http.get(`${BASE_URL_QUEUE}/actuator/health`);
  const coreHealth = http.get(`${BASE_URL_CORE}/actuator/health`);

  if (queueHealth.status !== 200) {
    console.error('Queue Service is not healthy!');
    throw new Error('Queue Service unavailable');
  }

  if (coreHealth.status !== 200) {
    console.error('Core Service is not healthy!');
    throw new Error('Core Service unavailable');
  }

  console.log('✓ Queue Service is healthy');
  console.log('✓ Core Service is healthy');
  console.log('');

  // Initialize test data
  console.log('Initializing test data...');
  const initDataResponse = http.post(`${BASE_URL_CORE}/api/admin/test-data/init`);

  if (initDataResponse.status !== 200) {
    console.error('Failed to initialize test data!');
    console.error(`Status: ${initDataResponse.status}`);
    console.error(`Response: ${initDataResponse.body}`);
    throw new Error('Test data initialization failed');
  }

  const initData = JSON.parse(initDataResponse.body);
  console.log(`✓ Test data initialized in ${initData.duration_ms}ms`);
  console.log(`  - Concerts: ${initData.data.concerts}`);
  console.log(`  - Schedules: ${initData.data.schedules}`);
  console.log(`  - Seats: ${initData.data.seats}`);
  console.log('='.repeat(60));

  return { startTime: new Date() };
}

// ============================================
// 테스트 종료
// ============================================

export function teardown(data) {
  const endTime = new Date();
  const duration = (endTime - data.startTime) / 1000;

  console.log('='.repeat(60));
  console.log('E2E Test Completed');
  console.log('='.repeat(60));
  console.log(`Total Duration: ${duration.toFixed(2)}s`);
  console.log('');
  console.log('📊 Check Grafana Dashboard: http://localhost:3000');
  console.log('   - Active Queue Size (stable, < 50,000)');
  console.log('   - Wait Queue Size (draining to 0)');
  console.log('   - Entry Rate vs Exit Rate (balanced)');
  console.log('   - E2E Success Rate');
  console.log('');
  console.log('Next Steps:');
  console.log('  1. Verify E2E Success Rate > 95%');
  console.log('  2. Check Queue circulation (Entry ≈ Exit)');
  console.log('  3. Verify Kafka event processing (payment → queue removal)');
  console.log('  4. Document results in PERFORMANCE_TEST_SUMMARY.md');
  console.log('='.repeat(60));
}
