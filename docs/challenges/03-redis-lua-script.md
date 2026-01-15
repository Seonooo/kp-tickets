# Redis Lua Script 최적화

**문제 해결 과정**: Redis 호출 83% 감소 (6회 → 1회), 평균 응답시간 38.7% 단축

---

## 📌 비즈니스 요구사항

### 배경
[DB Pool 튜닝](02-db-pool-tuning.md)으로 예매 성공률 95.62%를 달성했지만, **대기열 진입 응답시간이 여전히 느렸습니다** (P95 205ms).

비즈니스 관점에서 **빠른 대기열 진입**은 사용자 이탈 방지의 핵심입니다:
- 3초 이상 대기 시 50% 이탈
- 대기열 진입조차 느리면 사용자 불만 증가

### 목표
- **대기열 진입 P95 < 200ms**
- **평균 응답시간 < 50ms**
- **원자성 보장** (경합 조건 제거)

---

## 🔍 문제 발견

### Redis 호출 패턴 분석

**대기열 진입 시 Java 코드**
```java
// EnterQueueService.java
public QueueEntryResult enter(String concertId, String userId) {
    // 1. Active Token 확인 (Redis 호출 1회)
    Map<String, String> activeToken = redisTemplate.opsForHash()
        .entries("active:token:" + concertId + ":" + userId);

    if (!activeToken.isEmpty()) {
        return QueueEntryResult.alreadyActive(activeToken);
    }

    // 2. Wait Queue 확인 (Redis 호출 2회)
    Long existingRank = redisTemplate.opsForZSet()
        .rank("queue:wait:" + concertId, userId);

    if (existingRank != null) {
        Long totalWaiting = redisTemplate.opsForZSet()
            .zCard("queue:wait:" + concertId);
        return QueueEntryResult.alreadyWaiting(existingRank, totalWaiting);
    }

    // 3. 신규 진입 (Redis 호출 3회)
    double score = System.currentTimeMillis();
    redisTemplate.opsForZSet()
        .add("queue:wait:" + concertId, userId, score);

    Long newRank = redisTemplate.opsForZSet()
        .rank("queue:wait:" + concertId, userId);

    Long totalWaiting = redisTemplate.opsForZSet()
        .zCard("queue:wait:" + concertId);

    return QueueEntryResult.newEntry(newRank, totalWaiting);
}
```

**문제 1: Redis 6회 호출**
```
1. HGETALL active:token:{concertId}:userId
2. ZRANK queue:wait:{concertId} userId
3. ZCARD queue:wait:{concertId}
4. ZADD queue:wait:{concertId} score userId
5. ZRANK queue:wait:{concertId} userId
6. ZCARD queue:wait:{concertId}

네트워크 RTT: 각 ~1ms × 6회 = 6ms
→ 응답시간의 주요 부분 차지
```

**문제 2: 원자성 미보장**
```java
// Thread 1: Active 확인
Map<String, String> activeToken = redisTemplate.opsForHash()
    .entries("active:token:" + concertId + ":" + userId);

// → 이 시점에 Thread 2가 Active Token 삭제 가능

if (!activeToken.isEmpty()) {
    // Thread 1: Active라고 판단
    // Thread 2: 이미 삭제됨
    // → 중복 진입 가능
}
```

### 성능 측정 (Before)

```bash
k6 run k6-tests/queue-entry-scale-test.js
```

**결과**:
```
평균 응답시간: 37.0ms
P95: 292ms
P99: 577ms

Redis 호출: 6회/요청
총 Redis 호출: 25,970회 (4,345 req/s × 6)
```

---

## 💡 해결 과정

### 1단계: 최적화 방안 비교

| 방안 | 예상 개선율 | 구현 난이도 | 원자성 | 선택 |
|------|------------|------------|--------|------|
| **totalWaiting 캐싱** | +3~5% | 쉬움 | ❌ | ❌ ROI 낮음 |
| **Redis Pipeline** | +5~7% | 보통 | ❌ | ❌ 원자성 미보장 |
| **Lua 스크립트 통합** | **+30~50%** | 보통 | ✅ | ✅ **채택** |

**Lua 스크립트 선택 이유**
1. **네트워크 RTT 최소화**: 6회 → 1회
2. **원자성 보장**: Redis 내부에서 원자적 실행
3. **성능 개선**: 30~50% 예상 (네트워크 병목 제거)

### 2단계: Lua 스크립트 작성

**enter_queue.lua**
```lua
-- KEYS[1]: active:token:{concertId}:userId
-- KEYS[2]: queue:wait:{concertId}
-- ARGV[1]: userId
-- ARGV[2]: score (timestamp)

local cjson = require("cjson")

-- 1. Active Token 확인
local activeTokenKey = KEYS[1]
local activeToken = redis.call('HGETALL', activeTokenKey)

if #activeToken > 0 then
    -- Active Token이 있으면 토큰 데이터 반환
    local tokenData = {}
    for i = 1, #activeToken, 2 do
        tokenData[activeToken[i]] = activeToken[i + 1]
    end
    return cjson.encode({
        status = 'ACTIVE',
        token = tokenData
    })
end

-- 2. Wait Queue 확인
local waitQueueKey = KEYS[2]
local userId = ARGV[1]
local score = tonumber(ARGV[2])

local existingRank = redis.call('ZRANK', waitQueueKey, userId)

if existingRank then
    -- 이미 대기 중이면 순번 반환
    local totalWaiting = redis.call('ZCARD', waitQueueKey)
    return cjson.encode({
        status = 'WAITING',
        position = existingRank,
        totalWaiting = totalWaiting
    })
end

-- 3. 신규 진입
redis.call('ZADD', waitQueueKey, score, userId)
local newRank = redis.call('ZRANK', waitQueueKey, userId)
local totalWaiting = redis.call('ZCARD', waitQueueKey)

return cjson.encode({
    status = 'NEW',
    position = newRank,
    totalWaiting = totalWaiting
})
```

### 3단계: Java Adapter 구현

**RedisEnterQueueAdapter.java**
```java
@Component
@RequiredArgsConstructor
public class RedisEnterQueueAdapter implements EnterQueuePort {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<String> enterQueueScript;

    @Override
    public QueueEntryResult enter(String concertId, String userId) {
        // KEYS
        List<String> keys = List.of(
            "active:token:" + concertId + ":" + userId,  // KEYS[1]
            "queue:wait:" + concertId                     // KEYS[2]
        );

        // ARGV
        String score = String.valueOf(System.currentTimeMillis());
        String[] args = { userId, score };

        // Lua 스크립트 실행 (단일 호출)
        String jsonResult = redisTemplate.execute(
            enterQueueScript,
            keys,
            args
        );

        // JSON 파싱
        Map<String, Object> result = objectMapper.readValue(
            jsonResult,
            new TypeReference<>() {}
        );

        String status = (String) result.get("status");

        return switch (status) {
            case "ACTIVE" -> QueueEntryResult.alreadyActive(
                (Map<String, String>) result.get("token")
            );
            case "WAITING" -> QueueEntryResult.alreadyWaiting(
                (Long) result.get("position"),
                (Long) result.get("totalWaiting")
            );
            case "NEW" -> QueueEntryResult.newEntry(
                (Long) result.get("position"),
                (Long) result.get("totalWaiting")
            );
            default -> throw new IllegalStateException("Unknown status: " + status);
        };
    }
}
```

### 4단계: Lua 스크립트 등록

**RedisConfig.java**
```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisScript<String> enterQueueScript() {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setScriptSource(
            new ResourceScriptSource(
                new ClassPathResource("scripts/enter_queue.lua")
            )
        );
        script.setResultType(String.class);
        return script;
    }
}
```

### 5단계: 서비스 리팩토링

**Before: 3단계 검증 (각 단계마다 여러 Redis 호출)**
```java
return queueEntryValidator.checkActiveUser()
    .or(() -> queueEntryValidator.checkWaitingUser())
    .orElseGet(() -> queueEntryProcessor.proceed());
```

**After: 단일 Lua 스크립트**
```java
return redisEnterQueueAdapter.enterQueue(concertId, userId);
```

---

## 📊 결과 분석

### Before vs After 비교

| 지표 | Before | After (Lua) | 개선율 |
|------|--------|------------|--------|
| **Redis 호출 횟수** | 6회/요청 | **1회/요청** | **-83%** |
| **평균 응답시간** | 37.0ms | **22.69ms** | **-38.7%** |
| **P95** | 292ms | **205.61ms** | **-29.6%** |
| **P99** | 577ms | **468.66ms** | **-18.8%** |
| **TPS** | 4,345 req/s | 4,362.8 req/s | +0.4% |
| **성공률** | 99.17% | 99.28% | +0.1% |

### 네트워크 RTT 절감 효과

**Before: 6회 Redis 호출**
```
네트워크 RTT: 1ms × 6회 = 6ms
실제 측정 평균: 37.0ms

→ 네트워크가 전체 시간의 16% 차지
```

**After: 1회 Lua 스크립트**
```
네트워크 RTT: 1ms × 1회 = 1ms
실제 측정 평균: 22.69ms

→ 네트워크가 전체 시간의 4% 차지
→ 5회 RTT 절약 (5ms) = 응답시간 14.3ms 단축
```

### 원자성 보장

**Before: Race Condition 가능**
```java
// Thread 1: Active 확인
Map<String, String> activeToken = redis.get(...);

// → 이 시점에 Thread 2가 삭제 가능

if (!activeToken.isEmpty()) {
    // Thread 1: Active라고 판단
    // → 중복 진입 가능
}
```

**After: Lua 스크립트 원자적 실행**
```lua
-- Redis 내부에서 원자적 실행
-- 다른 스레드의 간섭 없음
local activeToken = redis.call('HGETALL', activeTokenKey)
if #activeToken > 0 then
    return cjson.encode({status = 'ACTIVE', ...})
end
```

---

## 🎓 배운 점

### 1. 네트워크 RTT는 "보이지 않는 병목"

**직관적으로 생각**
```
"Redis는 빠르잖아? 1ms도 안 걸리는데?"
→ 6회 호출하면 6ms
→ 응답시간 37ms의 16%
→ 무시할 수 없는 수준
```

**측정 결과**
```
6회 → 1회 통합
→ 5ms RTT 절약
→ 평균 응답시간 14.3ms 단축 (38.7%)
```

### 2. 대안 비교의 중요성

| 방안 | 개선율 | 원자성 | 복잡도 |
|------|--------|--------|--------|
| 캐싱 | 3~5% | ❌ | 낮음 |
| Pipeline | 5~7% | ❌ | 중간 |
| **Lua Script** | **30~50%** | ✅ | 중간 |

**왜 Lua Script인가?**
- 캐싱: TTL 관리 복잡, 정합성 이슈
- Pipeline: 원자성 미보장, 성능 개선 제한적
- **Lua Script**: 성능 + 원자성 + ROI 최대

### 3. 코드 복잡도 vs 성능

**트레이드오프**
- 복잡도 증가: Java → Lua 스크립트 추가
- 디버깅 어려움: Lua 스크립트 디버깅 도구 부족
- 유지보수: 스크립트 수정 시 주의 필요

**그럼에도 선택한 이유**
- **성능 38.7% 개선**: 사용자 경험 대폭 향상
- **원자성 보장**: 경합 조건 제거 → 안정성 증가
- **ROI**: 복잡도 증가 대비 성능 개선 효과 큼

### 4. TPS는 왜 증가하지 않았나?

**예상**: Redis 호출 83% 감소 → TPS 대폭 증가?
**실제**: TPS 0.4% 증가 (4,345 → 4,362.8)

**원인**:
- Redis가 병목이 아니었음
- **다음 병목**: Redis 단일 인스턴스 처리량 한계

→ **다음 도전**: [Redis Cluster 확장](04-redis-cluster.md)

---

## 🧠 CS 이론과 깊이

### Redis 내부 동작: Single-threaded Event Loop

#### 1. 왜 Redis는 Single-threaded인가?

**Multi-threaded의 문제**
```
Thread 1: GET key1
Thread 2: SET key1 value
Thread 3: DEL key1

→ Lock 필요 (Mutex)
→ Context Switching 오버헤드
→ Cache Coherence 유지 비용
```

**Redis의 선택: Single-threaded Event Loop**
```
Event Loop (Main Thread):
while (true) {
    events = epoll_wait(epoll_fd);  // I/O Multiplexing
    for (event in events) {
        processCommand(event.client);  // 순차 처리
    }
}

장점:
- Lock 불필요
- Context Switching 없음
- CPU Cache Friendly
```

**결과**
- 명령어 처리: O(1) ~ O(log N) → 매우 빠름
- **하지만**: 네트워크 RTT가 병목
- → Lua Script로 RTT 최소화

#### 2. Network RTT vs Redis 처리 시간

**측정**
```
Redis 명령어 실행 시간:
- GET: ~100ns (나노초)
- SET: ~100ns
- ZADD: ~200ns
- HGETALL: ~300ns (필드 개수에 비례)

네트워크 RTT:
- 로컬 (Docker): ~1ms (마이크로초)
- 같은 AZ: ~5ms
- 다른 AZ: ~50ms

비교:
1ms = 1,000,000ns
→ 네트워크가 Redis보다 10,000배 느림!
```

**결론**
```
6회 Redis 호출:
- Redis 처리: 6 × 200ns = 1.2μs
- 네트워크 RTT: 6 × 1ms = 6ms
- → 네트워크가 5,000배 느림

Lua Script (1회 호출):
- Redis 처리: 1.2μs (동일)
- 네트워크 RTT: 1 × 1ms = 1ms
- → 5ms 절약 (83% 감소)
```

#### 3. Lua Script 원자성 보장 원리

**Redis는 어떻게 원자성을 보장하는가?**

**일반 명령어 (Race Condition 가능)**
```
Client 1:
1. GET key  → value = 10
2. SET key 11

Client 2:
1. GET key  → value = 10 (동시에!)
2. SET key 11

결과: value = 11 (한 번만 증가, Lost Update)
```

**Lua Script (원자성 보장)**
```lua
-- increment.lua
local current = redis.call('GET', KEYS[1])
redis.call('SET', KEYS[1], current + 1)
return current + 1
```

**Redis 내부 동작**
```
1. Lua Script 로드
2. Main Thread에서 실행 (다른 명령어 차단)
3. Script 완료 후 다음 명령어 처리

→ Script 실행 중에는 다른 클라이언트 대기
→ 원자성 보장 (ACID의 A와 I)
```

**트레이드오프**
```
장점:
- 원자성 보장
- 네트워크 RTT 최소화

단점:
- Script 실행 중 다른 명령어 차단
- 긴 Script는 Redis 전체 성능 저하
- → 우리 Script: 1.2μs (무시 가능)
```

#### 4. Redis Pipeline vs Transaction vs Lua Script

| 방안 | 네트워크 RTT | 원자성 | 조건문 | 선택 |
|------|-------------|--------|--------|------|
| **일반 호출 (6회)** | 6 × 1ms = 6ms | ❌ | ✅ | ❌ |
| **Pipeline** | 1ms | ❌ | ❌ | ❌ |
| **MULTI/EXEC** | 2ms | ✅ | ❌ | ❌ |
| **Lua Script** | 1ms | ✅ | ✅ | ✅ |

**Pipeline**
```java
// 6개 명령어를 한 번에 전송
pipeline.hgetAll("active:token:...");
pipeline.zrank("queue:wait:...");
pipeline.zadd("queue:wait:...");
...
List<Object> results = pipeline.syncAndReturnAll();

장점: 네트워크 RTT 1회
단점:
- 원자성 보장 안 됨
- 조건문 불가 (if activeToken exists then...)
```

**MULTI/EXEC (Transaction)**
```java
multi();
hgetAll("active:token:...");
zrank("queue:wait:...");
zadd("queue:wait:...");
exec();

장점: 원자성 보장
단점:
- 네트워크 RTT 2회 (MULTI + EXEC)
- 조건문 불가 (모든 명령어 무조건 실행)
```

**Lua Script**
```lua
if activeToken exists then
    return {status = 'ACTIVE'}
elseif waitingUser exists then
    return {status = 'WAITING'}
else
    zadd()
    return {status = 'NEW'}
end

장점:
- 네트워크 RTT 1회
- 원자성 보장
- 조건문 가능 (if/else)
```

**선택 이유**
- **조건문 필수**: Active 확인 → Wait 확인 → 진입
- **원자성 필수**: Race Condition 방지
- → Lua Script만 가능

---

## 🔀 고려한 다른 방안

### 1. Redis Cache (totalWaiting)

**아이디어**
```java
// totalWaiting을 캐싱하여 ZCARD 호출 제거
String cached = redis.get("cache:total:" + concertId);
if (cached != null) {
    return Long.parseLong(cached);
}

Long total = redis.zcard("queue:wait:" + concertId);
redis.setex("cache:total:" + concertId, 10, total);  // 10초 TTL
```

**장점**
- ZCARD 호출 2회 제거 (6회 → 4회)
- 구현 간단

**단점**
- **정합성 이슈**: TTL 동안 실제값과 불일치
- **개선율 낮음**: 33% vs Lua Script 83%
- **복잡도 증가**: 캐시 무효화 로직 필요

**선택하지 않은 이유**
- ROI 낮음 (33% vs 83%)
- 정합성 리스크

### 2. Redis Modules (RedisJSON, RedisGears)

**RedisJSON**
```
HGETALL 대신 JSON.GET 사용
→ 더 복잡한 데이터 구조 가능

JSON.GET active:token:{concertId}:{userId}
```

**장점**
- JSON 직렬화/역직렬화 불필요
- 복잡한 데이터 구조 지원

**단점**
- Redis Module 설치 필요
- 프로덕션 안정성 검증 부족
- Standard Redis 명령어가 더 안정적

**선택하지 않은 이유**
- **안정성**: Standard Redis로 충분
- **복잡도**: Module 관리 부담

### 3. Application-level Caching (Caffeine)

**Caffeine Cache**
```java
@Cacheable(value = "queuePosition", key = "#concertId + ':' + #userId")
public QueuePosition getPosition(String concertId, String userId) {
    // Redis 호출
}
```

**장점**
- Redis 호출 완전 제거 (메모리 캐시)
- 가장 빠름 (나노초 수준)

**단점**
- **정합성 이슈**: 다른 서버와 캐시 불일치
- **Invalidation 복잡**: 캐시 무효화 시점 판단 어려움
- **메모리 사용**: 각 서버마다 캐시 중복

**선택하지 않은 이유**
- **정합성**: 대기열은 정확성이 핵심
- **분산 환경**: Queue Service 4 instances

---

## 📂 관련 문서

- **[02. DB Pool 튜닝](02-db-pool-tuning.md)**: Lua Script 최적화 이전 단계
- **[04. Redis Cluster](04-redis-cluster.md)**: Lua Script 최적화 후 발견한 다음 병목
- **[Phase 3-2 Analysis](../phase3-lua-redis-cluster-analysis.md)**: Lua Script 실험 과정

---

## 🔧 재현 방법

### 1. Before: 6회 Redis 호출
```bash
# 기존 코드로 테스트
git checkout before-lua-script
./gradlew :queue-service:bootRun
k6 run k6-tests/queue-entry-scale-test.js
```

### 2. After: Lua 스크립트
```bash
# Lua 스크립트 적용
git checkout after-lua-script
./gradlew :queue-service:bootRun
k6 run k6-tests/queue-entry-scale-test.js
```

### 3. Redis 호출 횟수 확인
```bash
# Redis MONITOR 명령으로 호출 확인
redis-cli MONITOR | grep "queue:wait"
```

---

**작성자**: Yoon Seon-ho
**작성일**: 2025-12-26
**태그**: `Redis`, `Lua Script`, `Performance`, `Network RTT`
