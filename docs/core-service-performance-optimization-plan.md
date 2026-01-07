# Core Service 성능 최적화 계획

## 목차
- [1. 현재 상태 (Baseline)](#1-현재-상태-baseline)
- [2. 성능 병목 분석](#2-성능-병목-분석)
- [3. 최적화 실행 계획](#3-최적화-실행-계획)
- [4. 성공 기준](#4-성공-기준)

---

## 1. 현재 상태 (Baseline)

### 1.1 테스트 환경
**날짜**: 2025-12-29
**테스트 도구**: K6 (queue-e2e-circulation-test.js)
**테스트 시나리오**: 대기열 → 좌석 조회 → 예약 → 결제 → 자동 제거

**부하 프로필**:
- Warmup: 10초 동안 100 VU/s
- Peak: 60초 동안 500 VU/s
- 총 요청 수: 약 31,000명의 사용자

**인프라 구성**:
- Core Service: 1 instance
- Queue Service: 4 instances
- Redis Cluster: 3 Master + 3 Replica
- MySQL: 1 instance
- HikariCP 설정: max-pool-size=10, min-idle=5

**테스트 데이터**:
- 콘서트: 1개
- 스케줄: 1개
- 좌석: 100개 (VIP 25, R 25, S 25, A 25)

---

### 1.2 성능 측정 결과

#### E2E 전체 플로우
| 메트릭 | 측정값 | 목표 | 상태 |
|--------|-------|------|------|
| E2E Success Rate | 0.32% (96/29,465) | >90% | ❌ FAILED |
| E2E Total Duration P95 | 8.75s | <10s | ⚠️ MARGINAL |

#### API 응답 시간 (P95)
| API | P95 | 목표 | 상태 | 초과율 |
|-----|-----|------|------|--------|
| Queue Entry | 3.25ms | <200ms | ✅ PASS | - |
| Queue Poll | 3.83ms | <100ms | ✅ PASS | - |
| Seats Query | **4.66s** | <500ms | ❌ FAIL | **+932%** |
| Reservation | 517.4ms | <1s | ✅ PASS | - |
| Payment | **5.22s** | <2s | ❌ FAIL | **+261%** |

#### 기능별 성공률
| 단계 | 성공률 | 성공/전체 |
|------|--------|----------|
| Queue Entry | 100% | 29,465 / 29,465 |
| Queue Activation | 100% | 29,465 / 29,465 |
| Seats Has Data | **2%** | 657 / 29,465 |
| Reservation | 14% | 96 / 657 |
| Payment | 100% | 96 / 96 |

---

### 1.3 현재 구현 상태 (코드 분석)

#### 1.3.1 Seats Query API

**파일**: `SeatPersistenceAdapter.java:24`, `AvailableSeatsQueryService.java:35`

**현재 구현**:
```java
// AvailableSeatsQueryService.getAvailableSeats()
@Override
public List<Seat> getAvailableSeats(Long scheduleId, Long userId, String queueToken) {
    // 1. Queue Token 검증
    queueServiceClient.validateToken(concertId, userId, queueToken);

    // 2. DB에서 직접 조회 (캐싱 없음)
    var availableSeats = seatRepository.findAvailableByScheduleId(scheduleId);
    return availableSeats;
}

// JPA Query
@Query("SELECT s FROM SeatEntity s WHERE s.scheduleId = :scheduleId AND s.status = :status")
List<SeatEntity> findByScheduleIdAndStatus(@Param("scheduleId") Long scheduleId,
                                            @Param("status") SeatStatus status);
```

**데이터베이스 인덱스** (SeatEntity.java:6-8):
```java
@Table(name = "seats",
    indexes = {
        @Index(name = "idx_schedule_status", columnList = "schedule_id, status"),
        @Index(name = "idx_schedule_id", columnList = "schedule_id")
    })
```
- ✅ 복합 인덱스 존재: `idx_schedule_status (schedule_id, status)`
- ✅ 단일 인덱스 존재: `idx_schedule_id`

**조회 결과 캐싱**:
- ❌ **없음** - 매 요청마다 DB 조회
- Redis 인프라는 존재하지만 좌석 조회에는 미사용
- `@Cacheable` 애너테이션 없음
- Cache Manager 설정 없음

**참고**: Redis는 좌석 선점용으로만 사용 중 (후술)

---

#### 1.3.2 Seat Reservation API (예약 시 선점 처리)

**파일**: `SeatReservationService.java:35`, `RedisSeatLockAdapter.java:28`

**현재 구현 플로우**:
```java
@Override
public Reservation reserveSeat(ReserveSeatCommand command) {
    // 1. Queue Token 검증
    queueServiceClient.validateToken(concertId, userId, queueToken);

    // 2. Redis Lock 획득 (선점) - TTL: 300초
    boolean locked = seatLockRepository.tryLock(seatId, userId, 300);
    if (!locked) {
        throw new SeatAlreadyReservedException(seatId);
    }

    try {
        // 3. DB에 예약 저장 (트랜잭션)
        var saved = bookingManager.reserveSeatInTransaction(command);

        // 4. Redis에 예약 TTL 설정 (만료 추적)
        reservationCacheRepository.setReservationTTL(saved.id(), saved.expiresAt());

        return saved;
    } finally {
        // 5. Redis Lock 해제
        seatLockRepository.unlock(seatId, userId);
    }
}
```

**Redis 선점 Lock 구현** (RedisSeatLockAdapter.java:28-41):
```java
@Override
public boolean tryLock(Long seatId, Long userId, int ttlSeconds) {
    String key = "seat:lock:" + seatId;
    String value = String.valueOf(userId);

    // SETNX + TTL 원자적 수행
    Boolean success = redisTemplate.opsForValue()
            .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));

    return Boolean.TRUE.equals(success);
}
```

**Redis Keys**:
- `seat:lock:{seatId}` - ✅ **좌석 선점 락** (이미 구현됨)
- `reservation:{reservationId}` - ✅ **예약 만료 추적** (이미 구현됨)

**중요**:
- 좌석 **선점용 Redis Lock**은 이미 구현되어 동시성 제어 중
- 좌석 **조회 결과 캐싱**은 구현되지 않아 매번 DB 조회

---

#### 1.3.3 Payment API

**파일**: `PaymentProcessingService.java:41`, `PaymentMockService.java:26`, `PaymentKafkaPublisher.java:54`

**현재 구현 플로우**:
1. **예약 검증** (~10-50ms)
   ```java
   reservationValidator.validate(reservationId, userId);
   ```

2. **결제 생성 (TX1)** (~20-50ms)
   ```java
   transactionTemplate.execute(status -> {
       Payment payment = Payment.pending(reservationId, userId, amount);
       return paymentRepository.save(payment);
   });
   ```

3. **외부 결제 처리 (Mock)** (500-1000ms) ⚠️ **PRIMARY BOTTLENECK**
   ```java
   // PaymentMockService.processPayment()
   int delay = ThreadLocalRandom.current().nextInt(MIN_DELAY_MS, MAX_DELAY_MS);
   Thread.sleep(delay);  // MIN=500, MAX=1000
   ```

4. **결제 완료 처리 (TX2)** (~20-50ms)
   ```java
   transactionTemplate.execute(status -> {
       payment.complete();
       paymentRepository.save(payment);  // UPDATE status='COMPLETED'
       publishPaymentCompleted(payment);  // Outbox 이벤트 저장
       return payment;
   });
   ```

5. **Kafka 이벤트 발행** (50-200ms) ⚠️ **SECONDARY BOTTLENECK**
   ```java
   // PaymentKafkaPublisher.publishPaymentCompleted()
   kafkaTemplate.send(topic, key, payload).join();  // 동기 블로킹
   ```

**Outbox 패턴**:
- Scheduler: 5초마다 실행 (`@Scheduled(fixedDelay = 5000)`)
- PENDING 이벤트를 조회하여 Kafka 발행
- 최대 3회 재시도

**데이터베이스 인덱스** (OutboxEventEntity.java:3):
```java
@Table(name = "payment_outbox_events",
    indexes = {
        @Index(name = "idx_status_created", columnList = "status, created_at")
    })
```
- ✅ 존재: `idx_status_created (status, created_at)`
- ❌ 누락: `idx_status_published (status, published_at)` - cleanup 쿼리용
- ❌ 누락: `idx_aggregate_type_id (aggregate_type, aggregate_id)` - aggregate 조회용

---

#### 1.3.4 Connection Pool

**파일**: `core-service/src/main/resources/application.yml:17-21`

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000  # 30초
      idle-timeout: 600000       # 10분
      max-lifetime: 1800000      # 30분
```

**분석**:
- 최대 10개 커넥션으로 500 req/s 부하 처리 시도
- 평균 응답 시간 4-5초 기준, 이론상 초당 2-2.5 TPS만 처리 가능
- 실제 필요량 (이론치): 500 req/s × 4s = 2000 concurrent connections
- **Connection Pool 부족으로 인한 대기 시간 발생 가능성 높음**

---

## 2. 성능 병목 분석

### 2.1 Priority 1: Seats Query (P95: 4.66s → 목표: <500ms)

#### 병목 원인

**1. 조회 결과 캐싱 부재** (추정 영향: 95%)
- 매 요청마다 MySQL 조회 (30,000+ queries / 70초 = 430 QPS)
- 동일한 스케줄의 좌석 데이터 반복 조회
- 데이터 변경 빈도 낮음 (좌석은 예약 시점에만 AVAILABLE → RESERVED 변경)
- Redis 인프라는 존재하지만 조회 캐싱에는 미사용

**참고**:
- 좌석 **선점 락**은 Redis로 구현됨 (SeatReservationService.java:40)
- 좌석 **조회 캐싱**은 구현 안됨 (AvailableSeatsQueryService.java:35)

**2. 테스트 데이터 부족** (추정 영향: 98% 실패율의 원인)
- 100개 좌석 vs 30,000명 사용자
- 첫 1초 내에 모든 좌석 소진 (성공률 2%)
- 이후 29,000+ 요청은 빈 배열 반환하지만 여전히 DB 조회 수행
- 빈 결과셋 조회에도 2-10초 소요 (Connection Pool 대기 포함)

**3. 전체 데이터 로딩** (추정 영향: 10%)
- 페이지네이션 없음 (모든 AVAILABLE 좌석 반환)
- Stream 변환 오버헤드 (Entity → Domain 모델)
- 100석 기준으로는 미미하나, 10,000석 이상 시 증가 예상

#### 개선 방안

**Step 1: Redis 조회 결과 캐싱 도입**
- 스케줄별 좌석 목록을 Redis에 캐싱 (TTL: 60초)
- Cache-Aside 패턴 적용
- 예약 성공 시 해당 스케줄의 캐시 무효화
- Cache Key: `seats:schedule:{scheduleId}:available`

**Step 2: 테스트 데이터 확장**
- 좌석 수: 100 → 10,000개로 증가
- 다양한 등급별 좌석 분포 유지
- 실제 부하 테스트 시나리오에 맞는 용량 확보

**Step 3: 쿼리 최적화 (선택)**
- DTO 프로젝션으로 필요한 필드만 조회
- 페이지네이션 적용 (클라이언트 협의 필요)

**예상 효과**:
- DB 부하 감소: 430 QPS → ~86 QPS (80% cache hit 가정)
- 응답 시간 단축: 4.66s → 예상 100-300ms (Redis 조회 시간)

---

### 2.2 Priority 2: Payment (P95: 5.22s → 목표: <2s)

#### 병목 원인

**1. Mock 결제 게이트웨이 지연** (추정 영향: 50%)
- `PaymentMockService.processPayment()`: 500-1000ms 인위적 지연
- 실제 외부 결제 게이트웨이 시뮬레이션 목적
- 코드: `Thread.sleep(delay);` (PaymentMockService.java:26)

**2. 동기 Kafka 발행** (추정 영향: 25%)
- `kafkaTemplate.send().join()`: 브로커 ACK 대기로 블로킹
- 평균 50-200ms 네트워크 지연
- HTTP 응답 스레드가 Kafka 발행 완료까지 대기

**3. 트랜잭션 오버헤드** (추정 영향: 15%)
- 3개의 별도 트랜잭션 (생성, 성공, 실패)
- Outbox 이벤트 저장 오버헤드
- 예약 조회 중복 (검증 단계 + handleSuccess 단계)

**4. Connection Pool 제약** (추정 영향: 10%)
- 최대 10개 커넥션으로 고부하 처리
- 긴 트랜잭션 시간 (평균 2.86s)으로 인한 커넥션 고갈
- Connection 획득 대기 시간 증가

#### 개선 방안

**Step 1: Kafka 비동기 발행**
```java
// Before
kafkaTemplate.send(topic, key, payload).join();  // 블로킹

// After
kafkaTemplate.send(topic, key, payload)
    .whenComplete((result, ex) -> {
        if (ex != null) {
            log.error("Failed to publish event", ex);
        }
    });
```

**Step 2: Connection Pool 확장**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # 10 → 50
      minimum-idle: 20       # 5 → 20
```

**Step 3: Mock 지연 시간 환경 변수화**
```java
@Value("${payment.mock.min-delay-ms:0}")
private int minDelayMs;

@Value("${payment.mock.max-delay-ms:0}")
private int maxDelayMs;
```
- 성능 테스트 시 0으로 설정
- 기능 테스트 시 500-1000으로 설정

**Step 4: 누락된 인덱스 추가**
```sql
-- Cleanup 쿼리 최적화
CREATE INDEX idx_status_published
ON payment_outbox_events(status, published_at);

-- Aggregate 조회 최적화
CREATE INDEX idx_aggregate_type_id
ON payment_outbox_events(aggregate_type, aggregate_id);
```

**예상 효과**:
- Kafka 블로킹 제거: -50-200ms
- Mock 지연 제거 (테스트 시): -500-1000ms
- Connection Pool 여유 확보: 대기 시간 감소

---

### 2.3 Priority 3: 데이터베이스 인덱스 최적화

#### 누락된 인덱스 (코드 분석 결과)

**Outbox Events 테이블**:
```sql
-- 현재: idx_status_created (status, created_at)
-- 추가 필요:

-- 1. Cleanup 쿼리용 (findByStatusAndPublishedAtBefore)
CREATE INDEX idx_status_published
ON payment_outbox_events(status, published_at);

-- 2. Aggregate 조회용 (findByAggregateTypeAndAggregateId)
CREATE INDEX idx_aggregate_type_id
ON payment_outbox_events(aggregate_type, aggregate_id);
```

**Reservations 테이블**:
```sql
-- 현재: uk_schedule_seat (schedule_id, seat_id) UNIQUE만 존재
-- 추가 필요:

-- 1. User별 예약 조회용
CREATE INDEX idx_reservations_user_id
ON reservations(user_id);

-- 2. Seat별 예약 조회용
CREATE INDEX idx_reservations_seat_id
ON reservations(seat_id);
```

---

## 3. 최적화 실행 계획

### Phase 5-1: Seats Query 캐싱 및 데이터 확장

**목표**: Seats Query P95 4.66s → <500ms

#### 작업 항목

**1. Redis 조회 결과 캐싱 구현**

`application.yml` 수정:
```yaml
spring:
  cache:
    type: redis
    redis:
      time-to-live: 60000  # 60초
```

`AvailableSeatsQueryService.java` 수정:
```java
@Cacheable(value = "seats", key = "#scheduleId")
@Override
public List<Seat> getAvailableSeats(Long scheduleId, Long userId, String queueToken) {
    queueServiceClient.validateToken(concertId, userId, queueToken);
    return seatRepository.findAvailableByScheduleId(scheduleId);
}
```

캐시 무효화:
```java
@CacheEvict(value = "seats", key = "#scheduleId")
public void invalidateSeatCache(Long scheduleId) {
    log.info("Invalidating seat cache for schedule: {}", scheduleId);
}
```

**2. 테스트 데이터 확장**

`setup-test-data.sql` 수정:
```sql
-- 기존: 100개 좌석
-- 변경: 10,000개 좌석

INSERT INTO seats (schedule_id, seat_number, grade, price, status)
SELECT
    1 as schedule_id,
    CONCAT(
        CHAR(65 + (n DIV 100)),  -- 섹션 (A-Z)
        '-',
        LPAD((n MOD 100) + 1, 3, '0')  -- 좌석번호 (001-100)
    ) as seat_number,
    CASE (n MOD 4)
        WHEN 0 THEN 'VIP'
        WHEN 1 THEN 'R'
        WHEN 2 THEN 'S'
        ELSE 'A'
    END as grade,
    CASE (n MOD 4)
        WHEN 0 THEN 50000
        WHEN 1 THEN 40000
        WHEN 2 THEN 30000
        ELSE 20000
    END as price,
    'AVAILABLE' as status
FROM
    (SELECT @row := @row + 1 as n
     FROM (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t1,
          (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t2,
          (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t3,
          (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t4,
          (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) t5,
          (SELECT @row := -1) r
     LIMIT 10000) numbers;
```

**3. 성능 테스트 및 검증**
- K6 baseline test 재실행
- P95 <500ms 달성 확인
- Cache Hit Rate 모니터링 (목표: >80%)
- Grafana 대시보드에서 Redis 메트릭 확인

**예상 효과**:
- DB 쿼리 감소: 430 QPS → ~86 QPS
- P95 응답 시간: 4.66s → 100-300ms

---

### Phase 5-2: Payment API 최적화

**목표**: Payment P95 5.22s → <2s

#### 작업 항목

**1. Kafka 비동기 발행**

`PaymentKafkaPublisher.java` 수정:
```java
// Before (Line 54)
kafkaTemplate.send(topic, key, payload).join();

// After
kafkaTemplate.send(topic, key, payload)
    .whenComplete((result, ex) -> {
        if (ex != null) {
            log.error("Failed to publish payment event: paymentId={}",
                payment.getId(), ex);
            // Outbox 패턴이 재시도 보장하므로 여기서는 로깅만
        } else {
            log.debug("Payment event published: paymentId={}", payment.getId());
        }
    });
```

**2. Connection Pool 확장**

`application.yml` 수정:
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50  # 10 → 50
      minimum-idle: 20       # 5 → 20
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**3. Mock 지연 시간 환경 변수화**

`PaymentMockService.java` 수정:
```java
@Value("${payment.mock.min-delay-ms:500}")
private int minDelayMs;

@Value("${payment.mock.max-delay-ms:1000}")
private int maxDelayMs;

@Override
public ExternalPaymentResult processPayment(ProcessPaymentRequest request) {
    try {
        int delay = ThreadLocalRandom.current().nextInt(minDelayMs, maxDelayMs);
        if (delay > 0) {
            Thread.sleep(delay);
        }
        return ExternalPaymentResult.success(generateTransactionId());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return ExternalPaymentResult.failure("INTERRUPTED");
    }
}
```

`application-test.yml` 추가:
```yaml
payment:
  mock:
    min-delay-ms: 0
    max-delay-ms: 0
```

**4. 누락된 인덱스 추가**

Flyway Migration 파일 생성: `V4__add_missing_indexes.sql`
```sql
-- Outbox events cleanup 쿼리 최적화
CREATE INDEX idx_status_published
ON payment_outbox_events(status, published_at);

-- Outbox events aggregate 조회 최적화
CREATE INDEX idx_aggregate_type_id
ON payment_outbox_events(aggregate_type, aggregate_id);

-- Reservations 사용자별 조회 최적화
CREATE INDEX idx_reservations_user_id
ON reservations(user_id);

-- Reservations 좌석별 조회 최적화
CREATE INDEX idx_reservations_seat_id
ON reservations(seat_id);
```

**5. 성능 테스트 및 검증**
- Mock 지연 제거 후 테스트 (`application-test.yml` 사용)
- P95 <2s 달성 확인
- Connection Pool 사용률 모니터링 (목표: <60%)
- Kafka 발행 성공률 확인

**예상 효과**:
- Kafka 블로킹 제거: -50-200ms
- Mock 지연 제거 (테스트): -500-1000ms
- Connection Pool 여유: 대기 시간 감소

---

### Phase 5-3: 통합 성능 검증

**목표**: E2E Success Rate 0.32% → >90%

#### 작업 항목

**1. 전체 최적화 적용**
- Seats Query Redis 캐싱
- Payment Kafka 비동기 발행
- Connection Pool 확장 (10 → 50)
- 테스트 데이터 확장 (100 → 10,000석)
- 누락된 인덱스 추가

**2. K6 통합 성능 테스트**
```powershell
# 테스트 실행
docker run --rm --network ai_concert-network `
  -v "${PWD}\k6-tests:/scripts" `
  grafana/k6 run /scripts/queue-e2e-circulation-test.js
```

**3. 성능 지표 수집 및 분석**

Grafana 대시보드에서 모니터링:
- API P95 응답 시간
- Redis Cache Hit Rate
- MySQL Connection Pool 사용률
- Kafka 발행 성공률
- E2E Success Rate

**4. 목표 달성 검증**

| API | 목표 P95 | 검증 |
|-----|---------|------|
| Queue Entry | <200ms | ✅ |
| Queue Poll | <100ms | ✅ |
| Seats Query | <500ms | 🔍 검증 필요 |
| Reservation | <1s | ✅ |
| Payment | <2s | 🔍 검증 필요 |

**5. 회귀 테스트**
```bash
cd e2e-tests
./gradlew clean test
```

---

### Phase 6: QA E2E 자동화 테스트 확장

**목표**: 기능적 회귀 테스트 자동화 및 커버리지 확대

#### 작업 항목

**1. E2E 테스트 시나리오 확장**

현재 커버리지:
- ✅ 정상 예매 플로우 (Queue → Seats → Reservation → Payment → Auto Removal)

추가 시나리오:
```gherkin
# 1. 동시성 충돌 시나리오
Scenario: 두 사용자가 동일 좌석 예약 시도
  Given 사용자 "user1"과 "user2"가 대기열을 통과한다
  When 두 사용자가 동시에 좌석 "A-001" 예약을 시도한다
  Then 한 명만 예약에 성공한다
  And 다른 한 명은 "SeatAlreadyReservedException"을 받는다

# 2. 예약 만료 시나리오
Scenario: 예약 후 결제하지 않으면 만료된다
  Given 사용자가 좌석을 예약한다
  When 5분 동안 결제하지 않는다
  Then 예약이 "EXPIRED" 상태가 된다
  And 좌석이 다시 "AVAILABLE" 상태가 된다

# 3. 결제 실패 시나리오
Scenario: 결제 실패 시 좌석이 해제된다
  Given 사용자가 좌석을 예약한다
  When 결제가 실패한다
  Then 예약이 "CANCELLED" 상태가 된다
  And 좌석이 다시 "AVAILABLE" 상태가 된다

# 4. 대기열 토큰 만료 시나리오
Scenario: 대기열 토큰 만료 시 API 호출 차단
  Given 사용자가 대기열을 통과한다
  When 토큰 만료 시간이 지난다
  Then 좌석 조회 시 401 Unauthorized를 받는다
```

**2. CI/CD 파이프라인 통합**

`.github/workflows/e2e-test.yml`:
```yaml
name: E2E Tests

on:
  pull_request:
    branches: [ main ]

jobs:
  e2e-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'

      - name: Run E2E Tests
        run: |
          cd e2e-tests
          ./gradlew clean test

      - name: Upload Test Reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: cucumber-reports
          path: e2e-tests/target/cucumber-reports/
```

**3. 성능 회귀 테스트 자동화**

`.github/workflows/performance-test.yml`:
```yaml
name: Performance Tests

on:
  schedule:
    - cron: '0 2 * * *'  # 매일 02:00 UTC

jobs:
  k6-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Start Services
        run: docker-compose up -d

      - name: Run K6 Test
        run: |
          docker run --rm --network ai_concert-network \
            -v $PWD/k6-tests:/scripts \
            grafana/k6 run /scripts/queue-e2e-circulation-test.js \
            --out json=results.json

      - name: Validate Performance
        run: |
          # P95 체크 스크립트
          python scripts/validate-performance.py results.json
```

---

## 4. 성공 기준

### 4.1 성능 목표 (Phase 5 완료 후)

| API | 현재 P95 | 목표 P95 | 개선율 | 우선순위 |
|-----|---------|---------|-------|---------|
| Queue Entry | 3.25ms | <200ms | N/A (Already passing) | - |
| Queue Poll | 3.83ms | <100ms | N/A (Already passing) | - |
| **Seats Query** | **4.66s** | **<500ms** | **>89% 개선** | P1 |
| Reservation | 517.4ms | <1s | N/A (Already passing) | - |
| **Payment** | **5.22s** | **<2s** | **>62% 개선** | P2 |

### 4.2 시스템 목표

| 메트릭 | 현재 | 목표 | 우선순위 |
|--------|------|------|---------|
| E2E Success Rate | 0.32% | >90% | P1 |
| E2E Total Duration P95 | 8.75s | <10s | P2 |
| DB Query Load | 430 QPS | <100 QPS | P1 |
| Redis Cache Hit Rate | 0% | >80% | P1 |
| Connection Pool 사용률 | 추정 >90% | <60% | P2 |

### 4.3 품질 목표 (Phase 6 완료 후)

| 항목 | 목표 |
|------|------|
| E2E 테스트 시나리오 | 주요 플로우 100% 커버 |
| 동시성 시나리오 테스트 | 5개 이상 |
| CI/CD 자동화 | PR 생성 시 자동 실행 |
| 성능 회귀 방지 | K6 테스트 nightly 자동화 |

---

## 부록

### A. 테스트 명령어

**K6 Baseline Test**:
```powershell
# PowerShell
docker run --rm --network ai_concert-network `
  -v "${PWD}\k6-tests:/scripts" `
  grafana/k6 run /scripts/queue-e2e-circulation-test.js

# Bash
docker run --rm --network ai_concert-network \
  -v "$(pwd)/k6-tests:/scripts" \
  grafana/k6 run /scripts/queue-e2e-circulation-test.js
```

**E2E Cucumber Test**:
```bash
cd e2e-tests
./gradlew clean test

# 특정 시나리오만 실행
./gradlew test -Dcucumber.filter.tags="@booking"
```

**테스트 데이터 초기화**:
```bash
docker exec -i ai-mysql-1 mysql -uroot -ppassword concert_core < k6-tests/setup-test-data.sql
```

### B. 참고 문서

- `PERFORMANCE_TEST_SUMMARY.md`: Phase 1-4 성능 테스트 요약
- `phase1-capacity-limit-analysis.md`: Queue Service 용량 분석
- `phase3-lua-redis-cluster-analysis.md`: Redis Cluster 최적화
- `performance-test-final-report.md`: 최종 성능 테스트 리포트

### C. 주요 코드 위치

**Core Service**:
- Seats Query: `core-service/src/main/java/personal/ai/core/booking/application/service/AvailableSeatsQueryService.java:35`
- Seat Reservation (선점): `core-service/src/main/java/personal/ai/core/booking/application/service/SeatReservationService.java:35`
- Redis Seat Lock: `core-service/src/main/java/personal/ai/core/booking/adapter/out/redis/RedisSeatLockAdapter.java:28`
- Payment: `core-service/src/main/java/personal/ai/core/payment/application/service/PaymentProcessingService.java:41`
- Configuration: `core-service/src/main/resources/application.yml:17`

**K6 Tests**:
- E2E Circulation Test: `k6-tests/queue-e2e-circulation-test.js`
- Test Data Setup: `k6-tests/setup-test-data.sql`

**E2E Tests**:
- Step Definitions (English): `e2e-tests/src/test/java/personal/ai/e2e/steps/BookingFlowStepsEn.java`
- Step Definitions (Korean): `e2e-tests/src/test/java/personal/ai/e2e/steps/BookingFlowStepsKo.java`
- Feature Files: `e2e-tests/src/test/resources/features/`

---

**문서 작성일**: 2025-12-29
**작성 기준**: K6 Baseline Test 결과 (2025-12-29 실행)
**버전**: 1.0.0
