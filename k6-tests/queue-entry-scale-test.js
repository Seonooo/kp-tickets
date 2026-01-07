/**
 * 30만 명 대기열 Baseline 성능 테스트 (단계적 시나리오)
 *
 * 목적: 30만 명 동시 대기열 진입 시 시스템 성능 측정 (Baseline)
 * 시나리오: 인기 콘서트 티켓 오픈 시 폭발적 유입 (실제 티켓팅 반영)
 *
 * 부하 시나리오:
 *   Phase 1 (워밍업):   0~10초  - TPS 1000  (10,000명 진입)
 *   Phase 2 (피크):    10~70초 - TPS 5000  (300,000명 진입) ← 핵심 측정
 *   Phase 3 (관찰):    70~100초 - TPS 0     (대기열 처리 관찰)
 *   총 시간: 100초 (1분 40초)
 *
 * 테스트 방법:
 *   1. 서비스 시작: docker-compose -f docker-compose.simple-scale.yml up -d
 *   2. K6 실행: docker-compose -f docker-compose.simple-scale.yml run --rm k6 run /scripts/queue-entry-scale-test.js
 *   3. Grafana 모니터링: http://localhost:3000
 *   4. 결과 분석: Wait Queue Size, Throughput, Estimated Wait Time
 *
 * 측정 메트릭:
 *   [클라이언트 측 - k6]
 *   - HTTP 응답 시간 (P95, P99)
 *   - 대기열 진입 성공률
 *   - 에러율
 *
 *   [서버 측 - Grafana/Prometheus]
 *   - queue.wait.size: 대기 중인 인원
 *   - queue.throughput.users_per_second: 초당 처리 속도 (Wait → Active)
 *   - queue.estimated.wait.seconds: 예상 대기 시간
 *   - scheduler.move.duration: 스케줄러 처리 시간
 *   - redis.script.duration: Redis Lua 스크립트 실행 시간
 *
 * 성공 기준:
 *   - 대기열 진입 성공률 > 95%
 *   - HTTP 에러율 < 5%
 *   - P95 응답 시간 < 200ms
 *   - P99 응답 시간 < 500ms
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';
import { randomUserId, validateApiResponse } from './utils/common.js';

// ============================================
// 설정
// ============================================

// Queue Service URL (Docker 네트워크 내부)
const BASE_URL_QUEUE = 'http://queue-service:8081';

// 테스트 대상 콘서트 (concert-1로 통일)
const CONCERT_ID = 'concert-1';

// ============================================
// K6 옵션 - 단계적 시나리오
// ============================================

export const options = {
  // 단계적 시나리오 (실제 티켓팅 상황 반영)
  scenarios: {
    // Phase 1: 워밍업 (0~10초, TPS 1000)
    warmup: {
      executor: 'constant-arrival-rate',
      rate: 1000,                         // TPS: 초당 1000개 요청
      timeUnit: '1s',
      duration: '10s',                    // 10초 동안 10,000명 진입
      preAllocatedVUs: 500,
      maxVUs: 1000,
      startTime: '0s',
      tags: { phase: 'warmup' },
    },

    // Phase 2: 피크 부하 (10~70초, TPS 5000) - 핵심 측정 구간
    peak_load: {
      executor: 'constant-arrival-rate',
      rate: 5000,                         // TPS: 초당 5000개 요청 (실제 티켓팅 수준)
      timeUnit: '1s',
      duration: '60s',                    // 60초 동안 300,000명 진입
      preAllocatedVUs: 2000,              // 메모리 사전 할당
      maxVUs: 3000,                       // 최대 VU (최적값)
      startTime: '10s',                   // 워밍업 후 시작
      gracefulStop: '30s',                // 테스트 후 30초 대기 (스케줄러 관찰)
      tags: { phase: 'peak' },
    },

    // Phase 3: 피크 부하를 90초로 연장 (60초 피크 + 30초 관찰)
    // 70~90초는 새 요청 없이 스케줄러 처리 관찰
  },

  // 임계값 (성능 기준)
  thresholds: {
    // HTTP 요청 지속 시간
    'http_req_duration': [
      'p(95)<200',   // 95%의 요청이 200ms 이내
      'p(99)<500',   // 99%의 요청이 500ms 이내
    ],
    // HTTP 실패율
    'http_req_failed': [
      'rate<0.05',   // 에러율 5% 미만
    ],
    // 성공률
    'queue_entry_success_rate': [
      'rate>0.95',   // 성공률 95% 이상
    ],
  },

  // 결과 출력 설정
  summaryTrendStats: ['min', 'avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

// ============================================
// 커스텀 메트릭
// ============================================

// 대기열 진입 성공률
const queueEntrySuccessRate = new Rate('queue_entry_success_rate');

// 대기열 진입 실패 카운터
const queueEntryFailures = new Counter('queue_entry_failures');

// 대기열 진입 응답 시간 (성공한 요청만)
const queueEntryDuration = new Trend('queue_entry_duration');

// ============================================
// 메인 테스트 함수
// ============================================

export default function () {
  // 고유한 사용자 ID 생성
  const userId = randomUserId();

  // 대기열 진입 요청
  const enterQueuePayload = JSON.stringify({
    concertId: CONCERT_ID,
    userId: userId,
  });

  const enterQueueParams = {
    headers: {
      'Content-Type': 'application/json',
    },
    tags: {
      name: 'EnterQueue',  // 메트릭 태그
    },
  };

  // API 호출
  const enterResponse = http.post(
    `${BASE_URL_QUEUE}/api/v1/queue/enter`,
    enterQueuePayload,
    enterQueueParams
  );

  // 응답 검증
  const enterQueueSuccess = check(enterResponse, {
    'status is 201': (r) => r.status === 201,
    'result is success': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.result === 'success';
      } catch (e) {
        return false;
      }
    },
    'has queue position': (r) => {
      try {
        const body = JSON.parse(r.body);
        return body.data && body.data.position !== undefined;
      } catch (e) {
        return false;
      }
    },
    'response time < 500ms': (r) => r.timings.duration < 500,
  });

  // 메트릭 기록
  queueEntrySuccessRate.add(enterQueueSuccess);
  if (enterQueueSuccess) {
    queueEntryDuration.add(enterResponse.timings.duration);
  } else {
    queueEntryFailures.add(1);
    console.error(`Queue entry failed for userId=${userId}, status=${enterResponse.status}, body=${enterResponse.body}`);
  }

  // 짧은 대기 (다음 반복까지)
  // constant-arrival-rate executor가 자동으로 간격을 조절하므로 sleep 불필요
  // 하지만 너무 빠른 연속 요청을 방지하기 위해 최소 대기
  sleep(0.1);  // 100ms
}

// ============================================
// 테스트 설정 검증 (테스트 시작 전 1회 실행)
// ============================================

export function setup() {
  console.log('='.repeat(60));
  console.log('Queue Entry Scale Test - Staged Scenario');
  console.log('='.repeat(60));
  console.log(`Target: ${BASE_URL_QUEUE}`);
  console.log(`Concert ID: ${CONCERT_ID}`);
  console.log('');
  console.log('Test Scenario:');
  console.log('  Phase 1 (Warmup):   0~10s  - TPS 1000  (10,000 users)');
  console.log('  Phase 2 (Peak):    10~70s  - TPS 5000  (300,000 users) ← CORE');
  console.log('  Phase 3 (Cooldown): 70~100s - TPS 0     (observe processing)');
  console.log('');
  console.log('Total Duration: 100 seconds');
  console.log('Total Expected Users: 310,000');
  console.log('='.repeat(60));

  // Queue Service 연결 확인
  const healthCheckResponse = http.get(`${BASE_URL_QUEUE}/actuator/health`);

  if (healthCheckResponse.status !== 200) {
    console.error('Queue Service is not healthy!');
    console.error(`Status: ${healthCheckResponse.status}`);
    console.error(`Body: ${healthCheckResponse.body}`);
    throw new Error('Queue Service is not available');
  }

  console.log('✓ Queue Service is healthy');
  console.log('='.repeat(60));

  return { startTime: new Date() };
}

// ============================================
// 테스트 종료 후 요약 (테스트 종료 후 1회 실행)
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
  console.log('📈 Check Prometheus: http://localhost:9090');
  console.log('');
  console.log('Next Steps:');
  console.log('  1. Stop services: docker-compose -f docker-compose.simple-scale.yml down');
  console.log('  2. Scale to 2 instances: docker-compose -f docker-compose.simple-scale.yml up --scale queue-service=2');
  console.log('  3. Re-run test: docker-compose -f docker-compose.simple-scale.yml run --rm k6 run /scripts/queue-entry-scale-test.js');
  console.log('  4. Compare results in Grafana');
  console.log('='.repeat(60));
}

// ============================================
// 사용 예시
// ============================================

/**
 * 1개 인스턴스 테스트:
 *   docker-compose -f docker-compose.simple-scale.yml up -d db cache broker redis-exporter core-service queue-service prometheus grafana
 *   docker-compose -f docker-compose.simple-scale.yml run --rm k6 run /scripts/queue-entry-scale-test.js
 *
 * 2개 인스턴스 테스트:
 *   docker-compose -f docker-compose.simple-scale.yml down queue-service
 *   docker-compose -f docker-compose.simple-scale.yml up -d --scale queue-service=2
 *   docker-compose -f docker-compose.simple-scale.yml run --rm k6 run /scripts/queue-entry-scale-test.js
 *
 * 4개 인스턴스 테스트:
 *   docker-compose -f docker-compose.simple-scale.yml down queue-service
 *   docker-compose -f docker-compose.simple-scale.yml up -d --scale queue-service=4
 *   docker-compose -f docker-compose.simple-scale.yml run --rm k6 run /scripts/queue-entry-scale-test.js
 *
 * 결과 비교:
 *   - Grafana: http://localhost:3000 (admin/admin)
 *   - Prometheus: http://localhost:9090
 *   - K6 Summary 출력 확인
 */
