---
branch: refactor/booking-code-review
created: 2026-04-22
updated: 2026-04-22
status: in-progress
tags: [code-review, booking, refactor, outbox, idempotency]
related:
  - "[[convention]]"
  - "[[architecture]]"
  - "[[pitfalls]]"
  - "[[erd]]"
---

# Refactor: Booking Code Review

> `/code-review` 결과 발견된 이슈를 우선순위에 따라 해결하는 작업 계획.

---

## 📋 이슈 보드

| # | 우선순위 | 상태 | 제목 | 관련 파일 |
|---|---------|------|------|----------|
| 1 | 🔴 CRITICAL | ✅ Done | adapter → service 역방향 의존 | `OutboxEventPersistenceAdapter`, `OutboxEventService` |
| 2 | 🟠 HIGH | ✅ Done | `@Transactional` 범위 내 Kafka 발행 | `OutboxEventService`, `OutboxEventProcessor` |
| 3 | 🟠 HIGH | ✅ Done | Consumer 멱등성 부재 | `ReservationConfirmService` |
| 4 | 🟡 MEDIUM | ✅ Done | `orElse(null)` + null 체크 패턴 (+ CANCELLED 버그 수정) | `BookingManager.expireReservation` |
| 5 | 🔴 CRITICAL | ✅ Done | `List<Seat>` Redis 역직렬화 실패 (PTV allowlist 누락) | `RedisCacheConfig` |

**범례:** 🔴 CRITICAL · 🟠 HIGH · 🟡 MEDIUM · 🟢 LOW · ✅ Done · 🔄 In Progress · ⏸️ Todo · ❌ Blocked · 🚫 Dropped

---

## 🔀 범위 제약

- **In Scope:** `core-service/booking/**`
- **Out of Scope:**
  - `queue-service/adapter/in/consumer/PaymentEventConsumer.java` → 별도 브랜치 (`refactor/queue-consumer-idempotency`)
  - Resume 관련 파일 (`docs/resume/*`, `crawl-*`, `k6-tests/queue-*`) → unstaged 처리 완료

---

## 📜 의사결정 로그

### [#1] 도메인 정책 상수는 `{도메인}Policy` 클래스에

**선택지:**
- A) Port 인터페이스에 상수
- B) 도메인 상수 클래스 ⭐
- C) Adapter 자체 상수 중복 선언

**결정:** B안.

**근거:**
- "재시도 3회"는 **비즈니스 정책**이지 인프라 설정이 아님
- 도메인 개념으로 명시하면 Adapter/Service 양쪽이 동일 상수를 바라봄 → 불일치 방지
- [[convention#4.5-Domain-Policy-Constants]] 에 규칙 추가됨
- [[memory:feedback_domain_policy_constants]] 에 기록됨

---

### [#2] Outbox 발행 트랜잭션은 이벤트 단위 분리 (A안)

**선택지:**
- A) `OutboxEventProcessor` 분리 + `REQUIRES_NEW` ⭐
- B) `@Transactional` 제거 + Consumer 멱등성에 위임

**결정:** A안.

**근거:**
- B안은 "중복 발행을 전제"로 설계하는 구조 → 컨슈머에 책임 전가
- A안은 발행 측이 **스스로 완결**, 멱등성은 네트워크 장애 등 예외 상황의 **마지막 안전망**
- "각 계층은 자기 책임을 다해야 한다"는 원칙 적용
- 단, Self-invocation 문제로 클래스 분리 필수

---

### [#3] Consumer 멱등성은 도메인 상태 기반

**선택지:**
- A) 도메인 상태 체크 후 skip ⭐
- B) 처리 이벤트 ID 별도 테이블
- C) Kafka Exactly-Once 설정

**결정:** A안.

**근거:**
- 이미 `reservation.isConfirmed()` 조회 중이라 **추가 비용 0**
- 기존 `uk_schedule_seat`, `uk_payment_reservation_id` 는 **insert 중복 방지**용으로 Consumer 멱등성과 성격이 다름 ([[erd#C-reservations]] 참고)
- B안은 도메인 상태로 판단 어려운 이벤트가 생기면 그때 도입

**주의:** Consumer 멱등성 체크를 `ensureOwnership` 이후·`isExpired` 이전에 배치. 이유: 보안 체크는 항상 유지하되, 이미 확정된 예약은 만료 검증 불필요.

---

### [#4] Redis 직렬화 검증은 Serializer 단위 테스트 + 설정 추출

**선택지:**
- A) `@SpringBootTest` + Testcontainers Redis 통합 테스트
- B) `GenericJackson2JsonRedisSerializer` 단위 테스트 + `buildCacheValueSerializer()` 추출 ⭐
- C) 아무 테스트도 안 두고 수동 확인

**결정:** B안.

**근거:**
- A안은 컨테이너 부팅(5초+)·Docker 의존성으로 테스트 피드백이 느림
- B안은 실제 Bean이 사용하는 static factory 메서드를 그대로 호출 → **설정 드리프트 구조적으로 차단**
- `@Cacheable` AOP 자체는 Spring이 보장하는 영역, 우리가 검증해야 할 건 "우리가 만진 직렬화 설정"
- 결과적으로 B안으로 **프로덕션을 뚫고 있던 PTV allowlist 버그**를 6초 만에 발견

**주의:** PTV 확장(`java.lang`/`java.math`/`java.time`)은 로컬 Redis 캐시 한정의 완화 조치.
외부 입력을 역직렬화하는 경로(Kafka payload 등)에는 동일 서비스를 재사용하지 말 것.

---

## 🗂️ 작업 로그

### Step 1. `OutboxPolicy` 도메인 상수 분리 ✅
- **Created:** `domain/model/OutboxPolicy.java`
- **Modified:** `OutboxEventService`, `OutboxEventPersistenceAdapter`, `OutboxEventServiceTest`
- **Docs:** [[convention#4.5-Domain-Policy-Constants]] 추가
- **Memory:** `feedback_domain_policy_constants.md` 기록

### Step 2. `ReservationConfirmService` 멱등성 추가 ✅
- CONFIRMED 상태면 조용히 반환 (Kafka at-least-once 대응)
- 순서: findById → ensureOwnership → **isConfirmed skip** → isExpired → confirm
- 테스트: `confirmReservation_alreadyConfirmed_idempotentSkip`, `confirmReservation_cancelled_throwsInvalidState`

### Step 3. `OutboxEventProcessor` 분리 ✅
- **Created:** `application/service/OutboxEventProcessor.java` (`REQUIRES_NEW`)
- **Modified:** `OutboxEventService` → 오케스트레이터로 축소
- **Tests:** `OutboxEventServiceTest` (위임 검증), `OutboxEventProcessorTest` (실제 로직)

### Step 4. `orElse(null)` 패턴 개선 ⏸️
- Target: `BookingManager.expireReservation`
- Todo: `Optional.ifPresent` 또는 early return 패턴 적용

### Step 5. `Seat` Redis 직렬화 검증 ✅
- **Created:** `RedisCacheConfigSerializationTest` (라운드트립 회귀 방어망)
- **Refactored:** `RedisCacheConfig.buildCacheValueSerializer()` static 메서드 추출 → Bean과 테스트가 동일 설정 공유 (드리프트 방지)
- **🔴 발견한 진짜 버그:** PTV allowlist가 `personal.ai` + `java.util` 만 허용 →
  `RecordSupportingTypeResolver(NON_FINAL)`이 `Long`/`BigDecimal`/`enum`에도 `@class` 타입 힌트를 붙이면서
  역직렬화 시 `InvalidTypeIdException` 발생. 프로덕션에서는 `CustomCacheErrorHandler`가 조용히 로깅만 하고
  DB 폴백 → **1초 TTL 캐시가 사실상 항상 miss 상태로 운영됐음**.
- **Fix:** PTV allowlist에 `java.lang`, `java.math`, `java.time` 추가.
- **검증 커버리지:**
  - `List<Seat>` 라운드트립
  - 빈 리스트 라운드트립
  - 파생 접근자(`isReserved/isAvailable/isOccupied`) JSON 비유출
  - 단건 `Seat` 라운드트립 (record 타입 보존)

---

## 🔗 참고 문서
- [[convention]] — 코딩 규칙
- [[architecture]] — 헥사고날 아키텍처 원칙
- [[pitfalls]] — 과거 실수 이력
- [[erd]] — DB 스키마 및 유니크 제약
