# System Architecture & Design Specifications

이 문서는 **콘서트 티켓팅 서비스**의 최상위 아키텍처 설계 문서이다.
모든 구현은 이 문서에 정의된 **Hexagonal Architecture 원칙**, **Hybrid Queue 전략**, 그리고 **Safe Transaction 패턴**을 엄격히 준수해야 한다.

---

## 1. High-Level Architecture Patterns

### 1.1 Hexagonal Architecture (Ports and Adapters)
우리는 비즈니스 로직(Domain)을 외부 기술(Web, DB)로부터 철저히 격리하기 위해 헥사고날 아키텍처를 채택한다.

- **Domain Layer (`domain`)**:
    - 외부 의존성이 전혀 없는 순수 Java POJO.
    - 핵심 비즈니스 로직, Entity, Domain Service 포함.
- **Application Layer (`application`)**:
    - 도메인 로직을 조합하여 유스케이스(UseCase)를 처리.
    - `Input Port` (UseCase Interface)와 `Output Port` (Repository/Adapter Interface)를 정의.
- **Adapter Layer (`adapter`)**:
    - **Inbound:** Web Controller (`@RestController`), Kafka Consumer.
    - **Outbound:** Persistence (`Spring Data JPA`), Redis Client, External API Client (`RestClient`).

### 1.2 MSA-Ready Modular Monolith
물리적으로는 하나의 Spring Boot 애플리케이션으로 구동될 수 있으나, 논리적으로는 **User**, **Queue**, **Booking** 도메인이 완벽히 분리되어야 한다.
- **Rule:** 타 도메인의 테이블에 직접 접근(JOIN)하는 것을 금지하며, 반드시 정의된 `Port`를 통해서만 데이터를 요청한다.

---

## 2. Domain & Responsibility

| 도메인 | 역할 (Responsibility) | 주요 기술 요소 |
|:---:|:---|:---|
| **Queue** | 트래픽 제어, 대기열 순번 발급, Active 유저 관리 (The Shield) | Redis (ZSet+Hash), Scheduler |
| **Booking** | 좌석 조회/선점, 주문 생성, 결제 트랜잭션 관리 (The Core) | MySQL, Redis Lock (Fail-Fast), Virtual Threads |
| **User** | 회원 가입/로그인(JWT) | MySQL, Spring Security |
| **Payment** | 결제 승인/취소 (Mocking 처리) | `PaymentMockService` |

---

## 3. Deep Dive: Hybrid Queue System Strategy

대기열 시스템은 **Redis**를 사용하여 **대기(Wait)** 상태와 **활성(Active)** 상태를 관리한다.
성능 최적화(CPU 부하 감소)와 대규모 트래픽 제어를 위해 **ZSet**과 **Hash**를 혼용하는 **Hybrid 구조**를 사용한다.

### 3.1 Redis Key Structure

| 구분 | Redis Key | 자료구조 | 역할 및 특징 |
|:---:|:---|:---:|:---|
| **Wait Queue** | `queue:wait` | **ZSet** | **순서 보장 대기열**<br>- Member: `userId`<br>- Score: `Timestamp` (진입 시간) |
| **Active Queue** | `queue:active` | **ZSet** | **입장 인원(Capacity) 관리 & 청소**<br>- Member: `userId`<br>- Score: `Expire Timestamp` (만료 예정 시간) |
| **Active Token** | `active:token:{userId}` | **Hash** | **O(1) 검증 & 메타 데이터 관리**<br>- Field `token`: 검증용 UUID<br>- Field `status`: "ACTIVE"<br>- Field `extend_count`: **연장 횟수 (Integer)**<br>- TTL: Redis Native Expire (5분/10분) |

### 3.2 Workflow & Algorithms

![img.png](img.png)

#### A. 인입 (Ingestion)
1. 유저 요청 시 `queue:wait` (ZSet)에 적재 (`ZADD`).
2. 대기 순번 리턴 (`ZRANK`).
3. **Active Queue나 Token은 생성하지 않음.**

#### B. 전환 (Move Scheduling)
스케줄러(Worker)가 주기적으로 실행되어 유저를 Wait -> Active로 이동시킨다.
- **Lua Script 사용 필수:**
    1. `queue:active` 여유분 확인.
    2. `queue:wait`에서 Pop.
    3. `queue:active`에 `Score = 현재시간 + 5분`으로 `ZADD` (진입 대기 상태).
    4. `active:token:{userId}` Hash 생성 및 **TTL 5분** 설정.

#### C. 검증 및 연장 (Validation & Extension)
- **C-1. 최초 진입 (Page Access):**
    - 유저가 예매 페이지 진입(`GET /api/v1/bookings`) 시 토큰을 검증한다.
    - 유효하다면 즉시 **TTL을 10분으로 초기화**한다.
        - `EXPIRE active:token:{userId} 600`
        - `ZADD queue:active {현재시간 + 600} {userId}`

- **C-2. 명시적 연장 (User Action):**
    - 유저가 '시간 연장' 버튼 클릭 시 (`POST /api/v1/queue/extension`):
    1. `HGET active:token:{userId} extend_count` 조회.
    2. 값이 2 이상이면 에러 반환.
    3. 2 미만이면:
        - **`HINCRBY active:token:{userId} extend_count 1`**.
        - **`EXPIRE active:token:{userId} 600`** (다시 10분 세팅).
        - **`ZADD queue:active {현재시간 + 600} {userId}`** (청소 타겟 갱신).

#### D. 청소 (Cleanup Scheduling)
- **주기:** 1초 (Virtual Thread 활용).
- **로직:** `queue:active` 전체를 스캔(`ZSCAN`)하지 않는다.
- **명령어:** **`ZREMRANGEBYSCORE queue:active -inf {현재시간}`**
    - 만료 시간이 지난 유저만 O(log N)으로 효율적으로 삭제하여 대기열 슬롯을 확보한다.

---

## 4. Deep Dive: Booking & Transaction Flow

결제는 Mocking이지만, 데이터 정합성을 위한 트래픽 제어와 트랜잭션 범위는 실무 수준으로 구현한다.

### 4.1 Seat Reservation Strategy (Fail-Fast)
일반적인 분산락(Spin-Lock)은 대기열을 유발하므로 사용하지 않는다.
대신 **Redis `SETNX`를 활용한 Non-Blocking 선점** 전략을 사용하여 DB 부하를 원천 차단한다.

1.  **Redis Pre-occupation (1차 방어):**
    - `SETNX seat:{id} {userId} EX 300` 명령 사용.
    - **성공(1):** 즉시 다음 단계(DB 트랜잭션)로 진입.
    - **실패(0):** 대기하지 않고 **즉시 `SeatAlreadyReservedException` 반환.** (DB 접근 없음)

2.  **DB Persistence (2차 방어 & 데이터 저장):**
    - `seat_reservation` 테이블에 `(concert_id, seat_id)` Unique Index 필수.
    - Redis를 통과한 유저만 `INSERT`를 수행하며, 상태는 `PENDING`이다.

### 4.2 Payment Transaction Flow (Safe Pattern)
데이터 정합성을 위해 결제 로직은 반드시 아래 순서를 따른다.

1.  **좌석 선점 및 주문 생성 (Pending):**
    - Redis 락(`SETNX`) 성공 시, DB에 주문 및 좌석 정보를 `PENDING` 상태로 저장한다.
    - 1차 트랜잭션 커밋 완료.
2.  **Mock 결제 요청:**
    - DB 트랜잭션 범위 밖에서 `PaymentMockService`를 호출한다. (외부 I/O 분리)
    - 성공/실패 여부 응답 수신.
3.  **상태 확정 (Finalize):**
    - **결제 성공 시:** DB 상태를 `SUCCESS`로 업데이트하고, 트랜잭션 커밋 후(`After Commit`) Kafka 이벤트를 발행한다.
    - **결제 실패 시:** DB 상태를 `CANCEL`로 업데이트하고, Redis 선점(`seat:{id}`)을 해제(`DEL`)한다.

### 4.3 Constraints
- **Transactional Separation:**
    - DB 트랜잭션(`@Transactional`) 내부에서 외부 I/O(Mock API, Kafka, Redis) 작업을 수행하지 않는다.
    - 구조를 명확하게 하고 역할 분리를 통한 테스트 용이성을 고려하여 Facade Pattern을 적용한다.
- **Kafka Outbox:**
    - 결제 성공 후 이벤트 발행은 반드시 DB 커밋이 완료된 후(`TransactionalEventListener(AFTER_COMMIT)`) 수행한다.
    - Queue Service는 이 이벤트를 구독하여 해당 유저를 대기열에서 즉시 삭제한다.

---

## 5. Resilience & Fault Tolerance Strategy

대규모 트래픽 상황에서 외부 시스템(PG사)의 지연이 전체 시스템의 마비로 전이되는 것을 막기 위해 **Fail-Fast** 전략을 채택한다.
**데이터 정합성**을 최우선으로 하여 "Zombie Request(결과를 모르는 요청)"를 원천 차단한다.

### 5.1 Retry Strategy (재시도 정책)
- **Target:** `PaymentMockService` (외부 결제 요청)
- **Philosophy:** 연결 자체가 안 된 경우만 재시도하고, 응답이 늦는 경우는 중복 결제 위험이 있으므로 재시도하지 않는다.

| 설정 항목 | 값 / 조건 | 비고 |
|:---|:---|:---|
| **Max Attempts** | **1회** (최초 1회 + 재시도 1회 = 총 2회) | 무한 재시도 방지 |
| **Backoff** | **200ms (Fixed) + Jitter** | 속도전이므로 짧은 간격 + 분산 처리 |
| **Retry On** | `ConnectTimeoutException`<br>`502 Bad Gateway` | 서버 도달 전 실패 (안전함) |
| **No Retry** | `ReadTimeoutException`<br>`503`, `504`<br>`4xx Client Error` | 서버 도달 후 지연/실패 (중복 결제 위험) |

### 5.2 Circuit Breaker Strategy (차단 정책)
- **Target:** `PaymentMockService`
- **Philosophy:** 스레드 고갈 방지를 위해 조금이라도 이상 징후(지연/에러)가 보이면 즉시 차단(Open)한다.

| 설정 항목 | 값 | 근거 (Rationale) |
|:---|:---|:---|
| **Type** | `TIME_BASED` (Sliding Window) | 대규모 트래픽 특성상 시간 단위 통계가 유효함 |
| **Window Size** | **10초** | 최근 10초간의 트래픽을 표본으로 삼음 |
| **Min Calls** | **100회** | 최소 100건 이상의 요청이 있을 때만 판단 (통계적 유의미성) |
| **Failure Rate** | **50%** | 에러(500 등)가 절반 이상이면 차단 |
| **Slow Call** | **1000ms (1s)** | ReadTimeout(3s)까지 기다리면 늦음. 1초 넘으면 느린 것으로 간주 |
| **Slow Rate** | **50%** | 1초 이상 지연이 절반을 넘으면 차단 |
| **Wait Duration** | **10초** | Circuit Open 시 10초간 대기 후 Half-Open 전환 |
| **Half-Open** | **10회 허용** | 복구 여부 판단을 위해 10개의 요청을 흘려봄 |

### 5.3 Implementation Guide
- **Fallback:**
    - Circuit Breaker가 열리거나(Open) 재시도가 모두 실패한 경우, 즉시 **`PaymentFailedException`** (또는 503)을 반환하여 유저에게 "잠시 후 다시 시도해주세요"를 안내한다.
    - 억지로 성공 처리하거나 큐에 넣지 않는다. (Clean Failure)
  
---