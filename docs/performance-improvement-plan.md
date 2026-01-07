# 대기열 시스템 성능 개선 실행 계획

> **📋 최신 종합 보고서**: [PERFORMANCE_TEST_SUMMARY.md](./PERFORMANCE_TEST_SUMMARY.md)
> Phase 1~3 완료 결과 및 Phase 4 상세 계획은 위 문서 참조

## 목표

**30만 명 대기열 동시 처리 시 성능 최적화**
- 처리 속도(Throughput) 최대화
- 사용자 대기 시간 최소화
- 스케일 아웃 효율성 검증
- 정량적 근거 기반 성능 개선 달성

---

## 🎯 현재 상태 (2025-12-26)

### Phase 1~3 완료 ✅

| Phase | 상태 | 주요 성과 |
|-------|------|----------|
| **Phase 1** | ✅ 완료 | Baseline 측정, Lua 스크립트 오류 수정 |
| **Phase 2** | ✅ 완료 | 수평 확장 (2 instances), Redis 병목 확인 |
| **Phase 3-2** | ✅ 완료 | Lua 스크립트 최적화, 응답시간 -38.7% |
| **Phase 3-3** | ✅ 완료 | Redis Cluster, P95/P99 목표 달성 |

### 최종 달성 지표

| 지표 | 목표 | 달성 | 상태 |
|------|------|------|------|
| **P95** | < 200ms | 130.73ms | ✅ |
| **P99** | < 500ms | 356.48ms | ✅ |
| **TPS** | 5,000 | 4,406.2 | ⚠️ 88.1% |
| **성공률** | > 95% | 99.64% | ✅ |

**총 개선율** (Phase 1 → Phase 3-3):
- 평균 응답시간: **-33.3%**
- P95: **-68.8%**
- P99: **-45.2%**

---

## 🚀 다음 단계: Phase 4 (Queue 순환 테스트) - 필수

### 왜 Phase 4가 필요한가?

**프로젝트 범위**: "대기열 시스템" 성능 최적화
```
✅ Queue Service 성능 (핵심)
❌ Core Service 성능 (별도 관심사)
❌ DB 성능 (별도 관심사)

→ Queue Service만 집중 검증
```

**현재까지 테스트한 것**: Queue Entry만 (대기열 진입)
```
POST /api/v1/queue/enter → ✅ 검증 완료
Wait Queue → Active Queue → ✅ 검증 완료
```

**테스트 안 한 것**: Queue 순환
```
Active Queue 순환 (진입 vs 제거 속도) → ❌ 미검증
토큰 라이프사이클 (사용 → 제거) → ❌ 미검증
폴링 성능 → ❌ 미검증
```

### Phase 4 핵심 목표

1. **Active Queue 순환 검증** (가장 중요!)
   - Entry Rate (진입 속도): 2,000명/초 (검증 완료)
   - Exit Rate (제거 속도): ???명/초 (**미검증**)
   - 목표: Exit Rate >= Entry Rate (안정적 순환)

2. **토큰 라이프사이클 검증**
   - 활성화 대기 시간: P95 < 30초
   - Active Queue 사용 시간: 10~20초
   - 제거 성공률: > 99%

3. **폴링 성능 측정**
   - 동시 폴링 요청 처리
   - 폴링 응답시간: P95 < 100ms

---

## Phase 4 실행 계획 요약

자세한 내용은 [PERFORMANCE_TEST_SUMMARY.md](./PERFORMANCE_TEST_SUMMARY.md) 참조

### 1단계: 환경 준비
- Active Queue max-size: 50,000으로 복원
- Queue 제거 API 추가 (DELETE /queue/token)
- Exit Rate 메트릭 구현

### 2단계: Queue 순환 K6 스크립트 작성
- 진입 → 폴링 → 사용 → 제거 플로우 구현
- 각 단계별 메트릭 수집
- threshold 설정

### 3단계: Grafana 대시보드 업데이트
- Active Queue 순환 패널 추가
- Entry Rate vs Exit Rate 비교
- 활성화 대기 시간 차트

### 4단계: 테스트 실행
- 소규모 테스트: 100 VU, 2분
- 본 테스트: 2,000 TPS, 3분
- Active Queue 순환 확인

### 5단계: 결과 분석 및 최적화
- Entry/Exit Rate 균형 확인
- Active Queue 크기 추이 분석
- 발견된 병목 해결

---

## Phase 5: Core Service 성능 개선 (필수, Phase 4 이후)

**왜 이 순서인가?**
```
Phase 4 완료 → Queue Service 검증 완료
Phase 5 → Core Service 최적화 (좌석 조회/예약/결제)
Phase 6 → QA E2E 구축 (안정화된 상태 기준)
```

**Phase 6 (QA E2E) 전에 완료해야 하는 이유**:
- Core Service API 변경 완료 후 테스트 작성
- API 안정화 후 QA E2E 구축하면 재작업 없음
- 성능 최적화 완료 상태를 회귀 테스트 기준선으로 설정

**최적화 대상**:
- 좌석 조회 API: P95 < 500ms
- 좌석 예약 API: P95 < 1초
- 결제 완료 API: P95 < 2초
- DB 쿼리 최적화, 캐싱 전략, Connection Pool 튜닝

자세한 내용: [PERFORMANCE_TEST_SUMMARY.md - Phase 5](./PERFORMANCE_TEST_SUMMARY.md#phase-5-core-service-성능-개선-필수-queue-이후)

---

## Phase 6: QA E2E 자동화 구축 (필수, Phase 5 이후)

**왜 이 순서인가?**
```
Phase 4 (Queue) ✅ + Phase 5 (Core) ✅
  ↓
Phase 6: QA E2E 구축 (모든 성능 개선 완료 후)
  ↓
"완료 선언" 역할 - 회귀 테스트 기준선 확립
```

**핵심 원칙**:
- 모든 성능 최적화 완료 후 QA E2E 구축
- 안정화된 시스템을 기준으로 회귀 테스트 설정
- Core API 변경 완료 후 테스트 작성하여 재작업 방지

**구현 내용**:
- 새로운 Gradle 모듈: `qa-e2e-tests`
- Cucumber BDD 시나리오 (10+ 시나리오)
- TestContainers (Redis, PostgreSQL)
- CI/CD 파이프라인 통합 (GitHub Actions)

자세한 내용: [PERFORMANCE_TEST_SUMMARY.md - Phase 6](./PERFORMANCE_TEST_SUMMARY.md#phase-6-qa-e2e-자동화-구축-필수-모든-성능-개선-완료-후)

---

## Phase 7: 프로덕션 배포 (최종)

**왜 마지막 단계인가?**
```
Phase 4: Queue 검증 ✅
Phase 5: Core 최적화 ✅
Phase 6: QA E2E 기준선 ✅
  ↓
Phase 7: 프로덕션 배포 (모든 준비 완료)
```

**AWS 인프라**:
- ElastiCache Redis Cluster (3 Master + 3 Replica)
- ECS Fargate (Queue Service + Core Service, Auto Scaling)
- RDS PostgreSQL Multi-AZ
- ALB, Route 53, CloudWatch 설정
- Blue-Green/Canary 배포 파이프라인

**예상 성능**:
- TPS: 15,000~20,000 (로컬 대비 +240~354%)
- P95: < 50ms (로컬 대비 -61.5%)
- P99: < 150ms (로컬 대비 -57.9%)

자세한 내용: [PERFORMANCE_TEST_SUMMARY.md - Phase 7](./PERFORMANCE_TEST_SUMMARY.md#phase-7-프로덕션-배포-최종)

---

## 현재 상태 (완료된 작업)

### ✅ 기본 모니터링 메트릭 구현

#### 1. 스케줄러 성능 메트릭
- **위치**: `queue-service/src/main/java/personal/ai/queue/adapter/scheduler/QueueScheduler.java`
- **구현 내용**:
  ```java
  - scheduler.move.duration (Timer)
    → Wait → Active 전환 소요 시간

  - scheduler.move.users (Counter)
    → 이동된 사용자 수

  - scheduler.cleanup.removed (Counter)
    → 삭제된 만료 토큰 수
  ```

#### 2. Redis 성능 메트릭
- **위치**: `queue-service/src/main/java/personal/ai/queue/adapter/out/redis/RedisLuaScriptExecutor.java`
- **구현 내용**:
  ```java
  - redis.script.duration (Timer, script별 tag)
    → move_to_active_queue
    → remove_expired_tokens
    → activate_token
  ```

#### 3. 대기열 상태 메트릭
- **위치**: `queue-service/src/main/java/personal/ai/queue/application/service/QueueSchedulerService.java`
- **구현 내용**:
  ```java
  - queue.active.size (Gauge, concert별)
    → Active Queue 현재 크기
  ```

#### 4. Grafana 대시보드
- **위치**: `monitoring/grafana-dashboard-application.json`
- **패널**:
  1. Scheduler Move Duration (Wait → Active)
  2. Redis Lua Script Execution Time
  3. Active Queue Size (Gauge)
  4. Scheduler Throughput (Users Moved/sec)
  5. Cleanup Rate (Expired Tokens/sec)

---

## 추가 구현 사항

### Phase 1: 30만 명 시나리오 메트릭 추가

#### 1-1. Wait Queue Size 측정
**목적**: 30만 명 대기열 규모 추적

**구현 위치**: `QueueSchedulerService.java` 또는 `QueueScheduler.java`

```java
// QueueSchedulerService.moveWaitingToActive() 메서드에 추가
Long waitQueueSize = queueRepository.getWaitQueueSize(concertId);

Gauge.builder("queue.wait.size", waitQueueSize, Long::doubleValue)
    .tag("concert_id", concertId)
    .description("Number of users waiting in wait queue")
    .register(meterRegistry);
```

**Grafana 패널**:
```json
{
  "title": "Wait Queue Size (대기 중인 인원)",
  "expr": "queue_wait_size{service=\"queue-service\"}",
  "threshold": {
    "yellow": 100000,
    "red": 250000
  }
}
```

#### 1-2. Throughput (처리 속도) 측정
**목적**: 초당 처리 인원 계산 (30만 명 소진 시간 예측)

**구현 위치**: `QueueScheduler.java`

```java
// moveWaitingUsersToActive() 메서드에서
Timer.Sample sample = Timer.start(meterRegistry);
int moved = moveToActiveQueueUseCase.moveWaitingToActive(concertId);
long durationMs = sample.stop(...);

// 처리 속도 계산 (초당 인원)
double throughput = moved / (durationMs / 1000.0);

Gauge.builder("queue.throughput.users_per_second", () -> throughput)
    .tag("concert_id", concertId)
    .description("Users processed per second (Wait → Active)")
    .register(meterRegistry);
```

**Grafana 패널**:
```json
{
  "title": "Processing Throughput (초당 처리 인원)",
  "expr": "queue_throughput_users_per_second{service=\"queue-service\"}",
  "yAxisLabel": "Users/sec"
}
```

#### 1-3. Estimated Wait Time (예상 대기 시간)
**목적**: 사용자 경험 측정

**구현 위치**: `QueueScheduler.java`

```java
// 예상 대기 시간 계산
double estimatedWaitSeconds = waitQueueSize / Math.max(throughput, 1.0);

Gauge.builder("queue.estimated.wait.seconds", () -> estimatedWaitSeconds)
    .tag("concert_id", concertId)
    .description("Estimated wait time for last user in queue")
    .register(meterRegistry);
```

**Grafana 패널**:
```json
{
  "title": "Estimated Wait Time (예상 대기 시간)",
  "expr": "queue_estimated_wait_seconds{service=\"queue-service\"} / 60",
  "unit": "minutes",
  "threshold": {
    "yellow": 5,
    "red": 10
  }
}
```

---

### Phase 2: 스케일 아웃 메트릭 추가

#### 2-1. 분산 락 실패 카운트
**목적**: 인스턴스 간 경합 측정

**구현 위치**: `QueueScheduler.java`

```java
// moveWaitingUsersToActive() 메서드에서
for (String concertId : concertIds) {
    // 락 획득 시도
    if (!schedulerLockPort.tryAcquire(MOVE_SCHEDULER, concertId)) {
        // 실패 카운트 추가
        Counter.builder("scheduler.lock.acquire.failures")
                .tag("scheduler_type", MOVE_SCHEDULER)
                .tag("concert_id", concertId)
                .description("Number of lock acquisition failures (another instance processing)")
                .register(meterRegistry)
                .increment();

        log.debug("Skipping concertId={} (another instance is processing)", concertId);
        continue;
    }
    // ...
}
```

**Grafana 패널**:
```json
{
  "title": "Lock Contention (락 경합 비율)",
  "expr": "rate(scheduler_lock_acquire_failures_total[1m]) / (rate(scheduler_lock_acquire_failures_total[1m]) + rate(scheduler_move_duration_seconds_count[1m])) * 100",
  "unit": "percent",
  "description": "인스턴스 간 락 경합으로 인한 처리 스킵 비율"
}
```

#### 2-2. 콘서트 처리 분산
**목적**: 인스턴스별 부하 분산 확인

**구현 위치**: `QueueScheduler.java`

```java
// moveWaitingUsersToActive() 시작 부분에
Gauge.builder("scheduler.concerts.count", concertIds, List::size)
    .tag("scheduler_type", MOVE_SCHEDULER)
    .description("Number of concerts processed by this scheduler instance")
    .register(meterRegistry);
```

#### 2-3. 인스턴스 식별 태그 추가
**목적**: Grafana에서 인스턴스별 비교

**구현 위치**: `queue-service/src/main/resources/application.yml`

```yaml
management:
  metrics:
    tags:
      instance: ${INSTANCE_ID:instance-1}  # 환경변수로 주입
      service: queue-service
```

**Docker Compose 설정 예시**:
```yaml
# docker-compose.cluster.yml
services:
  queue-service-1:
    environment:
      - INSTANCE_ID=instance-1

  queue-service-2:
    environment:
      - INSTANCE_ID=instance-2

  queue-service-3:
    environment:
      - INSTANCE_ID=instance-3
```

**Grafana 쿼리 예시**:
```promql
# 인스턴스별 처리량 비교
sum by (instance) (rate(scheduler_move_users_total[1m]))

# 인스턴스별 락 실패율
sum by (instance) (rate(scheduler_lock_acquire_failures_total[1m]))
```

---

## 부하 테스트 계획 (단계별 접근)

### 전체 로드맵

```
Phase 1: Queue Entry 성능 측정 (대기열 진입만)
  ↓
Phase 2: E2E 플로우 테스트 (전체 예매 시나리오)
  ↓
Phase 3: 스케일 아웃 비교 (1개 → 2개 → 4개 인스턴스)
```

---

### Phase 1: Queue Entry 성능 측정

**목적**: 대기열 진입 성능만 집중 측정

**테스트 시나리오**:
```javascript
// k6-tests/queue-entry-scale-test.js
Phase 1 (워밍업):   0~10초  - TPS 1000  (10,000명)
Phase 2 (피크):    10~70초 - TPS 5000  (300,000명) ← 핵심
Phase 3 (관찰):    70~100초 - TPS 0     (처리 관찰)
```

**환경 설정**:
- Queue Service: 1개 인스턴스
- Redis: 단일 인스턴스
- **Active Queue Max Size: 310,000** (전체 수용 가능하도록)

**측정 지표**:
- ✅ HTTP 응답 시간 (P95, P99)
- ✅ 대기열 진입 성공률
- ✅ queue.wait.size (대기 인원)
- ✅ queue.throughput.users_per_second (Wait → Active 처리 속도)
- ✅ scheduler.move.duration (스케줄러 성능)
- ✅ redis.script.duration (Redis 성능)

**성공 기준**:
- 대기열 진입 성공률 > 95%
- P95 응답 시간 < 200ms
- P99 응답 시간 < 500ms

---

### Phase 2: E2E 플로우 테스트

**목적**: 실제 사용자 시나리오 반영 (대기열 → 조회 → 예약 → 결제)

**테스트 시나리오**:
```javascript
// k6-tests/e2e-booking-test.js
export default function () {
  // 1. 대기열 진입
  const token = enterQueue(concertId, userId);

  // 2. 폴링 (활성화 대기)
  const activated = pollUntilActive(token);

  if (activated) {
    // 3. 좌석 조회
    const seats = getAvailableSeats(token);

    // 4. 좌석 예약
    reserveSeat(token, seatId);

    // 5. 결제 완료
    payment(token, reservationId);
    // → Active Queue에서 자연스럽게 제거됨
  }
}
```

**환경 설정**:
- Queue Service: 1개 인스턴스
- Core Service: 1개 인스턴스
- Redis: 단일 인스턴스
- MySQL: 콘서트/좌석 데이터 사전 준비
- **Active Queue Max Size: 50,000** (현실적 크기)

**부하**:
```javascript
// 동시 사용자 점진적 증가
stages: [
  { duration: '30s', target: 500 },   // 500명 동시 E2E
  { duration: '60s', target: 1000 },  // 1000명 유지
  { duration: '30s', target: 0 },     // 종료
]
```

**측정 지표**:
- ✅ E2E 완료 시간 (진입 → 결제까지)
- ✅ 각 단계별 응답 시간
- ✅ Active Queue 순환율 (진입 → 제거)
- ✅ 전체 시스템 병목 지점
- ✅ DB 커넥션 풀, Redis 성능

**성공 기준**:
- E2E 완료 성공률 > 90%
- 평균 E2E 시간 < 60초
- Active Queue 정상 순환 확인

---

### Phase 3: 스케일 아웃 비교

**목적**: 인스턴스 증설 효과 측정

**테스트 시나리오**:
- Phase 2의 E2E 테스트를 1개 → 2개 → 4개 인스턴스로 반복

**환경 설정**:
```yaml
# docker-compose.simple-scale.yml
1개 인스턴스: up
2개 인스턴스: up --scale queue-service=2
4개 인스턴스: up --scale queue-service=4
```

**측정 지표**:
- ✅ 인스턴스별 처리량 분산
- ✅ scheduler.lock.acquire.failures (락 경합)
- ✅ 스케일 효율성 (선형 증가 확인)
- ✅ E2E 완료 시간 개선율

**목표**:
- 2배 증설 시 처리량 1.8배 이상
- 4배 증설 시 처리량 3.5배 이상
- 락 경합률 < 10%

---

### 테스트 환경

#### Redis 구성
```
Phase 1, 2: 단일 Redis (docker-compose.simple-scale.yml)
Phase 3 (옵션): Redis Cluster (docker-compose.cluster.yml)
```

#### Queue Service 인스턴스
```
Phase 1, 2: 1개 인스턴스
Phase 3: 1개 → 2개 → 4개 증설
```

---

## TPS 및 성능 테스트 개념 정리

### 1. TPS (Transactions Per Second)

**정의**: 초당 처리하는 트랜잭션(요청) 수

```
TPS = 완료된 요청 수 / 시간(초)
```

**예시**:
- 60초 동안 3000개 요청 완료 → TPS = 3000/60 = 50 TPS

---

### 2. 혼동하기 쉬운 개념 비교

#### TPS vs Throughput (처리량)

| 개념 | TPS | Throughput |
|------|-----|------------|
| **관점** | 클라이언트 → 서버 | 서버 내부 처리 |
| **의미** | 초당 **보내는** 요청 수 | 초당 **처리한** 작업 수 |
| **예시** | k6가 초당 5000개 요청 전송 | Wait → Active로 2000명/초 이동 |
| **측정** | k6 설정: `rate: 5000` | `queue.throughput.users_per_second` |

**실제 예시**:
```
TPS 5000으로 요청 전송
→ 서버 실제 처리: Throughput 2000 users/sec
→ 나머지 3000명/초는 Wait Queue에 쌓임
→ 이상적: TPS = Throughput (들어오는 속도 = 처리 속도)
→ 병목: TPS > Throughput (대기열 증가)
```

#### TPS vs Concurrency (동시성)

**TPS**: 시간당 처리량 (속도 개념)
**Concurrency**: 동시에 처리 중인 개수 (크기 개념)

**공식**:
```
Concurrency = TPS × 평균 응답 시간(Latency)
```

**예시**:
```
Case 1: TPS 1000, Latency 1초
  → Concurrency = 1000 (동시에 1000개 처리 중)

Case 2: TPS 1000, Latency 0.1초
  → Concurrency = 100 (동시에 100개만 처리 중)
```

---

### 3. 관련 핵심 개념

#### (1) Latency (지연 시간)

- 요청 전송 → 응답 수신까지 소요 시간
- 단위: ms (밀리초)
- 예: Latency 100ms = 요청 하나 처리에 0.1초

#### (2) Response Time (응답 시간)

- Latency와 거의 동일
- 사용자 체감 시간

#### (3) VU (Virtual Users)

- k6에서 사용하는 가상 사용자 수
- TPS를 생성하는 주체

**k6 설정 예시**:
```javascript
{
  rate: 5000,           // TPS 5000
  preAllocatedVUs: 1000,
  maxVUs: 2000,
}
```

**VU와 TPS 관계**:
```
각 VU가 1초에 1번 요청 → TPS = VU 수
각 VU가 1초에 5번 요청 → TPS = VU × 5
```

#### (4) Arrival Rate (도착률)

- 단위 시간당 요청 도착 속도
- TPS와 사실상 동일한 개념
- k6의 `constant-arrival-rate`: 일정한 속도로 요청 생성

---

### 4. 우리 프로젝트에 적용

#### 시스템 플로우

```
┌─────────────────────────────────────────────┐
│ 클라이언트 (k6)                              │
│ TPS 5000으로 요청 전송                       │
│ 60초 동안 → 총 300,000개 요청               │
└─────────────┬───────────────────────────────┘
              │ HTTP POST /api/v1/queue/enter
              ↓
┌─────────────────────────────────────────────┐
│ Queue Service (서버)                        │
│ - 요청 받아서 Wait Queue에 추가              │
│ - 처리 시간: 50ms (latency)                 │
│ - 초당 처리 가능: ?                          │
└─────────────┬───────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────┐
│ Redis (Wait Queue)                          │
│ - 5000명/초 씩 쌓임                          │
│ - 60초 후 총 300,000명 대기                 │
└─────────────┬───────────────────────────────┘
              │
              ↓ (Scheduler가 이동)
┌─────────────────────────────────────────────┐
│ Redis (Active Queue)                        │
│ - Throughput: 2000 users/sec로 이동         │
│ - 300,000명 모두 이동 시간: 150초           │
└─────────────────────────────────────────────┘
```

#### 숫자로 이해하기

**TPS 5000 부하 진입 과정** (요청 도착):
```
t=0초:   TPS 5000 → Wait Queue: 0명
t=1초:   TPS 5000 → Wait Queue: 5,000명
t=10초:  TPS 5000 → Wait Queue: 50,000명
t=60초:  TPS 5000 → Wait Queue: 300,000명 ✅ 도착 완료
```

**Throughput 2000 처리 과정** (Wait → Active 이동):
```
t=0초:   Wait: 0명,      Active: 0명
t=1초:   Wait: 3,000명,  Active: 2,000명  (5000 도착 - 2000 처리)
t=10초:  Wait: 30,000명, Active: 20,000명
t=60초:  Wait: 180,000명, Active: 120,000명 (요청 도착 종료)
t=150초: Wait: 0명,      Active: 300,000명 ✅ 모두 처리 완료
```

**결과 분석**:
- 요청 도착 완료: 60초
- 전체 처리 완료: 150초
- 평균 대기 시간: (0 + 150) / 2 = 75초
- **병목**: Throughput(2000) < TPS(5000) → Wait Queue 증가

---

### 5. 성능 테스트에서 TPS를 고정하는 이유

#### 코드 최적화 효과 측정

**최적화 전**:
```
TPS 5000 (고정) → Throughput 2000 → Wait Time 150초
```

**최적화 후**:
```
TPS 5000 (고정) → Throughput 4000 → Wait Time 75초
```

**결과**: 50% 개선! (동일 부하에서 처리 성능 비교 가능)

#### 만약 TPS를 가변으로 하면?

```
테스트1: TPS 2000 → Throughput 2000
테스트2: TPS 5000 → Throughput 2500
```

**문제점**:
- Throughput이 올라간 이유가 코드 개선? ❌
- 단순히 부하가 더 걸려서? ⭕
- **비교 불가능!**

---

### 6. 핵심 공식 정리

```
1. TPS = 완료된 요청 수 / 시간

2. Throughput = 처리된 작업 수 / 시간

3. Concurrency = TPS × 평균 Latency

4. 이상적: TPS = Throughput (들어오는 속도 = 처리 속도)

5. 병목: TPS > Throughput (들어오는 속도 > 처리 속도 → 대기열 증가)

6. Wait Time = Queue Size / Throughput
```

---

### 7. 시스템 상태 판단 기준

#### 좋은 시스템 ✅

```
TPS 5000 부하 테스트 시:
  - Throughput: 5000 users/sec 유지
  - Latency: 100ms 이하
  - Error Rate: 0%
  - Wait Queue Size: 증가하지 않음
```

#### 병목이 있는 시스템 ⚠️

```
TPS 5000 부하 테스트 시:
  - Throughput: 2000 users/sec만 처리
  - Latency: 5000ms (5초)
  - Error Rate: 10% (타임아웃)
  - Wait Queue Size: 계속 증가
```

#### 개선 전략

**병목 발견 시**:
1. **메트릭 분석**:
   - `redis.script.duration` 높음 → Redis 최적화
   - `scheduler.move.duration` 높음 → Scheduler 로직 개선
   - `lock.failures` 높음 → 분산 락 경합 해결

2. **코드 최적화** (수직적):
   - Lua 스크립트 최적화
   - Connection Pool 튜닝
   - Batch Size 조정

3. **스케일 아웃** (수평적):
   - 인스턴스 증설 (1 → 3)
   - Throughput 선형 증가 기대 (2000 → 6000)

---

### 부하 테스트 TPS/Duration 설정 근거

#### 프로젝트 시나리오: 30만 명 동시 대기열 진입

**실제 티켓팅 상황 가정**:
- 인기 콘서트 티켓 오픈 시각
- 대부분의 사용자가 **초기 30~60초 내** 폭발적으로 진입
- 실제 사례: 멜론티켓, 인터파크 등 대형 공연 오픈 시

#### TPS 계산 근거

**30만 명이 60초 동안 진입하는 경우**:
```
TPS = 300,000명 / 60초 = 5,000 TPS
```

**30만 명이 30초 동안 진입하는 경우 (극한 시나리오)**:
```
TPS = 300,000명 / 30초 = 10,000 TPS
```

**실제 티켓팅 트래픽 패턴**:
- 오픈 직후 **0~30초**: 초당 8,000~10,000명 진입 (피크)
- **30~60초**: 초당 3,000~5,000명 진입 (유지)
- 60초 이후: 초당 500~1,000명 (점진적 감소)

**결론**: **TPS 5,000** 설정이 실제 평균 부하를 반영

---

#### 접근 방식 비교

##### Approach A: TPS 고정 + Duration 가변 ✅ 권장

**설정**:
```javascript
// 목표 사용자 수에 따라 duration만 조정 (TPS 5000 고정)
10,000명:  TPS 5000 × 2초
100,000명: TPS 5000 × 20초
300,000명: TPS 5000 × 60초 (1분)
```

**장점**:
- ✅ **동일한 부하 강도**로 시스템 반응 비교 가능
- ✅ TPS가 일정하므로 **초당 처리량(Throughput)** 메트릭 비교 용이
- ✅ 실제 티켓팅 시나리오와 유사 (일정 속도로 폭발적 유입)
- ✅ 병목 지점 파악 용이 (동일 부하에서 규모만 증가)
- ✅ 코드 최적화 전후 비교 시 공정한 조건 유지

**단점**:
- ❌ 테스트 실행 시간이 규모에 비례 증가
- ❌ 10k 규모 테스트는 2초로 너무 짧음 (초기화 부하 영향)

**적합한 경우**:
- **코드 최적화 효과 측정** (동일 부하에서 처리 성능 비교)
- **대규모 부하 안정성 테스트**
- **현재 프로젝트 목적에 적합** ✅

---

##### Approach B: Duration 고정 + TPS 가변

**설정**:
```javascript
// 1분(60초) 고정, TPS만 조정
10,000명:  TPS 167  × 60초
100,000명: TPS 1,667 × 60초
300,000명: TPS 5,000 × 60초
```

**장점**:
- ✅ 모든 테스트가 **동일 시간**으로 비교 공정
- ✅ 시간 기반 메트릭 (CPU, Memory) 추이 비교 용이
- ✅ 테스트 실행 시간 예측 가능 (항상 1분)

**단점**:
- ❌ TPS가 다르므로 **부하 강도가 다름** (공정한 비교 어려움)
- ❌ 10k 규모는 TPS 167로 너무 낮아 의미 없음
- ❌ 실제 티켓팅 시나리오와 다름 (갑자기 TPS가 30배 증가하지 않음)
- ❌ 코드 최적화 효과 측정 어려움 (부하 자체가 다름)

**적합한 경우**:
- 지속 시간 기반 안정성 테스트
- 시간에 따른 메모리 누수 체크
- **현재 목적에는 부적합** ❌

---

#### 최종 권장 방안: Approach A (TPS 고정)

**근거**:

1. **실제 시나리오 반영**:
   - 티켓 오픈 시 **일정한 폭발적 속도**로 유입 (TPS 5000)
   - 사용자 수만 누적 증가 (30만 명까지)

2. **코드 최적화 효과 측정에 유리**:
   - Before/After 비교 시 **동일 TPS** 조건 유지
   - 처리 속도(Throughput) 개선율 명확히 측정
   - 예: "TPS 5000 부하에서 Throughput이 800 → 1200 users/sec로 향상"

3. **병목 지점 파악**:
   - 10k (2초):  TPS 5000 처리 가능? → 정상
   - 100k (20초): TPS 5000 유지 시 지연 발생? → Redis 병목
   - 300k (60초): TPS 5000 유지 시 큰 지연? → Scheduler 병목

4. **스케일 아웃 효과 검증**:
   - 1 인스턴스: TPS 5000 → Throughput 800
   - 3 인스턴스: TPS 5000 → Throughput 2400 (3배)
   - 선형 확장성 정량 측정 가능

---

#### 최종 k6 테스트 설정

**TPS 5000 기준 테스트**:

```javascript
// k6-tests/queue-entry-scale-test.js
export const options = {
  scenarios: {
    // Scenario 1: 1만 명 (2초)
    load_10k: {
      executor: 'constant-arrival-rate',
      rate: 5000,              // TPS 5000 고정
      duration: '2s',          // 10,000 / 5000 = 2초
      preAllocatedVUs: 1000,
      maxVUs: 2000,
    },

    // Scenario 2: 10만 명 (20초)
    load_100k: {
      executor: 'constant-arrival-rate',
      rate: 5000,
      duration: '20s',         // 100,000 / 5000 = 20초
      preAllocatedVUs: 1000,
      maxVUs: 2000,
      startTime: '10s',        // 이전 테스트 완료 + 정리 시간
    },

    // Scenario 3: 30만 명 (60초) - 메인 시나리오
    load_300k: {
      executor: 'constant-arrival-rate',
      rate: 5000,              // TPS 5000 고정
      duration: '60s',         // 300,000 / 5000 = 60초
      preAllocatedVUs: 1000,
      maxVUs: 2000,
      startTime: '40s',        // 이전 테스트 완료 + 정리 시간
    },
  },
};
```

**측정 목표**:

| 규모 | TPS | Duration | 목표 Throughput | 목표 Wait Time |
|------|-----|----------|-----------------|----------------|
| 1만 명 | 5000 | 2초 | 2000+ users/sec | < 10초 |
| 10만 명 | 5000 | 20초 | 2000+ users/sec | < 1분 |
| 30만 명 | 5000 | 60초 | 2000+ users/sec | < 3분 |

**성공 기준**:
- ✅ TPS 5000을 안정적으로 처리 (요청 손실률 < 1%)
- ✅ Throughput 2000+ users/sec 유지 (Wait → Active 전환 속도)
- ✅ 30만 명 규모에서 평균 대기 시간 < 5분
- ✅ Scheduler Duration < 2000ms (p99)
- ✅ Redis Script Duration < 10ms (p99)

---

#### 보수적 테스트 옵션 (TPS 2000)

만약 초기 테스트에서 시스템이 TPS 5000을 감당하지 못할 경우:

```javascript
// 보수적 설정: TPS 2000 (30만 명 / 150초)
export const options = {
  scenarios: {
    load_300k: {
      executor: 'constant-arrival-rate',
      rate: 2000,
      duration: '150s',        // 2.5분
      preAllocatedVUs: 500,
      maxVUs: 1000,
    },
  },
};
```

**단계별 접근**:
1. **Phase 1**: TPS 2000 (보수적) → 현재 시스템 성능 파악
2. **Phase 2**: 코드 최적화 후 TPS 3000~4000 테스트
3. **Phase 3**: 스케일 아웃 후 TPS 5000 목표 달성

---

### 테스트 시나리오 실행 가이드

---

#### Phase 1: Queue Entry 성능 측정 (현재)

**목표**: 대기열 진입 성능 집중 측정

**사전 준비**:
1. Active Queue 크기 증가
```yaml
# queue-service/src/main/resources/application.yml
queue:
  active:
    max-size: 310000  # 50000 → 310000
```

2. 빌드 및 배포
```bash
.\gradlew.bat :queue-service:clean :queue-service:build -x test
docker-compose -f docker-compose.simple-scale.yml build queue-service
docker-compose -f docker-compose.simple-scale.yml up -d
```

**테스트 실행**:
```bash
# K6 테스트 실행 (단계적 시나리오)
docker-compose -f docker-compose.simple-scale.yml run --rm k6 run /scripts/queue-entry-scale-test.js
```

**측정 항목**:
1. `queue.wait.size`: 대기열 크기
2. `queue.throughput.users_per_second`: 처리 속도
3. `queue.estimated.wait.seconds`: 예상 대기 시간
4. `scheduler.move.duration`: 스케줄러 처리 시간
5. `redis.script.duration`: Redis 쿼리 시간

**k6 스크립트**:
```javascript
// k6-tests/queue-entry-scale-test.js (이미 수정됨)
export const options = {
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate',
      rate: 1000,
      duration: '10s',
      startTime: '0s',
    },
    peak_load: {
      executor: 'constant-arrival-rate',
      rate: 5000,        // 핵심: TPS 5000
      duration: '60s',   // 300,000명 진입
      startTime: '10s',
    },
    cooldown: {
      executor: 'constant-arrival-rate',
      rate: 0,
      duration: '30s',   // 처리 관찰
      startTime: '70s',
    },
  },
};
```

**실제 테스트 결과** (2025-12-26 실행):

---

## Phase 1: Queue Entry 성능 측정 결과

### 📋 테스트 설정

| 항목 | 설정값 |
|------|--------|
| **테스트 일시** | 2025-12-26 11:26 KST |
| **환경** | Docker (1개 queue-service 인스턴스) |
| **Active Queue Max Size** | 310,000명 |
| **시나리오** | Warmup (10s, TPS 1000) + Peak (60s, TPS 5000) |
| **목표 총 요청** | 310,000명 |

---

### 📊 K6 클라이언트 측 성능 지표

#### HTTP 응답 시간
| 지표 | 목표 | 실제 측정 | 상태 |
|------|------|----------|------|
| **평균 응답 시간** | - | 136.69ms | ⚠️ |
| **중앙값 (P50)** | - | 2.94ms | ✅ |
| **P90 응답 시간** | - | 335.93ms | ⚠️ |
| **P95 응답 시간** | **< 200ms** | **632.21ms** | ❌ **3.2배 초과** |
| **P99 응답 시간** | **< 500ms** | **1.35s** | ❌ **2.7배 초과** |
| **최대 응답 시간** | - | 7.03s | ❌ |

#### 요청 처리 통계
| 지표 | 목표 | 실제 측정 | 상태 |
|------|------|----------|------|
| **총 요청 수** | 310,000 | **266,274** | ❌ **85.9%** |
| **성공 요청** | - | 266,080 | 99.93% |
| **실패 요청** | < 5% | 194 (0.07%) | ✅ |
| **성공률** | **> 95%** | **92.58%** | ❌ **2.42% 부족** |
| **Dropped Iterations** | 0 | **43,727** | ❌ **VU 부족** |

#### 처리량
| 지표 | 측정값 |
|------|--------|
| **평균 TPS** | 3,797 req/s |
| **테스트 총 시간** | 70.1초 |
| **데이터 수신** | 120 MB (1.7 MB/s) |
| **데이터 송신** | 51 MB (732 kB/s) |

---

### 🖥️ 서버 측 Prometheus 메트릭

#### 대기열 상태
| 메트릭 | 측정값 | 상태 |
|--------|--------|------|
| **queue.wait.size** | NaN | ❌ **메트릭 오류** |
| **queue.active.size** | 0명 | ⚠️ **비어있음** |
| **queue.throughput** | 41,801.6 users/sec | ❌ **비정상적** |

#### 스케줄러 성능
- **상태**: ❌ **오류 발생**
- **에러**: `QueueDataCorruptionException: 대기열 데이터 무결성 오류`
- **원인**: JSON 파싱 실패 (Lua 스크립트 반환 형식 오류)

---

### 🚨 발견된 주요 문제점

#### 1. **스케줄러 JSON 파싱 오류** (치명적)

**증상**:
```
MismatchedInputException: Cannot deserialize value of type ArrayList<String>
from Object value (token JsonToken.START_OBJECT)
```

**발생 위치**:
```
RedisTokenConverter.parseUserIdsFromJson (line 103)
  ↓
RedisActiveQueueAdapter.moveToActiveQueueAtomic (line 225)
  ↓
QueueScheduler.moveWaitingUsersToActive (line 95)
```

**근본 원인**:
- Lua 스크립트(`move_to_active_queue.lua`)가 잘못된 JSON 형식 반환
- **예상 형식**: `["userId1", "userId2", ...]` (배열)
- **실제 반환**: `{"key": "value"}` (객체)

**영향**:
- ❌ Wait Queue → Active Queue 전환 **완전히 중단**
- ❌ 266K명이 Wait Queue에 쌓인 채로 정체
- ❌ 메트릭 수집 불가 (NaN 값 발생)

---

#### 2. **HTTP 응답 시간 급격한 증가**

**P95/P99 급증 원인 분석**:

```
시간대별 응답 시간 추이 (추정):
0~10s  (Warmup):  P95 ~100ms   ← 정상
10~30s (Peak 초반): P95 ~200ms   ← 부하 증가
30~50s (Peak 중반): P95 ~500ms   ← 큐 적체 시작
50~70s (Peak 후반): P95 >1000ms  ← 스케줄러 오류로 인한 급증
```

**추정 원인**:
1. **스케줄러 오류로 인한 연쇄 효과**
   - Wait Queue가 비워지지 않음
   - 대기열 진입 시 대기 순번 계산 지연
   - Redis 조회 오버헤드 누적

2. **K6 VU 부족**
   - maxVUs 3000으로 설정했으나 부족
   - 43,727개 요청 drop
   - 실제 TPS: 3,797 (목표: 5,000)

---

#### 3. **메트릭 수집 실패**

**문제**:
- `queue.wait.size`: **NaN** (Not a Number)
- `queue.throughput`: **41,801.6** (비정상적으로 높음)

**원인**:
- Gauge 메트릭이 제대로 업데이트되지 않음
- 스케줄러 오류로 인해 메트릭 계산 로직 실행 안 됨

---

### 🔍 근본 원인 분석 (Root Cause Analysis)

#### 타임라인 재구성

```
T+0s: K6 테스트 시작 (Warmup phase)
  ├─ TPS 1000으로 요청 시작
  └─ 정상 처리 (P95 ~100ms)

T+10s: Peak load 시작 (TPS 5000)
  ├─ 초당 5000명 Wait Queue 진입
  ├─ 스케줄러 5초마다 Wait → Active 시도
  └─ 🚨 Lua 스크립트 JSON 파싱 오류 발생

T+15s: 첫 스케줄러 실행 실패
  ├─ QueueDataCorruptionException 발생
  ├─ Wait → Active 전환 중단
  └─ Wait Queue 누적 시작 (5000명/초)

T+20s~70s: 오류 지속
  ├─ 매 5초마다 스케줄러 실행 시도 → 실패 반복
  ├─ Wait Queue 계속 증가 (~266K명)
  ├─ Active Queue 비어있음 (0명)
  └─ HTTP 응답 시간 급증 (대기 순번 계산 부하)

T+70s: 테스트 종료
  ├─ 총 266,274명 진입 (목표의 85.9%)
  ├─ Wait Queue에 적체
  └─ Active Queue로 이동 0명
```

---

### 📌 병목 지점 정리

| 순위 | 병목 지점 | 심각도 | 영향도 |
|------|----------|--------|--------|
| 🥇 **1위** | **Lua 스크립트 JSON 형식 오류** | 🔴 치명적 | Wait → Active 전환 완전 중단 |
| 🥈 2위 | **K6 VU 부족** | 🟡 보통 | 43K 요청 drop, 실제 TPS 76% |
| 🥉 3위 | **HTTP 응답 지연** | 🟠 높음 | P95 3배, P99 2.7배 초과 |

---

### ✅ 개선 방안

#### 즉시 수정 필요 (Priority 1)

**1. Lua 스크립트 JSON 형식 수정**
```lua
-- move_to_active_queue.lua
-- Before (추정): return {key1 = val1, key2 = val2}  ← Object
-- After:         return {"val1", "val2"}            ← Array
```

**기대 효과**:
- ✅ Wait → Active 전환 정상화
- ✅ 메트릭 수집 정상화
- ✅ HTTP 응답 시간 개선 (대기 적체 해소)

**2. K6 설정 조정**
```javascript
// maxVUs 증가
peak_load: {
  preAllocatedVUs: 3000,  // 2000 → 3000
  maxVUs: 5000,           // 3000 → 5000
}
```

**기대 효과**:
- ✅ Dropped iterations 0으로 감소
- ✅ 실제 TPS 5000 달성

---

### 📝 추가 조사 필요 사항

1. **Lua 스크립트 검증**
   - [ ] `move_to_active_queue.lua` 코드 리뷰
   - [ ] 반환 형식 확인 및 수정
   - [ ] 단위 테스트 작성

2. **메트릭 로직 점검**
   - [ ] Gauge 등록 방식 확인
   - [ ] throughput 계산 로직 검증 (41K는 비정상)

3. **성능 재테스트**
   - [ ] Lua 스크립트 수정 후 재실행
   - [ ] 정상 작동 확인
   - [ ] Baseline 데이터 수집

---

### 💡 학습 포인트 (Lessons Learned)

1. **메트릭 우선 검증의 중요성**
   - K6 테스트만 보고 성능을 판단하면 안 됨
   - 서버 측 메트릭(Prometheus)과 교차 검증 필수
   - NaN, 비정상적으로 높은 값 → 즉시 조사

2. **오류 전파 메커니즘**
   - 스케줄러 오류 → 대기열 적체 → HTTP 지연 급증
   - 하나의 컴포넌트 실패가 전체 시스템 성능에 영향

3. **부하 테스트 환경의 중요성**
   - Docker 네트워크 내부에서 테스트 시 경로 문제 주의
   - VU 설정은 여유있게 (목표 TPS의 1.5~2배)

---

### 🎯 다음 단계

**Step 1**: Lua 스크립트 수정
**Step 2**: 로컬 단위 테스트
**Step 3**: Phase 1 재실행
**Step 4**: Baseline 데이터 확보 후 Phase 2 진행

```

---

### ✅ Phase 1 문제 해결 및 재테스트 (2025-12-26 12:00 KST)

#### 🔧 Root Cause Analysis

**문제**: Lua 스크립트 JSON 인코딩 오류로 인한 스케줄러 완전 중단
- Redis Lua의 `cjson.empty_array`가 일부 Redis 버전에서 지원되지 않음
- 빈 테이블 `{}`을 `cjson.encode()`로 인코딩 시 JSON 객체 `"{}"` 반환 (배열 `"[]"` 아님)
- Java Jackson parser가 객체를 배열로 파싱 시도 → `MismatchedInputException`
- 결과: Wait → Active 큐 전환 완전 중단

**로그 에러**:
```log
CRITICAL: Queue data corruption - Lua script succeeded but result parsing failed.
Users may have been moved but cannot be tracked: concertId=concert-1, jsonResult=null
```

#### 🛠️ 적용된 수정사항

**1. Lua 스크립트 수정** (`move_to_active_queue.lua`)
```lua
-- Before (broken)
if #poppedUsers == 0 then
    return cjson.encode(cjson.empty_array)  -- cjson.empty_array 미지원
end
local movedUserIds = cjson.empty_array
return cjson.encode(movedUserIds)

-- After (fixed)
if #poppedUsers == 0 then
    return "[]"  -- 명시적 JSON 문자열 반환
end
local movedUserIds = {}
if #movedUserIds == 0 then
    return "[]"  -- 모든 유저 롤백 시에도 명시적 빈 배열
end
return cjson.encode(movedUserIds)
```

**2. Java 방어 코드 추가** (`RedisActiveQueueAdapter.java:219`)
```java
// Before
if (jsonResult == null || jsonResult.isEmpty() || jsonResult.equals("[]")) {
    return List.of();
}

// After (defensive)
if (jsonResult == null || jsonResult.isEmpty() || jsonResult.equals("[]") || jsonResult.equals("{}")) {
    return List.of();  // "{}" 도 빈 결과로 처리
}
```

#### 📊 Phase 1 재테스트 결과 (수정 후)

| 지표 | 수정 전 (1차) | 수정 후 (2차) | 개선율 | 목표 | 달성 |
|------|---------------|---------------|--------|------|------|
| **총 요청 수** | 266,274 | 302,889 | +13.8% | 310,000 | 97.7% |
| **Dropped Iterations** | 43,727 | 7,114 | **-83.7%** | <5% | ❌ 2.3% |
| **성공률** | 92.58% | 96.49% | +4.2% | >95% | ✅ |
| **P95 응답시간** | 632ms | 419ms | **-33.7%** | <200ms | ❌ |
| **P99 응답시간** | 1.35s | 651ms | **-51.8%** | <500ms | ❌ |
| **HTTP 에러율** | 0.01% | 0.00% | -100% | <5% | ✅ |
| **실제 TPS** | 3,797 | 4,320 | +13.8% | 5,000 | 86.4% |

#### 🔍 서버 측 메트릭 (Prometheus)

| 메트릭 | 수정 전 (1차) | 수정 후 (2차) | 상태 |
|--------|---------------|---------------|------|
| `queue.wait.size` | NaN | 0 | ✅ 정상 |
| `queue.active.size` | 0 | 4 | ✅ 정상 |
| `queue.throughput.users_per_second` | 41,801.6 (비정상) | 0 (테스트 후) | ✅ 정상 |
| 스케줄러 처리량 | **0 users** (중단) | **~40,000 users** | ✅ 정상 |

**스케줄러 로그 (수정 후)**:
```log
[PERF] MoveToActive: concertId=concert-1, movedUsers=26801
[PERF] MoveToActive: concertId=concert-1, movedUsers=13008
Total moved: ~39,809 users (Wait → Active 전환 성공)
```

#### 💡 주요 개선 사항

**1. 스케줄러 정상 작동 확인**
- ✅ Lua 스크립트 JSON 인코딩 오류 해결
- ✅ Wait → Active 큐 전환 정상 작동
- ✅ 메트릭 수집 정상화 (NaN 해결)

**2. 클라이언트 성능 개선**
- ✅ P95 응답시간: 632ms → 419ms (33.7% 개선)
- ✅ P99 응답시간: 1.35s → 651ms (51.8% 개선)
- ✅ 성공률: 92.58% → 96.49% (목표 달성)
- ✅ Dropped iterations: 43,727 → 7,114 (83.7% 감소)

**3. 시스템 처리량 개선**
- ✅ 실제 TPS: 3,797 → 4,320 (13.8% 증가)
- ✅ 총 처리 요청: 266K → 302K (13.8% 증가)

#### 🚨 남은 병목 구간

**1. P95/P99 응답시간 목표 미달성**
- **현재**: P95=419ms, P99=651ms
- **목표**: P95<200ms, P99<500ms
- **원인 추정**:
  - Redis 커넥션 풀 부족 (현재 max-active: 20)
  - Wait Queue 크기 계산 오버헤드 (ZCOUNT 호출)
  - 대기열 위치 계산 복잡도 O(log N)

**2. VU 부족으로 인한 Dropped Iterations**
- **현재**: 7,114 dropped (2.3%)
- **원인**: maxVUs=3000 부족, 피크 시 5000 TPS 요구
- **해결**: k6 설정 수정 필요 (maxVUs: 5000)

**3. 실제 TPS 목표 미달**
- **현재**: 4,320 TPS (86.4%)
- **목표**: 5,000 TPS
- **원인**: VU 부족 + 응답시간 지연 복합

#### 📝 Lessons Learned

**1. Redis Lua 스크립트 호환성**
- `cjson.empty_array`는 cjson 2.1.0+ 전용 (Redis 버전별 상이)
- 명시적 JSON 문자열 반환 (`"[]"`)이 가장 안전
- 방어적 프로그래밍: Java에서 `"{}"` 케이스도 처리

**2. 메트릭 수집 중요성**
- Prometheus 메트릭 NaN 발견으로 스케줄러 중단 조기 파악
- 성능 테스트는 클라이언트 + 서버 양측 메트릭 필수

**3. 로드 테스트 VU 계산**
- constant-arrival-rate executor: `maxVUs >= rate * p99_duration`
- 예: 5000 TPS × 0.65s = 최소 3250 VUs 필요
- 여유 20%: 3900 VUs 권장

#### 🎯 다음 단계 (Updated)

| 순서 | 작업 | 예상 효과 | 우선순위 |
|------|------|-----------|----------|
| 1 | k6 VU 증가 (maxVUs: 5000) | TPS 5000 달성 | High |
| 2 | Redis 풀 증가 (max-active: 50) | P95/P99 개선 | High |
| 3 | 대기열 위치 계산 최적화 | P95 개선 | Medium |
| 4 | Phase 1 최종 Baseline 확보 | 다음 단계 진행 | High |

**Step 1**: k6 스크립트 수정 (VU 증가)
**Step 2**: Redis 풀 설정 최적화
**Step 3**: Phase 1 최종 재테스트
**Step 4**: Baseline 확보 후 Phase 2 진행

---

#### Phase 2: E2E 플로우 테스트 (다음 단계)

**목표**: 실제 예매 플로우 성능 측정

**사전 준비**:
1. Active Queue 크기 복원
```yaml
# queue-service/src/main/resources/application.yml
queue:
  active:
    max-size: 50000  # 310000 → 50000 (현실적 크기)
```

2. E2E 테스트 스크립트 작성
```bash
# 새로 작성 필요
# k6-tests/e2e-booking-test.js
```

3. 테스트 데이터 준비
```sql
-- MySQL에 콘서트/좌석 데이터 삽입
INSERT INTO concerts ...
INSERT INTO seats ...
```

**테스트 실행**:
```bash
# E2E 테스트 실행
docker-compose -f docker-compose.simple-scale.yml run --rm k6 run /scripts/e2e-booking-test.js
```

**측정 항목**:
- E2E 완료 시간 (대기열 진입 → 결제 완료)
- 각 단계별 응답 시간 (진입/폴링/조회/예약/결제)
- Active Queue 순환율
- DB 커넥션 풀 사용률
- 전체 시스템 처리량 (완료된 예매 수/초)

**결과 기록 양식**:
```markdown
## Phase 2: E2E 플로우 테스트 결과

### 테스트 설정
- 동시 사용자: 1000명
- Duration: 120초

### E2E 메트릭
- E2E 평균 완료 시간: XXX초
- E2E 성공률: XX%
- 대기열 진입 시간 (avg): XXX ms
- 폴링 대기 시간 (avg): XXX초
- 좌석 조회 시간 (avg): XXX ms
- 예약 시간 (avg): XXX ms
- 결제 시간 (avg): XXX ms

### Active Queue 순환
- 진입률: XXX users/sec
- 제거율: XXX users/sec
- 평균 체류 시간: XXX초
```

---

#### Phase 3: 스케일 아웃 비교 (최종 단계)

**목표**: 인스턴스 증설 효과 검증

**환경 설정**:
```bash
# 1개 인스턴스 (Baseline)
docker-compose -f docker-compose.simple-scale.yml up -d

# 2개 인스턴스
docker-compose -f docker-compose.simple-scale.yml up -d --scale queue-service=2

# 4개 인스턴스
docker-compose -f docker-compose.simple-scale.yml up -d --scale queue-service=4
```

**테스트 실행**:
- Phase 2의 E2E 테스트를 각 환경에서 반복 실행

**측정 항목**:
- 인스턴스별 처리량 분산 (균등 분산 확인)
- scheduler.lock.acquire.failures (락 경합률)
- E2E 완료 시간 개선율
- 스케일 효율성 (선형 증가 확인)

**결과 기록 양식**:
```markdown
## Phase 3: 스케일 아웃 비교 결과

### 인스턴스별 성능

| 인스턴스 수 | E2E 완료 시간 | 처리량 (users/sec) | 스케일 효율 |
|------------|---------------|-------------------|-------------|
| 1개        | XXX초         | XXX               | -           |
| 2개        | XXX초         | XXX               | XX%         |
| 4개        | XXX초         | XXX               | XX%         |

### 락 경합 분석
- 1개 인스턴스: 0 failures
- 2개 인스턴스: XXX failures/min (X%)
- 4개 인스턴스: XXX failures/min (X%)

### 인스턴스별 부하 분산
- instance-1: XXX users/sec (XX%)
- instance-2: XXX users/sec (XX%)
- instance-3: XXX users/sec (XX%)
- instance-4: XXX users/sec (XX%)
- scheduler.move.duration: 1200ms (유지)
- redis.script.duration: 3ms (유지)

### 스케일 효율성
- 이상적 처리량: 3600 users/sec (3배)
- 실제 처리량: 3400 users/sec
- 스케일 효율: 94% (3400/3600)

### 락 경합 분석
- lock.acquire.failures: 120회/분
- 전체 시도: 1800회/분
- 실패율: 6.7%

### 인스턴스별 분산
- instance-1: 1150 users/sec
- instance-2: 1100 users/sec
- instance-3: 1150 users/sec
→ 부하 균등 분산 확인
```

---

## 성능 개선 작업 예시

### 1. Redis Lua 스크립트 최적화

**문제 진단**:
```
redis.script.duration (move_to_active_queue) = 22ms
→ 30만 명 규모에서 ZPOPMIN 성능 저하
```

**개선 방안**:

#### Option A: 배치 크기 동적 조정
```java
// QueueDomainService.java
public int calculateBatchSize(Long currentActiveSize) {
    long available = queueConfig.getActiveMaxSize() - currentActiveSize;

    // 기존: 고정 배치 크기
    // return (int) Math.min(available, 1000);

    // 개선: 부하에 따라 동적 조정
    if (currentActiveSize < 10000) {
        return (int) Math.min(available, 2000);  // 부하 낮을 때 크게
    } else if (currentActiveSize < 40000) {
        return (int) Math.min(available, 1000);  // 중간
    } else {
        return (int) Math.min(available, 500);   // 포화 시 작게
    }
}
```

#### Option B: Redis Pipeline 도입
```java
// RedisTemplate Pipeline 사용
redisTemplate.executePipelined(new SessionCallback<Object>() {
    @Override
    public Object execute(RedisOperations operations) throws DataAccessException {
        // 여러 Redis 명령을 한 번에 전송
        operations.opsForZSet().popMin(waitQueueKey, count);
        operations.opsForZSet().add(activeQueueKey, userId, score);
        operations.opsForHash().put(tokenKey, fields);
        return null;
    }
});
```

**예상 효과**:
- redis.script.duration: 22ms → 3ms (86% 개선)
- queue.throughput: 800 → 1200 users/sec

---

### 2. 스케줄러 인터벌 최적화

**문제 진단**:
```
scheduler.move.duration = 4500ms
interval = 5000ms
→ 처리 시간이 인터벌에 근접 (90%), 처리 지연 발생
```

**개선 방안**:

```yaml
# application.yml
queue:
  scheduler:
    activation-interval-ms: 3000  # 5000 → 3000 (더 자주 실행)
```

**트레이드오프 분석**:
- 장점: 더 자주 처리하여 대기 시간 감소
- 단점: CPU 사용량 증가
- 권장: duration < interval * 0.7 유지

**예상 효과**:
- 실행 빈도: 12회/분 → 20회/분
- 처리 지연 감소

---

### 3. 분산 락 전략 최적화

**문제 진단** (스케일 아웃 시):
```
3 인스턴스 운영 시
lock.failures = 높음
→ 불필요한 경합 발생
```

**개선 방안**:

#### Option A: 콘서트별 Sticky 할당
```java
// 각 인스턴스가 특정 콘서트만 담당
int instanceIndex = Integer.parseInt(System.getenv("INSTANCE_ID").split("-")[1]);
int totalInstances = 3;

List<String> myConcerts = concertIds.stream()
    .filter(id -> Math.abs(id.hashCode()) % totalInstances == instanceIndex)
    .collect(Collectors.toList());
```

#### Option B: 락 타임아웃 조정
```java
// ClusterLockAdapter.java
private static final Duration LOCK_TIMEOUT = Duration.ofSeconds(3); // 5 → 3초
```

---

## 최종 성능 개선 보고서 양식

```markdown
# 대기열 시스템 성능 개선 보고서

## Executive Summary
30만 명 대기열 동시 처리 성능을 개선하여 사용자 대기 시간을 **76% 단축**하였습니다.

---

## 1. 개선 전 (Baseline)

### 시스템 구성
- Queue Service: 1 인스턴스
- Redis: Cluster (3 Master, 3 Replica)

### 성능 지표 (30만 명 부하)
| 지표 | 측정값 |
|------|--------|
| 처리 속도 | 800 users/sec |
| 대기 시간 | 375초 (6.25분) |
| Scheduler Duration | 4500ms (avg) |
| Redis Script Duration | 22ms (avg) |

### 병목 지점
1. Redis Lua 스크립트 성능 저하 (22ms)
2. 스케줄러 처리 시간 과다 (4.5초)
3. 단일 인스턴스 처리 한계

---

## 2. 개선 작업

### 2-1. 코드 최적화 (수직적 개선)

#### 작업 내용
1. Redis Lua 스크립트 최적화
   - ZPOPMIN 배치 크기 동적 조정
   - 불필요한 Redis 조회 제거

2. 스케줄러 인터벌 조정
   - 5초 → 3초 (실행 빈도 67% 증가)

3. Redis Connection Pool 튜닝
   - max-active: 10 → 20

#### 결과 (1 인스턴스)
| 지표 | Before | After | 개선율 |
|------|--------|-------|--------|
| 처리 속도 | 800 users/sec | 1200 users/sec | +50% |
| 대기 시간 | 375초 | 250초 | -33% |
| Scheduler Duration | 4500ms | 1200ms | -73% |
| Redis Script Duration | 22ms | 3ms | -86% |

---

### 2-2. 스케일 아웃 (수평적 확장)

#### 작업 내용
- Queue Service 인스턴스: 1개 → 3개 증설
- 분산 락 활성화 (Redis Streams)
- 콘서트별 부하 분산

#### 결과 (3 인스턴스)
| 지표 | 1 Instance | 3 Instances | 개선율 |
|------|------------|-------------|--------|
| 처리 속도 | 1200 users/sec | 3400 users/sec | +183% |
| 대기 시간 | 250초 | 88초 | -65% |
| 스케일 효율 | - | 94% | - |
| 락 실패율 | - | 6.7% | - |

**스케일 효율성 분석**:
- 이상적 처리량: 3600 users/sec (3배)
- 실제 처리량: 3400 users/sec
- 효율: 94% (락 경합 최소화로 우수한 선형 확장성 달성)

**인스턴스별 부하 분산**:
| Instance | Throughput | 비율 |
|----------|------------|------|
| instance-1 | 1150 users/sec | 34% |
| instance-2 | 1100 users/sec | 32% |
| instance-3 | 1150 users/sec | 34% |
→ 균등 분산 확인

---

## 3. 최종 결과

### 총 개선율 (30만 명 부하)
| 지표 | Before | After | 개선율 |
|------|--------|-------|--------|
| **처리 속도** | 800 users/sec | 3400 users/sec | **+325%** |
| **대기 시간** | 6.25분 | 1.5분 | **-76%** |
| Redis 성능 | 22ms | 3ms | -86% |
| Scheduler 성능 | 4500ms | 1200ms | -73% |

### 개선 기여도
- **코드 최적화**: 50% 처리 속도 향상
- **스케일 아웃**: 183% 추가 향상
- **총 향상**: 325% (800 → 3400 users/sec)

---

## 4. 시사점

### 성공 요인
1. **정량적 측정 기반 개선**
   - Micrometer 메트릭으로 병목 정확히 파악
   - Grafana로 실시간 성능 모니터링

2. **수직+수평 복합 전략**
   - 단일 인스턴스 최적화 먼저 수행
   - 스케일 아웃으로 선형 확장성 달성

3. **부하 테스트 시나리오 설계**
   - 30만 명 실제 시나리오 반영
   - Before/After 동일 조건 비교

### 향후 개선 방향
1. Redis Cluster 샤딩 최적화
2. Virtual Thread 전환 (Java 21)
3. 대기열 우선순위 처리 로직 추가

---

## 5. 참고 자료

### 모니터링 대시보드
- Grafana: http://localhost:3000/d/concert-booking-application
- Prometheus: http://localhost:9090

### 부하 테스트 스크립트
- `k6-tests/queue-300k-baseline.js`
- `k6-tests/queue-300k-optimized.js`

### 관련 문서
- `docs/performance-improvement-plan.md`
- `SCALE_TEST_GUIDE.md`
```

---

## 체크리스트

### ✅ 사전 준비 (완료)
- [x] queue.wait.size 메트릭 구현
- [x] queue.throughput.users_per_second 메트릭 구현
- [x] queue.estimated.wait.seconds 메트릭 구현
- [x] scheduler.lock.acquire.failures 메트릭 구현
- [x] scheduler.concerts.count 메트릭 구현
- [x] instance 태그 추가 (application.yml)
- [x] Grafana 대시보드 패널 추가
- [x] k6 단계적 시나리오 스크립트 작성 (queue-entry-scale-test.js)
- [x] Docker Compose 스케일 아웃 설정 (docker-compose.simple-scale.yml)

---

### 🔄 Phase 1: Queue Entry 성능 측정 (현재)

**환경 설정**:
- [ ] Active Queue max-size를 310,000으로 증가
- [ ] queue-service 빌드
- [ ] Docker 이미지 빌드
- [ ] 서비스 시작 (1개 인스턴스)

**테스트 실행**:
- [ ] K6 테스트 실행 (TPS 5000, 60초 피크)
- [ ] Grafana에서 메트릭 확인
- [ ] Prometheus 데이터 수집 확인

**결과 분석**:
- [ ] HTTP 응답 시간 기록 (P95, P99)
- [ ] queue.throughput 기록
- [ ] scheduler.move.duration 기록
- [ ] redis.script.duration 기록
- [ ] 병목 지점 식별

**개선 작업 (필요 시)**:
- [ ] Redis Lua 스크립트 최적화
- [ ] 스케줄러 인터벌 조정
- [ ] Connection Pool 튜닝
- [ ] 재측정 및 개선율 계산

---

### 📝 Phase 2: E2E 플로우 테스트 (다음)

**환경 설정**:
- [ ] Active Queue max-size를 50,000으로 복원
- [ ] MySQL 테스트 데이터 준비 (콘서트, 좌석)
- [ ] Core Service 연동 확인

**스크립트 작성**:
- [ ] e2e-booking-test.js 작성
- [ ] 대기열 진입 함수
- [ ] 폴링 함수 (활성화 대기)
- [ ] 좌석 조회 함수
- [ ] 좌석 예약 함수
- [ ] 결제 함수

**테스트 실행**:
- [ ] E2E 테스트 실행 (1000 동시 사용자)
- [ ] Active Queue 순환 확인
- [ ] 전체 플로우 완료 시간 측정

**결과 분석**:
- [ ] E2E 완료율 기록
- [ ] 각 단계별 응답 시간 기록
- [ ] Active Queue 진입/제거율 기록
- [ ] DB 커넥션 풀 사용률 확인
- [ ] 병목 지점 식별

---

### 🚀 Phase 3: 스케일 아웃 비교 (최종)

**환경 설정**:
- [ ] 1개 인스턴스 Baseline 측정
- [ ] 2개 인스턴스 배포 (--scale queue-service=2)
- [ ] 4개 인스턴스 배포 (--scale queue-service=4)
- [ ] INSTANCE_ID 환경변수 설정 확인

**테스트 실행**:
- [ ] 1개 인스턴스 E2E 테스트
- [ ] 2개 인스턴스 E2E 테스트
- [ ] 4개 인스턴스 E2E 테스트

**결과 분석**:
- [ ] 인스턴스별 처리량 비교
- [ ] scheduler.lock.acquire.failures 분석
- [ ] 스케일 효율성 계산
- [ ] 인스턴스별 부하 분산 확인
- [ ] E2E 완료 시간 개선율 계산

---

### 📊 최종 보고서 작성

- [ ] 3개 Phase 결과 데이터 정리
- [ ] Grafana 대시보드 스크린샷 캡처
- [ ] 성능 비교 그래프 생성
- [ ] 개선율 계산 (처리량, 대기 시간)
- [ ] 병목 지점 및 해결 방안 정리
- [ ] 최종 성능 개선 보고서 작성

---

## 참고 명령어

### 빌드
```bash
.\gradlew.bat :queue-service:clean :queue-service:build -x test
```

### Redis Cluster 시작
```bash
docker-compose -f docker-compose.cluster.yml up -d
```

### 3 인스턴스 배포
```bash
# docker-compose.cluster.yml 수정 후
docker-compose -f docker-compose.cluster.yml up -d --scale queue-service=3
```

### k6 부하 테스트
```bash
k6 run --out prometheus=localhost:9090 k6-tests/queue-300k-baseline.js
```

### Grafana 접속
```
http://localhost:3000
ID: admin
PW: admin
```

### Prometheus 쿼리 예시
```promql
# 처리 속도
queue_throughput_users_per_second

# 대기열 크기
queue_wait_size

# 인스턴스별 처리량
sum by (instance) (rate(scheduler_move_users_total[1m]))
```

---

## Phase 2: 수평 확장 테스트 결과 (완료)

**테스트 일시**: 2025-12-26

### 환경 구성
- Queue Service: 2 instances
- Redis: 단일 인스턴스
- Target TPS: 5,000
- Duration: 70초 (10s warmup + 60s peak)

### Phase 2 최종 결과

| 지표 | Phase 1 (1 instance) | Phase 2 (2 instances) | 개선율 |
|------|---------------------|----------------------|--------|
| **TPS** | 4,199 | 4,345 | +3.5% |
| **평균 응답 시간** | 31.8ms | 37.0ms | -16.4% |
| **P95** | 255ms | 292ms | -14.5% |
| **P99** | 516ms | 577ms | -11.8% |
| **성공률** | 99.04% | 99.17% | +0.13% |

### 주요 발견 사항

1. **TPS 개선 미미**: 3.5% 증가에 그침
   - 원인: Redis 단일 인스턴스 처리량 한계 (~4,300 TPS)

2. **응답 시간 증가**: 스케일 아웃 후 오히려 증가
   - 원인: 인스턴스 간 네트워크 오버헤드 증가

3. **Redis 병목 확인**: Redis가 주요 병목 지점으로 확인됨

**상세 보고서**: `docs/phase2-horizontal-scaling-analysis.md`

---

## Phase 3: Lua 스크립트 최적화 + Redis Cluster (완료)

**테스트 일시**: 2025-12-26

### Phase 3-2: Lua 스크립트 통합 (완료)

#### 문제 발견
Phase 2 종료 시점에서 대기열 진입 시 **6회 Redis 호출** 발생 확인:
- HGETALL (Active 확인)
- ZRANK (Wait 확인)
- ZCARD (Wait 크기)
- ZADD (신규 진입)
- ZRANK (신규 순번)
- ZCARD (전체 크기)

#### 해결 방안
모든 검증 및 진입 로직을 **단일 Lua 스크립트**(`enter_queue.lua`)로 통합:
- Redis 호출 6회 → 1회 (83% 감소)
- 네트워크 RTT 5회 절약 (약 5ms)
- 원자성 보장

#### 구현 파일
- `queue-service/src/main/resources/scripts/enter_queue.lua` (NEW)
- `RedisEnterQueueAdapter.java` (NEW)
- `EnterQueueService.java` (REFACTORED)
- `RedisConfig.java` (Bean 추가)

#### Phase 3-2 테스트 결과

| 지표 | Phase 2 | Phase 3-2 | 변화율 |
|------|---------|-----------|--------|
| **TPS** | 4,345 | 4,362.8 | +0.4% |
| **평균 응답시간** | 37.0ms | 22.69ms | **-38.7%** ✅ |
| **P95** | 292ms | 205.61ms | -29.6% |
| **P99** | 577ms | 468.66ms | -18.8% |
| **성공률** | 99.17% | 99.28% | +0.1% |

**핵심 발견**:
- ✅ 응답 시간 대폭 감소 (네트워크 RTT 절감 효과)
- ❌ TPS 미미한 증가 (Redis 단일 인스턴스 처리량 한계)

---

### Phase 3-3: Redis Cluster 확장 (완료)

#### 환경 구성
- Redis: Cluster (3 Master + 3 Replica = 6 nodes)
- Queue Service: 4 instances (스케일 아웃)
- Distributed Scheduler Lock: Redis SETNX 기반
- Hash Tag: `{concertId}` 사용 (Lua 스크립트 multi-key 지원)

#### Phase 3-3 최종 결과

| 지표 | Phase 3-2 | Phase 3-3 | 변화율 |
|------|-----------|-----------|--------|
| **TPS** | 4,362.8 | 4,406.2 | +1.0% |
| **평균 응답시간** | 22.69ms | 21.2ms | -6.6% |
| **P95** | 205.61ms | 130.73ms | **-36.4%** ✅ |
| **P99** | 468.66ms | 356.48ms | **-23.9%** ✅ |
| **성공률** | 99.28% | 99.64% | +0.4% |
| **HTTP 에러율** | 0.00% | 0.00% | - |

#### 임계값 달성 현황

- ✅ **P95 < 200ms**: 130.73ms (목표 대비 34.6% 여유)
- ✅ **P99 < 500ms**: 356.48ms (목표 대비 28.7% 여유)
- ✅ **에러율 < 5%**: 0.00%
- ✅ **성공률 > 95%**: 99.64%
- ⚠️ **TPS 5,000**: 4,406.2 (목표 대비 88.1%)

#### Phase 2 대비 전체 개선율

| 지표 | Phase 2 | Phase 3-3 | 총 개선율 |
|------|---------|-----------|----------|
| TPS | 4,345 | 4,406.2 | +1.4% |
| 평균 응답시간 | 37.0ms | 21.2ms | **-42.7%** ✅ |
| P95 | 292ms | 130.73ms | **-55.2%** ✅ |
| P99 | 577ms | 356.48ms | **-38.1%** ✅ |

---

### Phase 3 종합 평가

#### 성공한 부분 ✅

1. **사용자 경험 대폭 개선**
   - 평균 응답 시간 42.7% 단축
   - P95/P99 목표 최초 달성
   - 안정성 향상 (성공률 99.64%)

2. **Lua 스크립트 최적화 검증**
   - Redis 호출 83% 감소 효과 입증
   - 네트워크 RTT 절감 효과 확인
   - 원자성 보장으로 정합성 향상

3. **Redis Cluster 고가용성 확보**
   - Master 장애 시 자동 failover
   - 데이터 복제를 통한 안정성

#### 개선 필요 부분 ⚠️

1. **TPS 목표 미달**
   - 현재: 4,406.2 TPS (88.1%)
   - 목표: 5,000 TPS
   - 부족분: 593.8 TPS

2. **Redis Cluster 효과 제한적**
   - 예상: ~13,000 TPS (3 Master × 4,300 TPS)
   - 실제: 4,406.2 TPS (예상의 33.9%)
   - 원인: 단일 콘서트 테스트로 단일 샤드 집중

---

### Redis Cluster TPS 미달 원인 분석

#### 주요 원인: Hash Tag로 인한 단일 샤드 집중

**현재 Redis 키 설계**:
```java
"queue:wait:{concertId}"              // {concertId} hash tag
"active:token:{concertId}:userId"     // {concertId} hash tag
```

**Hash Slot 분배**:
```
CRC16({concertId}) mod 16384 = Hash Slot
→ 동일 concertId는 항상 동일 Slot
→ 동일 Slot은 동일 Master에 할당
```

**현재 테스트 상황**:
```
테스트 concertId: concert-1234 (단일 콘서트)
→ 모든 요청이 동일 Hash Slot으로 라우팅
→ 모든 요청이 동일 Redis Master로 집중
→ 나머지 2개 Master는 유휴 상태
→ 실질적으로 "단일 Redis"와 동일
```

---

### 5,000 TPS 달성을 위한 권장사항

#### ⭐ 옵션 1: 다중 콘서트 테스트 (즉시 가능, 권장)

**구현 방법**:
```javascript
// k6-tests/queue-entry-scale-test.js 수정
const concertIds = ['concert-alpha', 'concert-beta', 'concert-gamma'];
const concertId = concertIds[Math.floor(Math.random() * 3)];
```

**예상 효과**:
- 3개 콘서트 → 3개 Redis Master에 균등 분산
- 각 Master: 4,300 TPS 처리 가능
- **예상 총 TPS: 12,900 TPS** (목표 대비 258%)

**장점**:
- 코드 수정 최소 (테스트 스크립트만)
- 즉시 실행 가능
- Redis Cluster 진정한 효과 검증

#### 옵션 2: Queue Service 추가 스케일 아웃

```bash
docker-compose -f docker-compose.cluster.yml up -d --scale queue-service=8
```

**예상 효과**: TPS 8,800 (현재의 2배)

#### 옵션 3: 프로덕션 환경 배포 (최종 목표)

**AWS 구성**:
```
ALB → ECS (Queue Service Auto Scaling)
       ↓
    ElastiCache Redis Cluster
```

**예상 효과**: TPS 15,000~20,000+

---

### 최종 권장 실행 계획

**Phase 4: 다중 콘서트 테스트 (단기)**
1. K6 스크립트 수정 (3개 콘서트 랜덤)
2. Redis Cluster 성능 재측정
3. TPS 5,000 돌파 확인

**Phase 5: 프로덕션 준비 (중기)**
1. AWS 인프라 구성
2. ElastiCache Redis Cluster 설정
3. ECS 기반 Queue Service 배포
4. 프로덕션 스트레스 테스트

**상세 분석 보고서**: `docs/phase3-lua-redis-cluster-analysis.md`
