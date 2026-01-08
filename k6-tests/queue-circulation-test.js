/**
 * Queue 순환 테스트 (Phase 4 - 30만 명 폭주 시나리오)
 *
 * 목적: 30만 명 폭주 상황에서 Active Queue 순환 검증
 * 시나리오: 대기열 진입 → 활성화 대기 → Active Queue 사용 → 제거
 *
 * 테스트 플로우:
 *   1. 대기열 진입 (POST /api/v1/queue/enter)
 *      - Warmup: 10초간 초당 1,000명 (총 10,000명)
 *      - Peak: 60초간 초당 5,000명 (총 300,000명)
 *   2. 활성화 대기 (GET /api/v1/queue/status 폴링, 최대 2분)
 *   3. Active Queue 사용 시뮬레이션 (sleep 5~30초)
 *   4. Queue에서 제거 (DELETE /api/v1/queue/remove)
 *
 * 측정 메트릭:
 *   [클라이언트 측 - k6]
 *   - queue.entry.duration: 대기열 진입 응답 시간
 *   - queue.poll.duration: 폴링 응답 시간
 *   - queue.remove.duration: 제거 응답 시간
 *   - queue.activation.wait.time: 활성화 대기 시간 (WAITING → READY/ACTIVE)
 *   - queue.active.usage.time: Active Queue 사용 시간
 *   - queue.removal.success.rate: 제거 성공률
 *
 *   [서버 측 - Grafana/Prometheus]
 *   - queue.entry.count: 진입 수 (Entry Rate 계산용)
 *   - queue.exit.count: 제거 수 (Exit Rate 계산용)
 *   - queue.active.size: Active Queue 크기
 *   - queue.wait.size: Wait Queue 크기 (폭주 시 급증)
 *
 * 성공 기준:
 *   - 대기열 진입 P95 < 200ms
 *   - 폴링 P95 < 100ms
 *   - 제거 P95 < 100ms
 *   - 활성화 대기 P95 < 6분 (폭주 시 Wait Queue 대기 포함)
 *   - Active Queue max-size 유지 (50,000)
 *   - Queue 순환 안정 (Wait Queue 점진적 소진)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { randomUserId } from './utils/common.js';

// ============================================
// 설정
// ============================================

// Queue Service URL
const BASE_URL_QUEUE = 'http://queue-service:8081';

// 테스트 대상 콘서트 (환경 변수 또는 기본값 '1')
const CONCERT_ID = __ENV.CONCERT_ID || '1';

// ============================================
// K6 옵션
// ============================================

export const options = {
  scenarios: {
    // Phase 1: Warmup (10초간 초당 1000명)
    warmup: {
      executor: 'constant-arrival-rate',
      rate: 1000,
      timeUnit: '1s',
      duration: '10s',
      preAllocatedVUs: 5000,
      maxVUs: 15000,
      startTime: '0s',
      gracefulStop: '15m',
    },
    // Phase 2: Peak - 30만 명 폭주 (60초간 초당 5000명)
    peak: {
      executor: 'constant-arrival-rate',
      rate: 5000,
      timeUnit: '1s',
      duration: '1m',
      preAllocatedVUs: 15000,
      maxVUs: 50000,
      startTime: '10s',
      gracefulStop: '15m',
    },
  },

  // 임계값 (폭주 시나리오에 맞게 조정)
  thresholds: {
    'http_req_duration{step:enter}': ['p(95)<200'],       // 대기열 진입
    'http_req_duration{step:poll}': ['p(95)<100'],        // 폴링
    'http_req_duration{step:remove}': ['p(95)<100'],      // 제거
    'activation_wait_time': ['p(95)<360000'],             // 활성화 6분 이내 (폭주 시 Wait Queue 대기 포함)
    'active_usage_time': ['avg>5000', 'avg<30000'],       // 평균 사용 5~30초
    'queue_removal_success': ['rate>0.99'],               // 제거 성공률 99% 이상
  },

  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ============================================
// 커스텀 메트릭
// ============================================

// 활성화 대기 시간 (WAITING → ACTIVE)
const activationWaitTime = new Trend('activation_wait_time');

// Active Queue 사용 시간
const activeUsageTime = new Trend('active_usage_time');

// Queue 제거 성공률
const queueRemovalSuccess = new Rate('queue_removal_success');

// Queue 제거 실패 카운터
const queueRemovalFailures = new Counter('queue_removal_failures');

// 폴링 실패 카운터 (타임아웃)
const pollingTimeouts = new Counter('polling_timeouts');

// ============================================
// 메인 테스트 함수
// ============================================

export default function () {
  const userId = randomUserId();

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
    'enter: status is 201': (r) => r.status === 201,
    'enter: has position': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.position !== undefined;
      } catch (e) {
        return false;
      }
    },
  });

  if (!enterSuccess) {
    console.error(`Queue entry failed: userId=${userId}, status=${enterResponse.status}`);
    return;
  }

  // ========================================
  // 2. 활성화 대기 (폴링)
  // ========================================
  let activated = false;
  let pollCount = 0;
  const maxPolls = 300;                 // 최대 5분 대기 (폭주 시 Wait Queue 대기 포함)
  const pollInterval = 1;                // 1초마다 폴링
  const pollStartTime = Date.now();

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
      'poll: status is 200': (r) => r.status === 200,
      'poll: has data': (r) => {
        try {
          const body = JSON.parse(r.body);
          return body.data !== null;
        } catch (e) {
          return false;
        }
      },
    });

    if (statusCheck) {
      try {
        const body = JSON.parse(statusResponse.body);
        // READY or ACTIVE 상태면 활성화된 것으로 간주
        if (body.data && (body.data.status === 'READY' || body.data.status === 'ACTIVE')) {
          activated = true;
          const waitTime = Date.now() - pollStartTime;
          activationWaitTime.add(waitTime);
        }
      } catch (e) {
        // JSON 파싱 실패 무시
      }
    }

    pollCount++;
  }

  if (!activated) {
    pollingTimeouts.add(1);
    console.error(`Activation timeout: userId=${userId} after ${maxPolls} seconds (${maxPolls/60} minutes)`);
    return;
  }

  // ========================================
  // 3. Active Queue 사용 시뮬레이션
  // ========================================
  const usageSeconds = randomIntBetween(5, 30);  // 5~30초 랜덤 사용
  sleep(usageSeconds);
  activeUsageTime.add(usageSeconds * 1000);     // ms 단위로 기록

  // ========================================
  // 4. Queue에서 제거
  // ========================================
  const removeParams = {
    tags: { step: 'remove' },
  };

  const removeResponse = http.del(
    `${BASE_URL_QUEUE}/api/v1/queue/remove?concertId=${CONCERT_ID}&userId=${userId}`,
    null,
    removeParams
  );

  const removeSuccess = check(removeResponse, {
    'remove: status is 200': (r) => r.status === 200,
    'remove: result is success': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.result === 'success';
      } catch (e) {
        return false;
      }
    },
  });

  if (removeSuccess) {
    queueRemovalSuccess.add(1);
  } else {
    queueRemovalSuccess.add(0);
    queueRemovalFailures.add(1);
    console.error(`Queue removal failed: userId=${userId}, status=${removeResponse.status}`);
  }
}

// ============================================
// 유틸리티 함수
// ============================================

/**
 * 랜덤 정수 생성 (min ~ max 범위)
 */
function randomIntBetween(min, max) {
  return Math.floor(Math.random() * (max - min + 1) + min);
}

// ============================================
// 테스트 셋업
// ============================================

export function setup() {
  console.log('='.repeat(60));
  console.log('Queue Circulation Test (Phase 4 - 30만 명 폭주)');
  console.log('='.repeat(60));
  console.log(`Target: ${BASE_URL_QUEUE}`);
  console.log(`Concert ID: ${CONCERT_ID}`);
  console.log('');
  console.log('Test Flow:');
  console.log('  1. Enter Queue (POST /api/v1/queue/enter)');
  console.log('  2. Poll Until Active (GET /api/v1/queue/status)');
  console.log('  3. Simulate Usage (sleep 5~30s)');
  console.log('  4. Remove from Queue (DELETE /api/v1/queue/remove)');
  console.log('');
  console.log('Test Config:');
  console.log('  Warmup: 10s, 1,000 users/sec (10,000 users)');
  console.log('  Peak: 60s, 5,000 users/sec (300,000 users)');
  console.log('  Total Duration: 70 seconds');
  console.log('  Expected Users: ~310,000');
  console.log('');
  console.log('Success Criteria:');
  console.log('  - Queue Entry P95 < 200ms');
  console.log('  - Polling P95 < 100ms');
  console.log('  - Removal P95 < 100ms');
  console.log('  - Activation Wait P95 < 6 minutes (폭주 시 Wait Queue 대기 포함)');
  console.log('  - Active Queue Max Size: 50,000');
  console.log('  - Wait Queue 점진적 소진');
  console.log('  - Test Duration: ~15 minutes (gracefulStop)');
  console.log('='.repeat(60));

  // Health check
  const healthResponse = http.get(`${BASE_URL_QUEUE}/actuator/health`);

  if (healthResponse.status !== 200) {
    console.error('Queue Service is not healthy!');
    console.error(`Status: ${healthResponse.status}`);
    throw new Error('Queue Service is not available');
  }

  console.log('✓ Queue Service is healthy');
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
  console.log('Test Completed');
  console.log('='.repeat(60));
  console.log(`Total Duration: ${duration.toFixed(2)}s`);
  console.log('');
  console.log('📊 Check Grafana Dashboard: http://localhost:3000');
  console.log('   - Active Queue Size (should be stable, not increasing)');
  console.log('   - Entry Rate vs Exit Rate (should be similar)');
  console.log('   - Activation Wait Time P95');
  console.log('   - Active Usage Time Average');
  console.log('');
  console.log('📈 Check Prometheus Metrics:');
  console.log('   - rate(queue_entry_count_total[1m])   # Entry Rate');
  console.log('   - rate(queue_exit_count_total[1m])    # Exit Rate');
  console.log('   - queue_active_size                    # Active Queue Size');
  console.log('');
  console.log('Next Steps:');
  console.log('  1. Verify Entry Rate ≈ Exit Rate in Grafana');
  console.log('  2. Ensure Active Queue Size is stable (<50,000)');
  console.log('  3. Check Activation Wait Time P95 < 6 minutes');
  console.log('  4. Verify Wait Queue gradually drains to 0');
  console.log('  5. Document results in PERFORMANCE_TEST_SUMMARY.md');
  console.log('='.repeat(60));
}

// ============================================
// 사용 예시
// ============================================

/**
 * 소규모 테스트 (검증용):
 *   docker-compose -f docker-compose.cluster.yml up -d
 *   docker-compose -f docker-compose.cluster.yml run --rm k6 run /scripts/queue-circulation-test.js \
 *     -e RATE=100 -e DURATION=2m
 *
 * 본 테스트 (2,000 TPS):
 *   docker-compose -f docker-compose.cluster.yml up -d
 *   docker-compose -f docker-compose.cluster.yml run --rm k6 run /scripts/queue-circulation-test.js
 *
 * 결과 확인:
 *   - Grafana: http://localhost:3000
 *   - Active Queue Size 패널에서 순환 안정성 확인
 *   - Entry Rate vs Exit Rate 패널에서 균형 확인
 */
