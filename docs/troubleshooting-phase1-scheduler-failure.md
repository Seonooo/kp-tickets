# Phase 1 스케줄러 중단 문제 해결 리포트

**작성일**: 2025-12-26
**작성자**: AI 대기열 시스템 성능 테스트팀
**카테고리**: 트러블슈팅, 성능 최적화
**심각도**: Critical (시스템 완전 중단)

---

## 📋 Executive Summary

Phase 1 성능 테스트 중 Redis Lua 스크립트의 JSON 인코딩 버그로 인해 **대기열 스케줄러가 완전히 중단**되는 치명적 문제가 발생했습니다. `cjson.empty_array` API 호환성 문제로 인해 빈 배열이 JSON 객체로 인코딩되었고, Java Jackson parser가 이를 파싱하지 못해 스케줄러가 멈췄습니다.

문제 해결 후:
- **성공률**: 92.58% → 96.49% (목표 95% 달성)
- **P95 응답시간**: 632ms → 419ms (33.7% 개선)
- **P99 응답시간**: 1.35s → 651ms (51.8% 개선)
- **스케줄러 처리량**: 0 users → ~40,000 users (정상화)

---

## 1️⃣ 문제 발생

### 1.1 증상

**2025-12-26 11:26 KST**, Phase 1 대기열 진입 성능 테스트 (30만 명 동시 진입) 실행 중 다음과 같은 증상이 발생했습니다:

#### 클라이언트 측 (k6 테스트 결과)
```
총 요청 수: 266,274 / 310,000 (85.9%)
성공률: 92.58% (목표: >95%) ❌
P95 응답시간: 632ms (목표: <200ms) ❌
P99 응답시간: 1.35s (목표: <500ms) ❌
Dropped Iterations: 43,727 (14.1%)
실제 TPS: 3,797 (목표: 5,000)
```

#### 서버 측 (Prometheus 메트릭)
```
queue.wait.size: NaN ❌
queue.active.size: 0 ❌
queue.throughput.users_per_second: 41,801.6 (비정상적으로 높음) ❌
스케줄러 이동 횟수: 0 users ❌
```

#### 애플리케이션 로그
```log
2025-12-26T02:57:54.809Z ERROR [...] RedisActiveQueueAdapter  :
CRITICAL: Queue data corruption - Lua script succeeded but result parsing failed.
Users may have been moved but cannot be tracked:
concertId=concert-1, jsonResult=null

2025-12-26T02:57:54.811Z ERROR [...] QueueScheduler :
Failed to move users for concertId=concert-1

com.fasterxml.jackson.databind.exc.MismatchedInputException:
Cannot deserialize value of type ArrayList<String> from Object value
(token JsonToken.START_OBJECT)
```

### 1.2 영향 범위

**시스템 영향**:
- ✅ 대기열 진입(Queue Entry): 정상 작동 (266K 유저 진입 성공)
- ❌ Wait → Active 전환: **완전 중단** (0명 이동)
- ❌ 스케줄러: 매 5초마다 파싱 오류 발생, 작업 실패
- ❌ 메트릭 수집: NaN 값으로 모니터링 불가

**비즈니스 영향**:
- 대기열에 진입한 266,274명의 유저가 Active Queue로 이동하지 못함
- 실제 서비스라면 **모든 유저가 무한 대기 상태**에 빠졌을 것
- 티켓팅 서비스 완전 마비 상황

**재현 조건**:
- 대규모 트래픽 (TPS 5000+)
- Lua 스크립트 `move_to_active_queue.lua` 실행 시
- Redis 버전: cjson 2.1.0 미만 (empty_array API 미지원)

---

## 2️⃣ 모니터링 근거

### 2.1 이상 징후 탐지 타임라인

| 시간 (T+N초) | 이벤트 | 탐지 방법 |
|--------------|--------|-----------|
| **T+0s** | 테스트 시작 (TPS 1000 → 5000) | k6 시작 로그 |
| **T+15s** | 첫 스케줄러 실행 (5초 주기) | 애플리케이션 로그 |
| **T+15s** | **JSON 파싱 오류 발생** | ERROR 로그 출현 |
| **T+20s** | Prometheus 메트릭 NaN 기록 | Grafana 대시보드 |
| **T+30s** | Wait Queue 급격히 증가 (누적) | queue.wait.size 관찰 |
| **T+70s** | 테스트 종료, 총 266K 유저 진입 | k6 summary |
| **T+70s** | **Active Queue = 0 확인** | Prometheus 쿼리 |

### 2.2 Prometheus 메트릭 분석

#### 비정상 메트릭 상세

**1. queue.wait.size = NaN**
```promql
queue_wait_size{concert_id="concert-1"}
# Expected: 0 ~ 300,000 (점진적 증가 후 감소)
# Actual: NaN (메트릭 수집 실패)
```

**원인**: Gauge 메트릭이 Redis ZCOUNT 결과를 받아오지 못함
**의미**: 대기열 크기 추적 불가능, 스케줄러가 작동하지 않음을 암시

**2. queue.active.size = 0**
```promql
queue_active_size{concert_id="concert-1"}
# Expected: 0 → 50,000 (Active Queue 최대치까지 증가)
# Actual: 0 (변화 없음)
```

**원인**: Wait → Active 전환이 한 번도 실행되지 않음
**의미**: 스케줄러 `moveWaitingUsersToActive` 메서드 실패

**3. queue.throughput.users_per_second = 41,801.6**
```promql
queue_throughput_users_per_second{concert_id="concert-1"}
# Expected: 0 ~ 10,000 (현실적인 처리량)
# Actual: 41,801.6 (비정상적으로 높음)
```

**원인**: 0으로 나누기 또는 비정상적인 계산 결과
**의미**: Throughput 계산 로직이 예외 상황에서 잘못된 값 산출

### 2.3 애플리케이션 로그 분석

#### 에러 발생 코드 위치 추적

**Stack Trace 분석**:
```java
// 1. 진입점: QueueScheduler.java:95
int moved = moveToActiveQueueUseCase.moveWaitingToActive(concertId);

// 2. Service Layer: QueueSchedulerService.java
List<String> movedUserIds = activeQueueAdapter.moveToActiveQueueAtomic(...);

// 3. Adapter Layer: RedisActiveQueueAdapter.java:210
String jsonResult = luaScriptExecutor.executeMoveToActiveQueue(...);

// 4. Lua Executor: RedisLuaScriptExecutor.java:188
String jsonResult = redisTemplate.execute(moveToActiveQueueScript, ...);

// 5. Converter: RedisTokenConverter.java:103
return objectMapper.readValue(jsonArrayString, new TypeReference<List<String>>() {});
// ❌ MismatchedInputException 발생!
```

#### 에러 메시지 상세 분석

```log
com.fasterxml.jackson.databind.exc.MismatchedInputException:
Cannot deserialize value of type `java.util.ArrayList<java.lang.String>`
from Object value (token `JsonToken.START_OBJECT`)
```

**해석**:
- Jackson이 JSON을 파싱하려 했으나, **배열 `[...]` 대신 객체 `{...}`를 받음**
- `START_OBJECT` 토큰 = JSON이 `{`로 시작함을 의미
- 예상: `["userId1", "userId2"]`
- 실제: `{}` 또는 `{"key": "value"}`

### 2.4 Redis Lua 스크립트 디버깅

#### Lua 스크립트 반환 값 추적

**원본 코드** (`move_to_active_queue.lua`):
```lua
-- Line 33: 빈 큐 처리
if #poppedUsers == 0 then
    return cjson.encode({})  -- ❌ 문제 발생 지점
end

-- Line 37: 성공한 유저 ID 목록
local movedUserIds = {}

-- Line 76: 결과 반환
return cjson.encode(movedUserIds)  -- ❌ 빈 테이블 인코딩 문제
```

**문제 분석**:

1. **cjson.empty_array API 부재**
   - Redis 버전에 따라 cjson 라이브러리 버전이 다름
   - cjson 2.1.0 미만: `cjson.empty_array` 미지원
   - 대안 없이 `{}` 사용 시 객체로 인코딩

2. **빈 테이블 인코딩 동작**
   ```lua
   local t = {}
   cjson.encode(t)
   -- Redis cjson 기본 동작: "{}" (객체)
   -- 예상: "[]" (배열)
   ```

3. **배열 vs 객체 판단 기준**
   - Lua 테이블에 숫자 인덱스만 있으면 배열
   - 빈 테이블은 **애매모호** → 기본값으로 객체 처리

### 2.5 Root Cause 확정

**최종 원인**:
```
Redis Lua cjson 라이브러리가 빈 테이블 {}을 JSON 객체 "{}"로 인코딩
→ Java Jackson parser가 배열 List<String>으로 파싱 시도
→ MismatchedInputException 발생
→ QueueDataCorruptionException으로 래핑되어 throw
→ 스케줄러 작업 실패
→ Wait → Active 전환 중단
```

---

## 3️⃣ 해결을 위한 방안

### 3.1 문제 해결 전략 수립

#### Option 1: Lua 스크립트 수정 (채택 ✅)

**장점**:
- ✅ 근본 원인 해결 (소스에서 올바른 JSON 반환)
- ✅ Redis 버전 독립적 (명시적 문자열 반환)
- ✅ 성능 영향 없음

**단점**:
- ⚠️ Lua 스크립트 재배포 필요
- ⚠️ 테스트 필요

**구현 방법**:
```lua
-- 빈 배열을 명시적으로 JSON 문자열로 반환
if #poppedUsers == 0 then
    return "[]"  -- cjson.encode 대신 직접 문자열 반환
end

local movedUserIds = {}
-- ... 유저 처리 로직 ...

if #movedUserIds == 0 then
    return "[]"  -- 모든 유저가 롤백된 경우에도 명시적 빈 배열
end
return cjson.encode(movedUserIds)
```

#### Option 2: Java 방어 코드 추가 (보완적 적용 ✅)

**장점**:
- ✅ 방어적 프로그래밍 (예상치 못한 케이스 대응)
- ✅ 빠른 적용 가능

**단점**:
- ⚠️ 근본 해결 아님 (Workaround)
- ⚠️ "{}" 케이스가 정상인지 비정상인지 구분 불가

**구현 방법**:
```java
// RedisActiveQueueAdapter.java:219
if (jsonResult == null || jsonResult.isEmpty() ||
    jsonResult.equals("[]") || jsonResult.equals("{}")) {  // "{}" 추가
    log.debug("No users moved: concertId={}", concertId);
    return List.of();
}
```

#### Option 3: Redis 버전 업그레이드 (고려했으나 미채택)

**장점**:
- ✅ 최신 cjson 기능 사용 가능 (`cjson.empty_array`)

**단점**:
- ❌ 인프라 변경 필요 (리스크 높음)
- ❌ 다른 버전 호환성 문제 발생 가능
- ❌ 시간 소요 (즉시 해결 불가)

**판단**: Phase 1 긴급 해결에 부적합

#### Option 4: Jackson 설정 변경 (고려했으나 미채택)

**장점**:
- ✅ Java 코드만 수정 (배포 간단)

**단점**:
- ❌ "{}"를 배열로 강제 파싱하는 것은 의미상 부적절
- ❌ 다른 API에도 영향 가능 (전역 설정)

**판단**: 문제의 본질과 맞지 않음

### 3.2 선택한 해결 방안

**최종 선택**: **Option 1 (Lua 수정) + Option 2 (Java 방어 코드)**

**이유**:
1. **근본 원인 해결**: Lua 스크립트가 올바른 JSON 배열 반환
2. **방어적 프로그래밍**: 예상치 못한 "{}" 케이스에도 대응
3. **빠른 적용**: 인프라 변경 없이 코드 수정만으로 해결
4. **테스트 가능**: 로컬 환경에서 즉시 검증 가능

### 3.3 구현 계획

#### Step 1: Lua 스크립트 수정

**파일**: `queue-service/src/main/resources/scripts/move_to_active_queue.lua`

**변경 내용**:
```lua
-- Before (Line 33)
if #poppedUsers == 0 then
    return cjson.encode({})  -- ❌ 버그
end

local movedUserIds = {}
return cjson.encode(movedUserIds)  -- ❌ 빈 테이블 시 버그

-- After (Line 33)
if #poppedUsers == 0 then
    return "[]"  -- ✅ 명시적 빈 배열 문자열
end

local movedUserIds = {}
-- ... 처리 로직 ...
if #movedUserIds == 0 then
    return "[]"  -- ✅ 모든 유저 롤백 시에도 명시적 빈 배열
end
return cjson.encode(movedUserIds)  -- ✅ 하나 이상 있으면 cjson 사용
```

#### Step 2: Java 방어 코드 추가

**파일**: `queue-service/src/main/java/personal/ai/queue/adapter/out/redis/RedisActiveQueueAdapter.java`

**변경 내용**:
```java
// Line 219
// Before
if (jsonResult == null || jsonResult.isEmpty() || jsonResult.equals("[]")) {
    return List.of();
}

// After
if (jsonResult == null || jsonResult.isEmpty() ||
    jsonResult.equals("[]") || jsonResult.equals("{}")) {  // "{}" 케이스 추가
    log.debug("No users moved: concertId={}", concertId);
    return List.of();
}
```

#### Step 3: 빌드 및 배포

```bash
# 1. Gradle 빌드
./gradlew :queue-service:clean :queue-service:build -x test

# 2. Docker 이미지 빌드
docker-compose -f docker-compose.simple-scale.yml build queue-service

# 3. 서비스 재시작
docker-compose -f docker-compose.simple-scale.yml up -d queue-service

# 4. 헬스 체크
docker logs ai-queue-service-1 --tail 30
```

#### Step 4: 검증 테스트

```bash
# Phase 1 테스트 재실행
docker run --rm --network ai_concert-network \
  -v "C:\Users\윤선호\IdeaProjects\ai\k6-tests:/scripts" \
  grafana/k6:latest run //scripts//queue-entry-scale-test.js

# Prometheus 메트릭 확인
curl "http://localhost:9090/api/v1/query?query=queue_wait_size"
curl "http://localhost:9090/api/v1/query?query=queue_active_size"
curl "http://localhost:9090/api/v1/query?query=queue_throughput_users_per_second"

# 애플리케이션 로그 확인
docker logs ai-queue-service-1 2>&1 | grep "PERF\|ERROR"
```

---

## 4️⃣ 해결

### 4.1 수정 결과

#### 코드 변경 사항

**1. move_to_active_queue.lua**
```diff
  -- 1. Wait Queue에서 Pop
  local poppedUsers = redis.call('ZPOPMIN', waitQueueKey, batchSize)

  if #poppedUsers == 0 then
-     return cjson.encode({})  -- 빈 배열 반환
+     return "[]"  -- 빈 배열 반환 (명시적 JSON 문자열)
  end

  -- 2. 성공한 유저 ID 목록
- local movedUserIds = {}
+ local movedUserIds = {}

  -- ... (유저 처리 로직) ...

  -- 4. 성공한 유저 ID 목록 반환 (JSON 배열)
+ if #movedUserIds == 0 then
+     return "[]"  -- 빈 배열 (모든 유저가 롤백된 경우)
+ end
  return cjson.encode(movedUserIds)
```

**2. RedisActiveQueueAdapter.java**
```diff
  public List<String> moveToActiveQueueAtomic(String concertId, int count, Instant expiredAt) {
      // ... Lua 스크립트 실행 ...

-     if (jsonResult == null || jsonResult.isEmpty() || jsonResult.equals("[]")) {
+     if (jsonResult == null || jsonResult.isEmpty() ||
+         jsonResult.equals("[]") || jsonResult.equals("{}")) {
          log.debug("No users moved: concertId={}", concertId);
          return List.of();
      }

      // ... Jackson 파싱 ...
  }
```

#### 배포 이력

| 순서 | 작업 | 시간 | 상태 |
|------|------|------|------|
| 1 | Lua 스크립트 수정 | 11:50 | ✅ |
| 2 | Java 방어 코드 추가 | 11:51 | ✅ |
| 3 | Gradle 빌드 | 11:52 | ✅ 5초 소요 |
| 4 | Docker 이미지 빌드 | 11:52 | ✅ 38초 소요 |
| 5 | 서비스 재시작 | 11:53 | ✅ |
| 6 | 헬스 체크 | 11:53 | ✅ Healthy |
| 7 | Phase 1 재테스트 | 11:55 | ✅ 1분 10초 |
| 8 | 메트릭 검증 | 11:56 | ✅ 정상 |

### 4.2 테스트 결과

#### 4.2.1 K6 클라이언트 메트릭

| 지표 | 수정 전 (1차) | 수정 후 (2차) | 개선율 | 목표 | 달성 |
|------|---------------|---------------|--------|------|------|
| **총 요청 수** | 266,274 | 302,889 | **+13.8%** | 310,000 | 97.7% |
| **성공률** | 92.58% | **96.49%** | +4.2% | >95% | ✅ |
| **실패 요청** | 19,726 | 10,624 | **-46.1%** | <5% | ✅ |
| **P50 응답시간** | - | 1.79ms | - | - | - |
| **P90 응답시간** | - | 8.04ms | - | - | - |
| **P95 응답시간** | 632ms | **419ms** | **-33.7%** | <200ms | ❌ |
| **P99 응답시간** | 1.35s | **651ms** | **-51.8%** | <500ms | ❌ |
| **HTTP 에러율** | 0.01% | **0.00%** | -100% | <5% | ✅ |
| **Dropped Iterations** | 43,727 (14.1%) | **7,114 (2.3%)** | **-83.7%** | <5% | ❌ |
| **실제 TPS** | 3,797 | **4,320** | **+13.8%** | 5,000 | 86.4% |

**주요 개선 사항**:
- ✅ **성공률 95% 목표 달성** (96.49%)
- ✅ **P95 응답시간 33.7% 개선** (632ms → 419ms)
- ✅ **P99 응답시간 51.8% 개선** (1.35s → 651ms)
- ✅ **Dropped iterations 83.7% 감소** (43,727 → 7,114)
- ✅ **실제 TPS 13.8% 증가** (3,797 → 4,320)

#### 4.2.2 Prometheus 서버 메트릭

| 메트릭 | 수정 전 (1차) | 수정 후 (2차) | 상태 |
|--------|---------------|---------------|------|
| `queue.wait.size` | **NaN** ❌ | **0** ✅ | 정상화 |
| `queue.active.size` | **0** ❌ | **4** ✅ | 정상화 |
| `queue.throughput.users_per_second` | **41,801.6** ❌ | **0** (테스트 후) ✅ | 정상화 |
| **스케줄러 처리량** | **0 users** ❌ | **~40,000 users** ✅ | 정상화 |

**메트릭 정상화 확인**:
```bash
# queue.wait.size (테스트 종료 후)
$ curl "http://localhost:9090/api/v1/query?query=queue_wait_size"
{"value":[1766718356.318,"0"]}  # ✅ NaN → 0

# queue.active.size (일부 유저 남아있음)
$ curl "http://localhost:9090/api/v1/query?query=queue_active_size"
{"value":[1766718356.383,"4"]}  # ✅ 0 → 4

# queue.throughput.users_per_second (현재 처리 없음)
$ curl "http://localhost:9090/api/v1/query?query=queue_throughput_users_per_second"
{"value":[1766718356.446,"0"]}  # ✅ 41,801.6 → 0 (정상)
```

#### 4.2.3 스케줄러 로그

**수정 전 (ERROR 반복)**:
```log
2025-12-26T02:57:54.809Z ERROR [...] RedisActiveQueueAdapter  :
CRITICAL: Queue data corruption - Lua script succeeded but result parsing failed.
Users may have been moved but cannot be tracked: concertId=concert-1, jsonResult=null

2025-12-26T02:57:54.811Z ERROR [...] QueueScheduler :
Failed to move users for concertId=concert-1
```

**수정 후 (정상 작동)**:
```log
2025-12-26T03:04:47.562Z INFO [...] QueueScheduler :
[PERF] MoveToActive: concertId=concert-1, movedUsers=26801

2025-12-26T03:04:55.002Z INFO [...] QueueScheduler :
[PERF] MoveToActive: concertId=concert-1, movedUsers=13008

2025-12-26T03:05:02.099Z INFO [...] QueueScheduler :
[PERF] MoveToActive: concertId=concert-1, movedUsers=0
(Wait Queue 비어서 정상 종료)
```

**스케줄러 처리 통계**:
- 첫 번째 실행: **26,801명** 이동
- 두 번째 실행: **13,008명** 이동
- **총 39,809명** Wait → Active 전환 성공 ✅

### 4.3 성능 개선 분석

#### 4.3.1 응답시간 분포 개선

**P95 응답시간 개선 (632ms → 419ms)**:
- **개선율**: 33.7%
- **원인**: 스케줄러가 정상 작동하여 대기열 적체 해소
- **분석**: Wait Queue에 쌓인 유저들이 Active로 이동하면서 대기열 위치 계산 오버헤드 감소

**P99 응답시간 개선 (1.35s → 651ms)**:
- **개선율**: 51.8%
- **원인**: 최악의 케이스(대기열 맨 뒤) 유저들이 Active로 빠르게 이동
- **분석**: P99가 P95보다 더 큰 폭으로 개선됨 (꼬리 지연 해소)

#### 4.3.2 처리량 개선

**실제 TPS 증가 (3,797 → 4,320)**:
- **개선율**: 13.8%
- **원인**: Dropped iterations 감소 (43,727 → 7,114)
- **분석**: VU가 응답을 빨리 받아서 다음 요청을 더 많이 수행

**총 요청 수 증가 (266,274 → 302,889)**:
- **개선율**: 13.8%
- **원인**: 동일 시간 내에 더 많은 요청 처리 가능
- **분석**: 목표 310,000의 97.7% 달성

#### 4.3.3 안정성 개선

**성공률 향상 (92.58% → 96.49%)**:
- **개선**: +3.91%p (목표 95% 초과 달성)
- **원인**: 타임아웃 및 에러 응답 감소
- **분석**: 스케줄러가 정상 작동하여 시스템 전반적 안정성 향상

**HTTP 에러율 개선 (0.01% → 0.00%)**:
- **개선**: 완전 제거
- **원인**: 서버 내부 에러(500번대) 발생하지 않음
- **분석**: Lua 스크립트 파싱 오류가 API 레벨에는 영향 없었음 (스케줄러만 영향)

### 4.4 남은 개선 과제

#### 4.4.1 목표 미달성 지표

**1. P95/P99 응답시간**
- **현재**: P95=419ms, P99=651ms
- **목표**: P95<200ms, P99<500ms
- **Gap**: P95=+209%, P99=+30%

**원인 분석**:
```
1. Redis 커넥션 풀 부족
   - 현재: max-active=20
   - 동시 요청: 5000 TPS
   - 대기 시간 발생 가능

2. 대기열 위치 계산 오버헤드
   - ZRANK: O(log N) 복잡도
   - 30만 명 규모에서 성능 영향

3. Wait Queue 크기 계산
   - ZCOUNT: O(log N) 복잡도
   - 매 요청마다 호출
```

**해결 방안**:
- Redis 풀 증가: `max-active: 50` (2.5배)
- 위치 계산 캐싱: 최근 계산 결과 재사용 (TTL 1초)
- Batch 조회 최적화: Pipeline 사용

**2. 실제 TPS**
- **현재**: 4,320 TPS
- **목표**: 5,000 TPS
- **Gap**: -13.6%

**원인 분석**:
```
1. VU 부족 (Dropped iterations 7,114)
   - maxVUs=3000 부족
   - 5000 TPS × 0.65s(P99) = 3,250 VUs 최소 필요

2. 응답시간 지연 (P99=651ms)
   - 목표 500ms 초과로 VU 회전율 감소
```

**해결 방안**:
- k6 VU 증가: `maxVUs: 5000` (1.67배)
- preAllocatedVUs: `3000` (메모리 사전 확보)

#### 4.4.2 다음 최적화 우선순위

| 순위 | 작업 | 예상 효과 | 난이도 | 예상 시간 |
|------|------|-----------|--------|-----------|
| **1** | k6 VU 증가 (maxVUs: 5000) | TPS 5000 달성 | Low | 10분 |
| **2** | Redis 풀 증가 (max-active: 50) | P95/P99 20% 개선 | Low | 10분 |
| **3** | 위치 계산 캐싱 | P95 30% 개선 | Medium | 2시간 |
| **4** | Lua 스크립트 로깅 개선 | 모니터링 정확도 향상 | Low | 30분 |

### 4.5 Lessons Learned

#### 4.5.1 기술적 교훈

**1. Redis Lua cjson 호환성**
- ✅ `cjson.empty_array`는 cjson 2.1.0+ 전용 (Redis 버전별 상이)
- ✅ 명시적 JSON 문자열 반환 (`"[]"`)이 가장 안전
- ✅ 빈 테이블 `{}`의 기본 인코딩은 객체 `"{}"` (배열 아님)

**Best Practice**:
```lua
-- ❌ Bad (버전 의존적)
return cjson.encode(cjson.empty_array)

-- ✅ Good (명시적, 안전)
return "[]"

-- ✅ Better (조건부)
if #array == 0 then
    return "[]"
end
return cjson.encode(array)
```

**2. 방어적 프로그래밍**
- ✅ Lua 스크립트의 반환값을 Java에서 신뢰하지 말 것
- ✅ 예상치 못한 형식에 대한 방어 코드 필수
- ✅ 로그를 통해 실제 반환값 기록 (디버깅 용이)

**Best Practice**:
```java
// ✅ 다양한 빈 값 케이스 처리
if (jsonResult == null || jsonResult.isEmpty() ||
    jsonResult.equals("[]") || jsonResult.equals("{}")) {
    log.warn("Empty result from Lua script: {}", jsonResult);
    return List.of();
}
```

**3. 메트릭 수집의 중요성**
- ✅ Prometheus 메트릭 NaN으로 스케줄러 중단 조기 파악
- ✅ 클라이언트(k6) + 서버(Prometheus) 양측 메트릭 필수
- ✅ 메트릭 이상 → 로그 확인 → 코드 추적 순서로 디버깅

**Best Practice**:
```java
// ✅ 메트릭과 함께 상세 로그 남기기
log.info("[PERF] MoveToActive: concertId={}, movedUsers={}, " +
         "throughput={} users/sec, estimatedWait={}s",
         concertId, moved, throughput, estimatedWaitSeconds);
```

#### 4.5.2 프로세스 개선

**1. 로드 테스트 VU 계산 공식**
```
maxVUs >= rate × p99_duration × safety_margin

예: 5000 TPS × 0.65s × 1.2 = 3900 VUs
```

**2. 성능 테스트 체크리스트**
- [ ] k6 스크립트 VU 계산 검증
- [ ] Prometheus 메트릭 정의 확인
- [ ] Grafana 대시보드 설정
- [ ] 애플리케이션 로그 레벨 설정 (INFO/DEBUG)
- [ ] Redis 커넥션 풀 크기 확인
- [ ] 테스트 환경 리소스 확인 (CPU/Memory)

**3. 트러블슈팅 워크플로우**
```
1. 증상 파악 (k6 summary, Grafana dashboard)
2. 메트릭 분석 (Prometheus query)
3. 로그 확인 (ERROR 패턴 검색)
4. 코드 추적 (Stack trace → Source code)
5. 로컬 재현 (단위 테스트 작성)
6. 수정 및 검증 (테스트 → 배포 → 재테스트)
```

#### 4.5.3 문서화 개선

**1. 코드 주석**
```lua
-- Before
return cjson.encode({})

-- After
return "[]"  -- 명시적 JSON 문자열 반환 (cjson.empty_array 호환성 이슈 회피)
```

**2. API 문서화**
- Lua 스크립트 반환 타입 명시 (JSON 스키마)
- 예외 상황 처리 방법 문서화
- 버전별 호환성 정보 기록

**3. 트러블슈팅 가이드**
- 이번 이슈를 템플릿으로 활용
- 유사 문제 발생 시 참고 자료로 사용

---

## 📚 참고 자료

### 관련 문서
- [Performance Improvement Plan](./performance-improvement-plan.md)
- [Phase 1 Test Results (Initial)](./performance-improvement-plan.md#phase-1-queue-entry-성능-측정-결과)
- [Grafana Dashboard Configuration](../monitoring/grafana-dashboard-application.json)
- [K6 Test Script](../k6-tests/queue-entry-scale-test.js)

### 외부 참조
- [Redis Lua cjson Documentation](https://www.kyne.com.au/~mark/software/lua-cjson.php)
- [Jackson Deserialization Error Handling](https://github.com/FasterXML/jackson-databind/wiki/Deserialization-Features)
- [K6 Executor Types](https://k6.io/docs/using-k6/scenarios/executors/)

### 코드 위치
- Lua Script: `queue-service/src/main/resources/scripts/move_to_active_queue.lua`
- Adapter: `queue-service/src/main/java/personal/ai/queue/adapter/out/redis/RedisActiveQueueAdapter.java`
- Converter: `queue-service/src/main/java/personal/ai/queue/adapter/out/redis/RedisTokenConverter.java`
- Scheduler: `queue-service/src/main/java/personal/ai/queue/adapter/scheduler/QueueScheduler.java`

---

## 📝 변경 이력

| 날짜 | 버전 | 작성자 | 변경 내용 |
|------|------|--------|-----------|
| 2025-12-26 | 1.0 | AI Performance Team | 초안 작성 |

---

**문서 작성 완료**: 2025-12-26 12:10 KST
**다음 단계**: Phase 1 최종 최적화 (VU 증가, Redis 풀 증가)
