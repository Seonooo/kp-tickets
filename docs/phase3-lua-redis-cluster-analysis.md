# Phase 3: Lua Script Optimization + Redis Cluster 분석 보고서

## 목차
1. [Phase 3 개요](#phase-3-개요)
2. [Phase 3-2: Lua 스크립트 통합 최적화](#phase-3-2-lua-스크립트-통합-최적화)
3. [Phase 3-3: Redis Cluster 확장](#phase-3-3-redis-cluster-확장)
4. [성능 비교 분석](#성능-비교-분석)
5. [Redis Cluster TPS 미달 원인 분석](#redis-cluster-tps-미달-원인-분석)
6. [결론 및 권장사항](#결론-및-권장사항)

---

## Phase 3 개요

### 배경
Phase 2 종료 시점:
- **달성 TPS**: 4,345 (목표 5,000의 87%)
- **주요 이슈**: Redis 단일 인스턴스 처리량 한계 (~4,300 TPS)
- **개선 방향**: 네트워크 RTT 최적화 및 Redis 수평 확장

### Phase 3 전략
1. **Phase 3-2**: Lua 스크립트로 Redis 호출 횟수 감소 (6회 → 1회)
2. **Phase 3-3**: Redis Cluster 도입으로 처리량 확장

---

## Phase 3-2: Lua 스크립트 통합 최적화

### 문제 발견

Phase 2 메트릭 분석 결과, 대기열 진입 시 **불필요한 중복 Redis 호출** 발견:

```
EnterQueueService.enter() → 6회 Redis 호출
├─ 1. HGETALL active:token:{concertId}:userId  (Active 확인)
├─ 2. ZRANK queue:wait:{concertId} userId      (Wait 확인)
├─ 3. ZCARD queue:wait:{concertId}             (Wait 크기)
├─ 4. ZADD queue:wait:{concertId} score userId (신규 진입)
├─ 5. ZRANK queue:wait:{concertId} userId      (신규 순번)
└─ 6. ZCARD queue:wait:{concertId}             (전체 크기)
```

**문제점**:
- 네트워크 RTT 6회 발생 (각 ~1ms = 총 6ms 소요)
- 원자성 보장 없음 (경쟁 조건 가능성)
- Redis 처리 오버헤드 증가

### 해결 방안: enter_queue.lua

모든 검증 및 진입 로직을 **단일 Lua 스크립트**로 통합:

```lua
-- enter_queue.lua (queue-service/src/main/resources/scripts/)

-- 1. Active Token 확인
local activeToken = redis.call('HGETALL', activeTokenKey)
if #activeToken > 0 then
    -- 만료 확인 후 ACTIVE 반환
end

-- 2. Wait Queue 확인
local existingRank = redis.call('ZRANK', waitQueueKey, userId)
if existingRank then
    -- WAITING 반환
end

-- 3. Wait Queue 신규 진입
redis.call('ZADD', waitQueueKey, score, userId)
local newRank = redis.call('ZRANK', waitQueueKey, userId)
local totalWaiting = redis.call('ZCARD', waitQueueKey)

return cjson.encode({
    status = 'NEW',
    position = newRank,
    totalWaiting = totalWaiting
})
```

**효과**:
- Redis 호출 6회 → 1회 (83% 감소)
- 네트워크 RTT 5회 절약 (약 5ms 단축)
- 원자성 보장 (모든 연산이 단일 트랜잭션)

### 구현 상세

#### 1. RedisEnterQueueAdapter.java (NEW)

```java
@Component
@RequiredArgsConstructor
public class RedisEnterQueueAdapter {
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<String> enterQueueScript;

    public QueuePosition enterQueue(String concertId, String userId) {
        String activeTokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);
        String waitQueueKey = RedisKeyGenerator.waitQueueKey(concertId);

        List<String> keys = List.of(activeTokenKey, waitQueueKey);
        List<String> args = List.of(userId, timestamp, currentTime);

        // 단일 Lua 스크립트 실행!
        String jsonResult = redisTemplate.execute(
            enterQueueScript, keys, args.toArray(new String[0])
        );

        return parseScriptResult(concertId, userId, jsonResult);
    }
}
```

#### 2. EnterQueueService.java (REFACTORED)

**Before** (Phase 2):
```java
public QueuePosition enter(EnterQueueCommand command) {
    return queueEntryValidator.checkActiveUser(concertId, userId)
        .or(() -> queueEntryValidator.checkWaitingUser(concertId, userId))
        .orElseGet(() -> queueEntryProcessor.proceed(concertId, userId));
    // 각 단계마다 여러 Redis 호출
}
```

**After** (Phase 3-2):
```java
public QueuePosition enter(EnterQueueCommand command) {
    // 단일 Lua 스크립트로 모든 검증 및 진입 처리
    return redisEnterQueueAdapter.enterQueue(
        command.concertId(),
        command.userId()
    );
}
```

### Phase 3-2 테스트 결과

**테스트 설정**:
- Redis: 단일 인스턴스
- Queue Service: 2 instances
- Target TPS: 5,000
- Duration: 70초 (10s warmup + 60s peak)

**결과**:

| 지표 | Phase 2 | Phase 3-2 | 변화율 |
|------|---------|-----------|--------|
| **TPS** | 4,345 | 4,362.8 | +0.4% |
| **평균 응답시간** | 37.0ms | 22.69ms | **-38.7%** ✅ |
| **P95** | 292ms | 205.61ms | **-29.6%** |
| **P99** | 577ms | 468.66ms | **-18.8%** |
| **성공률** | 99.17% | 99.28% | +0.1% |

**핵심 발견**:
- ✅ **응답 시간 대폭 감소**: 네트워크 RTT 절감 효과 확인 (-38.7%)
- ❌ **TPS 미미한 증가**: Redis 처리량 병목 지속 (+0.4%)
- 📊 **원인 분석**: Redis 단일 인스턴스 처리 한계 (~4,300 TPS)

**결론**:
Lua 스크립트 최적화는 **사용자 경험 개선(latency 감소)**에는 성공했으나, **처리량 확장(TPS 증가)**을 위해서는 **Redis 수평 확장** 필요

---

## Phase 3-3: Redis Cluster 확장

### Redis Cluster 구성

기존에 구성되어 있던 `docker-compose.cluster.yml` 활용:

```yaml
Redis Cluster 구성:
- Master Nodes: 3개 (redis-node-1, 2, 3)
- Replica Nodes: 3개 (redis-node-4, 5, 6)
- 총 노드: 6개
- Hash Slots: 16384개 (각 Master가 1/3씩 담당)
- Replication: Master 1 : Replica 1
```

**Hash Tag 전략**:
```java
// Redis 키 설계에서 이미 구현됨
"queue:wait:{concertId}"        // {concertId}로 묶임
"active:token:{concertId}:userId"  // {concertId}로 묶음

→ 동일 concertId는 동일 Redis Master에 저장
→ Lua 스크립트 multi-key 연산 가능
```

### 인프라 확장

**Queue Service 스케일 아웃**:
```bash
docker-compose -f docker-compose.cluster.yml up -d --scale queue-service=4
```

**최종 구성**:
- Redis Cluster: 3 Master + 3 Replica (6 nodes)
- Queue Service: 4 instances
- Distributed Scheduler Lock: Redis SETNX 기반

### Phase 3-3 테스트 결과

**테스트 설정**:
- Redis: Cluster (3 Master + 3 Replica)
- Queue Service: 4 instances
- Target TPS: 5,000
- Duration: 70.1초 (10s warmup + 60s peak)

**최종 결과**:

| 지표 | Phase 3-2 | Phase 3-3 | 변화율 |
|------|-----------|-----------|--------|
| **TPS** | 4,362.8 | 4,406.2 | +1.0% |
| **평균 응답시간** | 22.69ms | 21.2ms | -6.6% |
| **P95** | 205.61ms | 130.73ms | **-36.4%** ✅ |
| **P99** | 468.66ms | 356.48ms | **-23.9%** ✅ |
| **성공률** | 99.28% | 99.64% | +0.4% |
| **HTTP 에러율** | 0.00% | 0.00% | - |
| **총 처리량** | 305,716 | 308,931 | +1.1% |

**임계값 달성 상황**:
- ✅ **P95 < 200ms**: 130.73ms (목표 대비 **34.6% 여유**)
- ✅ **P99 < 500ms**: 356.48ms (목표 대비 **28.7% 여유**)
- ✅ **에러율 < 5%**: 0.00%
- ✅ **성공률 > 95%**: 99.64%
- ⚠️ **TPS 5,000**: 4,406.2 (목표 대비 **88.1%**)

**Phase 2 대비 전체 개선율**:

| 지표 | Phase 2 | Phase 3-3 | 총 개선율 |
|------|---------|-----------|----------|
| TPS | 4,345 | 4,406.2 | +1.4% |
| 평균 응답시간 | 37.0ms | 21.2ms | **-42.7%** ✅ |
| P95 | 292ms | 130.73ms | **-55.2%** ✅ |
| P99 | 577ms | 356.48ms | **-38.1%** ✅ |

---

## 성능 비교 분석

### 전체 Phase 비교표

| Phase | 구성 | TPS | Avg RT | P95 | P99 | 성공률 |
|-------|------|-----|--------|-----|-----|--------|
| **Phase 2** | Redis 단일 + 2 instances | 4,345 | 37.0ms | 292ms | 577ms | 99.17% |
| **Phase 3-2** | Redis 단일 + Lua + 2 instances | 4,362.8 | 22.69ms | 205.61ms | 468.66ms | 99.28% |
| **Phase 3-3** | Redis Cluster + Lua + 4 instances | 4,406.2 | 21.2ms | 130.73ms | 356.48ms | 99.64% |

### 핵심 성과

1. **사용자 경험 대폭 개선**
   - 평균 응답 시간 **42.7% 단축** (37ms → 21.2ms)
   - P95 레이턴시 **55.2% 단축** (292ms → 130.73ms)
   - P99 레이턴시 **38.1% 단축** (577ms → 356.48ms)
   - **최초로 P95/P99 목표 달성** (P95 < 200ms, P99 < 500ms)

2. **안정성 향상**
   - 성공률 99.17% → 99.64% (+0.47%p)
   - HTTP 에러율 0.00% 유지
   - Dropped iterations 감소 (0.35%)

3. **Lua 스크립트 최적화 검증**
   - Redis 호출 횟수 83% 감소 (6회 → 1회)
   - 네트워크 RTT 5회 절약 효과 입증
   - 원자성 보장으로 데이터 정합성 향상

### 한계점

1. **TPS 목표 미달**
   - 목표: 5,000 TPS
   - 달성: 4,406.2 TPS (88.1%)
   - 부족분: 593.8 TPS (11.9%)

2. **Redis Cluster 기대치 미달**
   - 예상: ~13,000 TPS (3 Master × 4,300 TPS)
   - 실제: 4,406.2 TPS (예상의 33.9%)
   - 원인 분석 필요 (다음 섹션)

---

## Redis Cluster TPS 미달 원인 분석

### 예상 vs 실제

**예상 시나리오**:
```
Redis Cluster 3 Master Nodes
→ 각 Master가 독립적으로 4,300 TPS 처리
→ 총 처리량: 4,300 × 3 = 12,900 TPS (예상)
```

**실제 결과**:
```
Redis Cluster 3 Master Nodes
→ 총 처리량: 4,406.2 TPS
→ 예상 대비 34% 수준
```

### 원인 분석

#### 1. **Hash Tag로 인한 단일 샤드 집중** (주요 원인)

**현재 Redis 키 설계**:
```java
// 모든 키가 {concertId}로 hash tag 사용
"queue:wait:{concertId}"              // Wait Queue
"active:token:{concertId}:userId"     // Active Token
"queue:active:{concertId}"            // Active Queue
"stats:totalWaiting:{concertId}"      // Stats
```

**Hash Slot 분배 방식**:
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

**검증 방법**:
```bash
# Redis Cluster에서 키 분포 확인
redis-cli --cluster check localhost:6379

# 특정 키의 슬롯 확인
redis-cli cluster keyslot "queue:wait:{concert-1234}"
# → 항상 동일 슬롯 반환

# 슬롯별 키 개수 확인
redis-cli --cluster call localhost:6379 dbsize
# → Master-1: 1000+ keys, Master-2: 0, Master-3: 0 (예상)
```

#### 2. **Application Layer 병목**

Queue Service 4 instances가 동일한 처리 로직 수행:
- Java Virtual Thread 처리 한계
- Spring WebFlux Reactor 처리량
- JSON 파싱 오버헤드
- 비즈니스 로직 처리 시간

**현재 구성으로 추정되는 각 서비스 처리량**:
```
4,406.2 TPS ÷ 4 instances = 약 1,101 TPS/instance
→ 각 instance가 병목일 가능성
```

#### 3. **네트워크 및 Docker 오버헤드**

로컬 Docker Compose 환경의 한계:
- Container 간 가상 네트워크 레이턴시
- Docker Bridge Network 처리량 제한
- Host OS 리소스 경합 (모든 컨테이너가 동일 머신)

실제 프로덕션 환경(AWS ECS, ALB, ElastiCache)에서는 개선 가능

#### 4. **Lua 스크립트 실행 시간**

Lua 스크립트가 Redis 내에서 순차 실행:
```lua
1. HGETALL (Active 확인)
2. ZRANK (Wait 확인)
3. ZADD + ZRANK + ZCARD (진입 처리)
```

단일 호출이지만 Redis 내부에서 여러 연산 수행:
- 기존 6회 네트워크 호출 → 1회로 감소 (RTT 절약)
- 하지만 Redis CPU 사용량은 동일하거나 증가 가능

### 결론: Redis Cluster의 실질적 효과

**현재 테스트 환경에서**:
- ❌ TPS 증가 효과 미미: +1.0% (단일 샤드 집중)
- ✅ 레이턴시 개선: P95 -36.4%, P99 -23.9% (복제본 읽기 효과)
- ✅ 고가용성 확보: Master 장애 시 Replica 자동 승격

**Redis Cluster가 진정한 효과를 보려면**:

1. **다중 콘서트 동시 테스트**
   ```javascript
   // K6 스크립트 수정
   const concertIds = ['concert-A', 'concert-B', 'concert-C'];
   const concertId = concertIds[Math.floor(Math.random() * 3)];

   // 3개 콘서트 → 각각 다른 Hash Slot → 3개 Master에 분산
   // 예상 TPS: 4,300 × 3 = 12,900 TPS
   ```

2. **프로덕션 환경 배포**
   - AWS ElastiCache Redis Cluster
   - ECS 기반 Queue Service 수평 확장
   - ALB 기반 로드 밸런싱
   - 네트워크 오버헤드 최소화

---

## 결론 및 권장사항

### Phase 3 종합 평가

**성공한 부분** ✅:
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

**개선 필요 부분** ⚠️:
1. **TPS 목표 미달**
   - 현재: 4,406.2 TPS (88.1%)
   - 목표: 5,000 TPS
   - 부족분: 593.8 TPS

2. **Redis Cluster 효과 제한적**
   - 단일 콘서트 테스트로 단일 샤드 집중
   - 3 Master 중 1개만 활용

### 5,000 TPS 달성을 위한 권장사항

#### 옵션 1: 다중 콘서트 테스트 (즉시 가능) ⭐

**구현 방법**:
```javascript
// k6-tests/queue-entry-scale-test.js 수정
export default function () {
    const concertIds = [
        'concert-alpha',
        'concert-beta',
        'concert-gamma'
    ];
    const concertId = concertIds[Math.floor(Math.random() * 3)];

    const userId = `user-${__VU}-${__ITER}`;
    enterQueue(concertId, userId);
}
```

**예상 효과**:
- 3개 콘서트 → 3개 Redis Master에 균등 분산
- 각 Master: 4,300 TPS 처리 가능
- **예상 총 TPS: 12,900 TPS** (목표 대비 258%)

**장점**:
- 코드 수정 최소 (테스트 스크립트만)
- 즉시 실행 가능
- Redis Cluster 진정한 효과 검증

**단점**:
- 실제 프로덕션 시나리오와 다를 수 있음 (동시 다발 콘서트)
- 단일 콘서트 폭주 상황 대응 불가

#### 옵션 2: Queue Service 추가 스케일 아웃

**구현 방법**:
```bash
docker-compose -f docker-compose.cluster.yml up -d --scale queue-service=8
```

**예상 효과**:
- 현재: 4 instances (각 1,101 TPS)
- 변경: 8 instances
- **예상 TPS: 4,406 × 2 = 8,800 TPS**

**장점**:
- 단일 콘서트 폭주 상황 대응 가능
- Application Layer 병목 해소

**단점**:
- 여전히 Redis 단일 Master 병목 존재
- 스케줄러 락 경합 증가 가능

#### 옵션 3: Redis Pipeline 추가 적용

Lua 스크립트와 무관한 다른 연산에 Pipeline 적용:
```java
// 예: 토큰 조회 시 여러 사용자 배치 처리
redisTemplate.executePipelined(callback);
```

**예상 효과**: +5~10% TPS

#### 옵션 4: 프로덕션 환경 배포 (최종 목표)

**AWS 구성**:
```
ALB → ECS (Queue Service Auto Scaling)
       ↓
    ElastiCache Redis Cluster
    (3 Master + 3 Replica, cache.r7g.large)
```

**예상 효과**:
- Docker 네트워크 오버헤드 제거
- ECS Fargate 고성능 컴퓨팅
- ElastiCache 최적화된 Redis
- **예상 TPS: 15,000~20,000+**

### 최종 권장 실행 계획

**Phase 4: 다중 콘서트 테스트 (단기)**
1. K6 스크립트 수정 (3개 콘서트 랜덤)
2. Redis Cluster 성능 재측정
3. TPS 5,000 돌파 확인
4. 콘서트별 처리량 분석

**Phase 5: 프로덕션 준비 (중기)**
1. AWS 인프라 구성 (Terraform/CDK)
2. ElastiCache Redis Cluster 설정
3. ECS 기반 Queue Service 배포
4. ALB 기반 로드 밸런싱
5. CloudWatch 모니터링 대시보드
6. 프로덕션 스트레스 테스트

**Phase 6: 최적화 고도화 (장기)**
1. Redis Pipeline 적용 (읽기 연산)
2. CDC 기반 비동기 처리 (Kafka/SQS)
3. Read Replica 활용 (읽기 부하 분산)
4. Circuit Breaker 패턴 적용
5. Rate Limiting 고도화

---

## 부록: Phase 3 상세 메트릭

### Phase 3-2 상세 결과

```
TPS: 4,362.8 (목표 대비 87.3%)
Duration: 70초
Total Processed: 305,716 requests
Success Rate: 99.28%

Response Time:
- Average: 22.69ms
- Median: 16.00ms
- P90: 93.41ms
- P95: 205.61ms
- P99: 468.66ms
- Max: 1.61s

HTTP Errors: 0 (0.00%)
Dropped Iterations: 2,189 (0.71%)
```

### Phase 3-3 상세 결과

```
TPS: 4,406.2 (목표 대비 88.1%)
Duration: 70.1초
Total Processed: 308,931 requests
Success Rate: 99.64%

Response Time:
- Average: 21.2ms
- Median: 14.5ms
- P90: 77.2ms
- P95: 130.73ms ✅
- P99: 356.48ms ✅
- Max: 1.42s

HTTP Errors: 0 (0.00%)
Dropped Iterations: 1,072 (0.35%)

Infrastructure:
- Redis: Cluster (3 Master + 3 Replica)
- Queue Service: 4 instances
- Scheduler: Distributed Lock (Redis SETNX)
- Database: PostgreSQL (not bottleneck)
```

---

## 참고 문서

- [Phase 2 성능 테스트 결과](./phase2-horizontal-scaling-analysis.md)
- [성능 개선 계획](./performance-improvement-plan.md)
- [Phase 1 베이스라인 테스트](./phase1-baseline-test-result.md)
- [enter_queue.lua 스크립트](../queue-service/src/main/resources/scripts/enter_queue.lua)
- [RedisEnterQueueAdapter.java](../queue-service/src/main/java/personal/ai/queue/adapter/out/redis/RedisEnterQueueAdapter.java)
