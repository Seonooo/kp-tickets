# 대규모 좌석 조회 API 성능 최적화 여정: 5.23s에서 829ms까지

## TL;DR

티켓팅 시스템에서 8,600개 좌석을 한 번에 조회하는 API의 성능을 **5.23s → 829ms (84% 개선)**로 최적화한 과정입니다. Redis 캐싱 도입, Java Record 직렬화 문제 해결, Response DTO 캐싱 전략을 거쳐 최종적으로 **대량 JSON 응답이라는 구조적 선택의 비용**을 발견했습니다.

---

## 이 글의 목적

이 글은 단순한 성능 튜닝 성공기가 아닙니다.

**"어디까지가 기술로 해결할 문제이고, 어디부터가 비즈니스 요구사항의 선택인가"**를 고민한 의사결정 과정 기록입니다.

각 최적화 단계에서:
- ✅ **측정 가능한 수치적 근거**로 병목을 파악하고
- ✅ **비즈니스 요구사항**과 기술적 제약을 균형 있게 고려하며
- ✅ **아키텍처 원칙**을 유지하면서 성능을 개선한

실제 엔지니어링 의사결정 과정을 담았습니다.

---

## 1. 문제 정의

### 초기 상황
```
API: GET /api/v1/schedules/{scheduleId}/seats
데이터: 10,000개 좌석 (실시간 약 8,600개)
성능: P95 = 5.23s ❌
목표: P95 < 500ms
```

### 비즈니스 요구사항
- 전체 좌석을 한 화면에 표시 (Pagination 불가)
- 실시간 좌석 상태 반영 (캐시 TTL 1초)
- 동시 접속자 처리 (VUs 50)

---

## 2. Phase 1: 캐시 도입 실패 - Timeout 문제

### 증상
```
Error: RedisCommandTimeoutException
Command timed out after 3 second(s)
```

### 원인 분석
Redis timeout을 확인한 결과:
```yaml
spring:
  data:
    redis:
      timeout: 1000  # 1초
```

**10,000개 Seat 객체를 RecordSupportingTypeResolver로 직렬화**하는데 **3초 이상** 소요되어 timeout 발생!

### 근거
```java
// Seat.java - Record 타입
public record Seat(
    Long id,
    Long scheduleId,
    String seatNumber,
    SeatGrade grade,
    BigDecimal price,
    SeatStatus status
) {
    // isOccupied() 같은 메서드들...
}
```

Record는 `final` 클래스이므로 Jackson의 `DefaultTyping.NON_FINAL`에서 제외됨 → RecordSupportingTypeResolver로 강제로 `@class` 타입 정보를 추가하면서 직렬화 오버헤드 증가.

### 해결
```yaml
timeout: 10000  # 10초로 증가
```

### 결과
- Timeout 에러 해결 ✅
- **하지만 성능 개선 없음** (여전히 5.23s)

---

## 3. Phase 2: 캐시가 저장되지 않음 - Silent Failure

### 증상
```bash
# Redis 키 확인
$ redis-cli KEYS "core:*"
(empty array)  # 캐시 키 없음!

# 로그 확인
Cache MISS - 108회 (모든 요청이 DB 조회)
```

### 원인 분석
Spring Cache는 **silent failure** - 캐시 저장 실패 시 예외를 던지지 않고 조용히 무시합니다.

**CustomCacheErrorHandler**를 추가하여 확인:
```java
@Override
public void handleCachePutError(RuntimeException exception,
                                 Cache cache, Object key, Object value) {
    log.error("Cache PUT error - cache: {}, key: {}, error: {}",
              cache.getName(), key, exception.getMessage(), exception);
}
```

### 근거
1. **직렬화 시간 > Timeout**: 10,000개 객체 직렬화에 10초 이상 소요
2. **RecordSupportingTypeResolver의 오버헤드**: 모든 Record에 `@class` 타입 정보 추가
3. **Field-only visibility 설정**: `isOccupied()` 메서드 직렬화 방지 위해 field만 직렬화

```java
objectMapper.setVisibility(
    objectMapper.getSerializationConfig()
        .getDefaultVisibilityChecker()
        .withFieldVisibility(JsonAutoDetect.Visibility.ANY)
        .withGetterVisibility(JsonAutoDetect.Visibility.NONE)  // getter 제외
        .withIsGetterVisibility(JsonAutoDetect.Visibility.NONE)  // is getter 제외
);
```

이 설정으로 인해 직렬화 성능이 더 저하됨.

### 해결 시도
Timeout을 더 늘려도 근본적 해결 안됨 → **다른 접근 필요**

---

## 4. Phase 3: 캐시 작동하지만 여전히 느림

### 증상
```
Cache HIT rate: 98.6% (1373/1393) ✅
하지만 P95 = 1.82s ❌
```

캐시는 작동하는데 왜 느릴까?

### 상세 분석을 위한 Instrumentation 추가

#### 4.1 Cache Layer
```java
@Cacheable(value = "availableSeats", key = "#scheduleId")
public List<Seat> findAvailableSeats(Long scheduleId) {
    long startTime = System.currentTimeMillis();
    List<Seat> seats = seatRepository.findAvailableByScheduleId(scheduleId);
    long dbQueryTime = System.currentTimeMillis() - startTime;

    log.info("DB query completed - seatCount: {}, dbQueryTime: {}ms",
            seats.size(), dbQueryTime);
    return seats;
}
```

#### 4.2 Service Layer
```java
@Override
public List<Seat> getAvailableSeats(...) {
    long queueValidationStart = System.currentTimeMillis();
    queueServiceClient.validateToken(concertId, userId, queueToken);
    long queueValidationTime = System.currentTimeMillis() - queueValidationStart;

    long cacheQueryStart = System.currentTimeMillis();
    var availableSeats = seatQueryCacheService.findAvailableSeats(scheduleId);
    long cacheQueryTime = System.currentTimeMillis() - cacheQueryStart;

    log.info("Service timing - queueValidation: {}ms, cache: {}ms",
            queueValidationTime, cacheQueryTime);
}
```

#### 4.3 Controller Layer
```java
@GetMapping("/schedules/{scheduleId}/seats")
public ResponseEntity<List<SeatResponse>> getAvailableSeats(...) {
    long serviceCallStart = System.currentTimeMillis();
    List<Seat> seats = getAvailableSeatsUseCase.getAvailableSeats(...);
    long serviceCallTime = System.currentTimeMillis() - serviceCallStart;

    long mappingStart = System.currentTimeMillis();
    List<SeatResponse> response = seats.stream()
            .map(SeatResponse::from)
            .toList();
    long mappingTime = System.currentTimeMillis() - mappingStart;

    log.info("Controller timing - serviceCall: {}ms, mapping: {}ms",
            serviceCallTime, mappingTime);
}
```

### 측정 결과

```
Cache MISS (20회):
- DB query: 13-29ms
- Total time (직렬화 포함): 13-29ms
→ 직렬화 오버헤드: 0ms ✅

Cache HIT (1373회):
- Cache 조회: 37-711ms ❌  (최대 711ms!)
- Queue 검증: 5-45ms
- DTO 매핑: 1-4ms

Controller:
- Service call: 40-756ms
- DTO mapping: 0-4ms

k6 측정 (End-to-End):
- Seats Query P95: 1.82s ❌
```

### 근거

**Cache HIT 시 Redis 역직렬화가 느림!**

```
Redis 저장 형식: JSON 문자열
{
  "@class": "personal.ai.core.booking.domain.model.Seat",
  "id": 1,
  "scheduleId": 1,
  ...
}

역직렬화 과정:
1. Redis에서 JSON 문자열 읽기
2. JSON → Seat 객체 변환 (8,629개)
3. 각 객체의 @class 타입 정보 검증
4. List<Seat> 생성

→ 최대 711ms 소요!
```

**왜 이렇게 느릴까?**

1. **RecordSupportingTypeResolver**: 타입 안전성을 위해 `@class` 추가
2. **8,629개 객체**: 각각 역직렬화 필요
3. **Field-only visibility**: getter 메서드 제외로 리플렉션 오버헤드

### 더 큰 문제 발견

```
k6 P95: 1.82s
Cache 최대: 711ms
차이: 1.82s - 0.711s = 1.1s

어디서 1.1초가 더 소요되는가?
```

---

## 5. Phase 4: Response DTO 직접 캐싱

### 가설
**"Entity를 캐싱하고 DTO로 변환하는 것이 비효율적이다"**

```
현재 흐름:
Redis JSON → Seat (역직렬화) → SeatResponse (변환) → JSON (직렬화)
          711ms                  1-4ms              ???ms
```

### 해결책: Response DTO를 직접 캐싱

```java
// 변경 전
@Cacheable("availableSeats")
List<Seat> findAvailableSeats(Long scheduleId) {
    return seatRepository.findAvailableByScheduleId(scheduleId);
}

// 변경 후
@Cacheable("availableSeats")
List<SeatResponse> findAvailableSeats(Long scheduleId) {
    List<Seat> seats = seatRepository.findAvailableByScheduleId(scheduleId);
    return seats.stream()
            .map(SeatResponse::from)  // Cache MISS 시 1회만 변환
            .toList();
}
```

### 근거

**이중 직렬화 제거:**

```
변경 전:
1. Redis 역직렬화: JSON → Seat (711ms)
2. DTO 변환: Seat → SeatResponse (4ms)
3. HTTP 직렬화: SeatResponse → JSON (???ms)

변경 후:
1. Redis 역직렬화: JSON → SeatResponse (빠름!)
2. HTTP 직렬화: SeatResponse → JSON (???ms)

Entity → DTO 변환 단계 제거!
```

**왜 빠른가?**

1. **SeatResponse는 단순 DTO** - `@class` 타입 정보 불필요
2. **Flat 구조** - 중첩 객체 없음
3. **Cache HIT 시 변환 없음** - 바로 사용 가능

### 결과

```
성능 개선:
- P95: 1.82s → 829ms (54% 개선!) ✅

상세 측정:
Controller timing:
- Max: 62ms (이전 756ms에서 92% 개선!)
- Most: 18-32ms

Cache timing:
- Max: 55ms (이전 711ms에서 92% 개선!)
- Most: 30-47ms

Cache MISS (DTO 변환 포함):
- DB query: 14-26ms
- DTO mapping: 0ms (negligible)

k6 측정:
- P95: 829ms ❌ (목표 500ms 미달)
```

### 새로운 의문

```
Controller: 최대 62ms
k6 P95: 829ms
차이: 767ms

어디서 767ms가 소요되는가?
```

---

## 6. Phase 5: 진짜 병목 발견 - 대량 JSON 응답이라는 선택의 비용

### 분석

```
[우리가 측정한 영역]
Controller 시작
    ↓
Service 호출 (Queue 검증 + Cache 조회)
    ↓
DTO 반환
    ↓
Controller 종료 (32ms까지 측정) ← 여기서 로그 끝!
    ↓
================================ 측정 불가 영역
    ↓
[Spring Boot 자동 처리]
@RestController 감지
    ↓
HttpMessageConverter 호출
    ↓
Jackson ObjectMapper 직렬화
  - writeValueAsString()
  - List<SeatResponse> (8,629개) → JSON
    ↓ (~800ms 소요!)
JSON 문자열 생성
    ↓
HTTP Response Body 작성
    ↓
네트워크 전송
    ↓
================================
    ↓
[k6 Client]
측정 완료 (829ms)
```

### 증명

**Jackson 직렬화 시간 추정:**

```java
// 8,629개 SeatResponse 객체
for (SeatResponse seat : seats) {
    json.append("{");
    json.append("\"seatId\":").append(seat.seatId());
    json.append(",\"scheduleId\":").append(seat.scheduleId());
    json.append(",\"seatNumber\":\"").append(seat.seatNumber()).append("\"");
    json.append(",\"grade\":\"").append(seat.grade()).append("\"");
    json.append(",\"price\":").append(seat.price());
    json.append(",\"status\":\"").append(seat.status()).append("\"");
    json.append("}");
}
// 8,629개 × 6개 필드 = 51,774번의 연산
```

**계산:**
```
k6 측정: 829ms
Controller: 32ms
차이: 797ms

→ Spring Boot HTTP JSON 직렬화: ~800ms
```

### 근거

**중요: 이 병목은 Jackson의 비효율적인 구현 때문이라기보다, HTTP + JSON 포맷으로 대량 데이터를 단일 응답으로 반환하는 구조적 선택에서 발생한 비용에 가깝다.**

Jackson은 업계 표준 라이브러리로서 충분히 최적화되어 있으며, 문제는 **8,629개 객체를 한 번에 직렬화하는 방식** 자체에 있다.

**왜 직렬화에 시간이 걸리는가?**

1. **데이터 크기**: 8,629개 객체
2. **O(n) 복잡도**: 객체 수에 비례
3. **리플렉션 기반 필드 접근**: 필드 추출, 타입 변환
4. **문자열 연결**: StringBuilder append 반복 (8,629개 × 6개 필드 = 51,774번)

**비교 (여러 차례 측정 결과, 거의 선형적 경향):**
```
100개 객체: ~9ms
1,000개 객체: ~90ms
8,629개 객체: ~800ms

선형 비례 (O(n))
```

> **참고**: 실제 직렬화 시간은 GC 발생, JVM 힙 상태, StringBuilder 버퍼 확장 등 다양한 요인에 따라 달라질 수 있습니다. 다만 여러 차례 측정한 결과 객체 수에 거의 선형적으로 비례하는 패턴을 보였습니다.

---

## 7. 해결 방안 검토

### 방안 1: Pagination

**구현:**
```java
@GetMapping("/schedules/{scheduleId}/seats")
public Page<SeatResponse> getSeats(
    @PathVariable Long scheduleId,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "100") int size
) {
    // 100개씩 반환
}
```

**효과:**
```
직렬화 시간: 800ms × (100/8629) ≈ 9ms
총 응답 시간: 32ms + 9ms = 41ms ✅
```

**트레이드오프:**
- ✅ 근본적 해결
- ✅ UX 개선 (무한 스크롤)
- ❌ **비즈니스 요구사항 위배** (전체 좌석 한 번에 표시)

---

### 방안 2: JSON 문자열 직접 캐싱

**구현:**
```java
@Cacheable("availableSeatsJson")
String findAvailableSeatsAsJson(Long scheduleId) {
    List<SeatResponse> seats = ...;
    return objectMapper.writeValueAsString(seats);  // Cache MISS 시 1회만
}

@GetMapping(produces = "application/json")
String getSeats(...) {
    return findAvailableSeatsAsJson(...);  // 직렬화 스킵!
}
```

**효과:**
- Cache HIT: 직렬화 0ms
- 예상 P95: ~50ms ✅

**트레이드오프:**
- ✅ 즉시 적용 가능
- ✅ 클라이언트 변경 불필요
- ❌ **헥사고날 아키텍처 위반**

**아키텍처 위반 이유:**
```
[Domain/Application Layer]
- 비즈니스 로직만 담당해야 함
- Presentation 포맷(JSON)을 알아서는 안됨

[Presentation Layer]
- HTTP, JSON은 여기서 처리

JSON 문자열 캐싱 → Application Layer가 JSON 의존
→ gRPC, GraphQL 추가 시 재사용 불가
→ 관심사 분리 원칙 위반
```

**반론: "Cache Adapter에서 처리하면 되지 않나요?"**

물론 Cache Adapter 수준에서 JSON을 다루는 방식으로 레이어 경계를 조정할 수도 있습니다.

하지만 이 경우에도 문제가 있습니다:
- UseCase의 반환 타입이 `String`이 되는 순간, 도메인 유스케이스의 의미가 HTTP 포맷에 종속됩니다
- `List<SeatResponse> getAvailableSeats()`는 "좌석 목록을 반환한다"는 명확한 의미가 있지만
- `String getAvailableSeats()`는 "무엇을 반환하는가"의 의미가 사라집니다

**최종 판단:**
Cache는 "성능 최적화 기법"이지 "데이터 표현 방식"이 아닙니다. Infrastructure Layer(Cache)가 Presentation Layer의 직렬화 포맷(JSON)을 알게 되는 순간, 계층 간 의존성이 생깁니다.

**참고:**
소규모 서비스나 단기 프로젝트에서는 JSON 문자열 캐싱이 충분히 실용적인 선택이 될 수 있습니다. 이 글에서는 장기적인 확장성(gRPC, GraphQL 추가 등)과 아키텍처 일관성을 우선순위로 두고 판단했습니다.

---

### 방안 3: Binary Format (Protobuf)

**효과:**
```
JSON: 800ms
Protobuf: ~50ms (16배 빠름)
```

**트레이드오프:**
- ✅ 근본적 해결
- ✅ 대역폭 절감
- ❌ 클라이언트 수정 필요 (웹/앱)
- ❌ 개발 기간 2-3주
- ❌ 팀 전체 리소스 투입

---

### 방안 4: 현재 상태 수용 (829ms)

**근거:**

1. **Application Layer는 책임 범위 내에서 최적화 완료**
   ```
   - Cache: 55ms (92% 개선)
   - Service: 32ms
   - 비즈니스 로직, 데이터 조회, 캐시 전략, 객체 변환 범위에서
     유의미한 병목을 모두 제거함 ✅
   ```

2. **HTTP 직렬화는 Presentation Layer 책임**
   ```
   [Application] 비즈니스 로직 (32ms) ← 우리 책임
   [Presentation] HTTP 직렬화 (800ms) ← HTTP 레이어 책임
   ```

3. **829ms는 8,629개 데이터의 자연스러운 비용**
   ```
   100개: 41ms
   1,000개: 320ms
   8,629개: 829ms

   O(n) 복잡도 - 데이터 크기에 비례
   ```

4. **비즈니스 임팩트**
   ```
   Success Rate: 99.78% ✅
   사용자 체감: 1초 미만
   실제 문제: 없음
   ```

5. **Scale-up의 한계**
   ```
   2배 빠른 CPU: 829ms → 415ms (비용 2배)
   10배 빠른 CPU: 829ms → 83ms (현실적 불가)

   → 하드웨어로 해결 불가
   → 알고리즘 변경 필요 (Pagination/Binary)
   ```

---

## 8. 최종 결정: 현재 상태 수용

### 이유

#### 8.1 목표 재검토
```
원래 목표: P95 < 500ms
설정 근거: 개발자의 목표 수치

실제 비즈니스 요구사항:
- 전체 좌석 조회 ✅
- 99%+ Success Rate ✅
- 사용자 체감 속도 양호 ✅
```

#### 8.2 아키텍처 우선순위
```
성능 숫자 (500ms) < 아키텍처 원칙

- 헥사고날 아키텍처 유지
- 관심사 분리 준수
- 유지보수성 확보
```

#### 8.3 Application Layer 최적화 달성
```
Before: 5.23s (Cache 없음)
After: 829ms (Cache 최적화)
개선: 84% ✅

Application Layer (책임 범위 내): 5.2s → 0.032s (99.4% 개선!)
```

#### 8.4 근본 원인은 비즈니스 요구사항
```
문제: 8,629개 객체 직렬화 (O(n))
해결: n을 줄이기 (Pagination)
현실: 비즈니스에서 전체 좌석 표시 요구

→ 기술로 해결 불가능한 비즈니스 제약
```

---

## 9. 교훈 (Lessons Learned)

### 9.1 **성능 목표는 근거가 있어야 한다**

```
❌ "P95 < 500ms" (왜?)
✅ "사용자가 3초 이상 기다리면 이탈률 50% 증가" (데이터 기반)
```

829ms는 충분히 빠르지만, 500ms는 개발자가 임의로 정한 숫자였습니다.

### 9.2 **병목은 여러 겹으로 존재한다**

```
Layer 1: Redis Timeout (3s)
Layer 2: Cache Not Working (Silent Failure)
Layer 3: Cache Slow (Record 직렬화)
Layer 4: DTO Mapping (Entity → Response)
Layer 5: HTTP JSON Serialization (800ms) ← 진짜 병목
```

각 레이어를 하나씩 벗겨내며 최적화했습니다.

### 9.3 **Instrumentation 없이는 최적화 불가능**

```java
// 측정 없이는:
"느린 것 같아요" ← 주관

// 측정 후:
"Cache 역직렬화가 711ms 걸립니다" ← 객관
```

로그를 추가하여 정확한 병목 위치를 파악했습니다.

### 9.4 **아키텍처 원칙을 지키는 것도 최적화다**

JSON 문자열 캐싱으로 50ms를 달성할 수 있었지만, 헥사고날 아키텍처를 포기하는 비용이 더 컸습니다.

**단기 성능 < 장기 유지보수성**

### 9.5 **O(n) 문제는 하드웨어로 해결 안됨**

```
Scale-up: 상수 배 개선 (2x, 3x)
Algorithm: 복잡도 개선 (O(n) → O(1))

8,629개 데이터 → Pagination으로 100개
= 86배 개선 (O(n) → O(1))
```

알고리즘(데이터 크기) 변경이 하드웨어보다 효과적입니다.

### 9.6 **Cache Hit Rate ≠ Performance**

```
Cache Hit Rate: 98.6% ✅
But Performance: 1.82s ❌

원인: Cache Hit 시 역직렬화 711ms
```

캐시가 작동한다고 성능이 좋은 것은 아닙니다.

### 9.7 **DefaultTyping + Record + 대량 객체 캐싱의 조합이 성능 병목을 만들 수 있다**

**Record 자체는 문제가 아닙니다.**

이번 성능 이슈는 다음 세 가지가 결합되며 발생했습니다:

```java
1. GenericJackson2JsonRedisSerializer의 DefaultTyping
   → 타입 안전성을 위해 @class 정보 강제 삽입

2. Record (final 클래스)
   → DefaultTyping.NON_FINAL에서 제외되어 RecordSupportingTypeResolver 필요

3. 대량 객체 캐싱 (8,629개)
   → 각 객체마다 타입 정보 직렬화/역직렬화

→ 결과: Cache HIT 시 711ms 역직렬화
```

**Record는 Java의 훌륭한 기능**이며, 문제는 타입 정보를 강제로 포함시키는 DefaultTyping 전략과 대량 객체 캐싱이 결합된 상황에서 발생했습니다.

**해결책:**
- Response DTO는 Record 사용 ✅ (타입 정보 불필요)
- Domain Entity 대량 캐싱 시 타입 전략 재검토
- 또는 Entity 대신 DTO 캐싱

### 9.8 **비즈니스 제약이 기술 한계를 만든다**

```
기술적 최선: Pagination (9ms)
비즈니스 요구: 전체 좌석 표시
결과: 829ms 수용

기술은 비즈니스를 위한 도구
```

---

## 10. 최종 성능 비교

| 단계 | P95 | 개선율 | 주요 변경 |
|------|-----|--------|----------|
| **초기** | 5.23s | - | Cache 없음 |
| **Phase 1** | 5.23s | 0% | Redis Timeout 증가 |
| **Phase 2** | 5.23s | 0% | RecordSupportingTypeResolver |
| **Phase 3** | 1.82s | 65% | Cache 작동 시작 |
| **Phase 4** | 829ms | 54% | Response DTO 캐싱 |
| **최종** | **829ms** | **84%** | Application Layer 최적화 완료 |

### Application Layer 성능
```
- Cache: 55ms (92% 개선)
- Service: 32ms
- Controller: 32ms

HTTP JSON 직렬화: 800ms (Presentation Layer)
```

---

## 11. 결론

### 달성한 것
- ✅ **84% 성능 개선** (5.23s → 829ms)
- ✅ **Application Layer 99.4% 개선** (5.2s → 32ms)
- ✅ **99.78% Success Rate 유지**
- ✅ **헥사고날 아키텍처 준수**
- ✅ **Cache Hit Rate 98.6%**

### 발견한 것
- **진짜 병목: 대량 JSON 응답이라는 구조적 선택** (~800ms)
- **근본 원인: 8,629개 객체를 단일 HTTP 응답으로 반환하는 O(n) 복잡도**
- **해결 불가: 비즈니스 요구사항** (전체 좌석 표시)

### 배운 것
- 성능 목표는 **비즈니스 근거**가 있어야 한다
- **아키텍처 원칙 > 성능 숫자**
- **측정하지 않으면 최적화할 수 없다**
- **O(n) 문제는 하드웨어로 해결 안됨**

---

## 참고 자료

- [Java Record를 Redis에 캐싱하기 (우아한 기술블로그)](https://techblog.woowahan.com/22767/)
- Hexagonal Architecture (Ports and Adapters Pattern)
- Spring Cache Abstraction
- Jackson ObjectMapper Performance

---

**최종 판단: 829ms는 충분히 빠르다. 더 이상의 최적화는 아키텍처를 희생하거나 비즈니스 요구사항을 변경해야 한다.**
