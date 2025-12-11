# Coding Conventions & Standards

이 문서는 **콘서트 티켓팅 서비스** 개발 시 준수해야 할 코드 스타일, 네이밍 규칙, 아키텍처 제약 사항을 정의한다.
AI 에이전트는 코드를 작성할 때 본 문서의 규칙을 **엄격히 준수**해야 한다.

---

## 1. Project Structure (Modular Monolith + Hexagonal)

우리는 도메인별로 패키지를 최상위로 분리하고, 내부에서 헥사고날 계층을 구성한다.

### 1.1 Root Package Structure

```text
personal.ai
├── common               # 전역 공통 유틸리티, 예외, 설정 (Global Config)
├── user                 # [Domain] User Service (Auth)
├── queue                # [Domain] Queue Service (Redis)
├── booking              # [Domain] Booking Service (Core)
└── payment              # [Domain] Payment Service (Mock)
```

### 1.2 Internal Package Structure (Hexagonal)

각 도메인 패키지 내부는 다음과 같은 계층 구조를 가진다.

```text
personal.ai.booking
├── domain               # [Core] 순수 비즈니스 로직 (POJO)
│   ├── model            # Entities, Value Objects
│   └── service          # Domain Services (Business Logic)
├── application          # [App] 유스케이스 및 포트 정의
│   ├── port
│   │   ├── in           # UseCase Interfaces (Input Port)
│   │   └── out          # Repository/Adapter Interfaces (Output Port)
│   └── service          # Application Services (UseCase Impl)
└── adapter              # [Infra] 외부 기술 연동
    ├── in
    │   ├── web          # RestControllers, DTOs
    │   └── consumer     # Kafka Consumers
    └── out
        ├── persistence  # JPA Repositories, Entities
        ├── redis        # Redis Clients
        └── external     # RestClient (External API)
```

---

## 2. Naming Conventions

### 2.1 Classes & Interfaces

- **Class:** `PascalCase`
- **Interface:** `PascalCase` (접두사 `I` 사용 금지)
- **Implementation:**
    - Standard: `Impl` 접미사 지양. 의도를 드러내는 네이밍 권장.
    - Hexagonal Adapter: `~Adapter` 접미사 사용. (ex. `TicketRepository` -> `TicketJpaAdapter`)
- **DTO:** `~Request`, `~Response`, `~Command`, `~Result` 등 의도가 명확한 접미사 사용.
    - 단순 `Dto` 접미사는 지양한다.

### 2.2 Methods & Variables

- **Method:** `camelCase` (동사로 시작)
- **Variable:** `camelCase`
- **Constant:** `UPPER_SNAKE_CASE`
- **Test Method:** **한글 메서드명 허용**하여 가독성을 높인다. (ex. `@Test void 대기열_진입_성공()`)

### 2.3 Database

- **Table:** `snake_case` (ex. `concert_seat`)
- **Column:** `snake_case` (ex. `expired_at`)
- **Index:** `idx_{table}_{column}`

---

## 3. Java 21 & Spring Boot Standards

### 3.1 Java 21 Features

- **Record:** DTO, VO(Value Object), 이벤트 객체는 반드시 **`record`**를 사용한다.
- **Var:** 가독성을 해치지 않는 범위 내에서 로컬 변수 선언 시 `var`를 적극 활용한다.
- **Pattern Matching:** `instanceof`, `switch` 사용 시 패턴 매칭 문법을 적용한다.
- **SequencedCollection:** `List.getFirst()`, `getLast()` 등 신규 API를 사용한다.

### 3.2 Spring Framework

- **DI (Dependency Injection):**
    - 생성자 주입만 허용한다.
    - Lombok `@RequiredArgsConstructor` 사용 권장.
    - `@Autowired` 필드 주입 **절대 금지**.
- **HTTP Client:**
    - 외부 API 호출 시 `RestTemplate`, `WebClient` 대신 **`RestClient`** (Spring Boot 3.4 표준)를 사용한다.
- **Virtual Threads:**
    - `synchronized` 키워드 사용 금지 (**Pinning 이슈** 발생).
    - 동기화가 필요할 경우 `ReentrantLock`을 사용한다.

### 3.3 Lombok Usage Rules

- **Allowed:** `@Getter`, `@ToString`, `@RequiredArgsConstructor`, `@Builder`
- **Forbidden:**
    - `@Data` (사용 금지: 무분별한 Setter 및 HashCode 오버헤드 방지)
    - `@Setter` (Entity에는 사용 금지. 변경 로직은 도메인 메서드로 구현)

---

## 4. Layering & Architecture Rules

### 4.1 Dependency Rule

- **Domain Layer**는 어떤 외부 계층(Application, Adapter, Framework)에도 의존해서는 안 된다.
- **Adapter Layer**는 Application Layer(Port)에만 의존해야 한다.

### 4.2 Object Mapping

계층 간 이동 시 객체 변환을 철저히 수행한다.

- `Request DTO` -> `Command/Domain Model` -> `Entity`
- `Entity` -> `Domain Model` -> `Response DTO`
- **이유:** DB 스키마 변경이 API 스펙에 영향을 주거나, 그 반대의 경우를 방지하기 위함.

### 4.3 Transaction Management

- **ReadOnly Default:** 클래스 레벨에 `@Transactional(readOnly = true)`를 적용한다.
- **Write:** 데이터를 변경하는 메서드에만 `@Transactional`을 명시한다.
- **Scope:** 트랜잭션 범위 내에서 외부 API 호출(Network I/O)을 금지한다. (Safe Transaction Pattern 준수)

### 4.4 Transactional Propagation (Outbox Pattern)

Outbox 이벤트 저장은 반드시 비즈니스 로직과 **같은 트랜잭션**에서 수행되어야 한다.

```java
// Outbox Adapter에 MANDATORY 적용
@Transactional(propagation = Propagation.MANDATORY)
public void publishEvent(...) { }
```

- **MANDATORY:** 호출자가 트랜잭션을 시작하지 않으면 즉시 예외 발생 (Fail-Fast)
- **효과:** 데이터 정합성 보장을 아키텍처 레벨에서 강제

### 4.5 Service Separation (SRP + DIP)

- **SRP (Single Responsibility):** UseCase 당 하나의 Application Service 클래스
  - ❌ `BookingService` (여러 UseCase 혼재)
  - ✅ `SeatReservationService`, `ReservationQueryService` (UseCase별 분리)
- **DIP (Dependency Inversion):** private 메서드 → Port 인터페이스로 분리
  - 테스트 시 Mock 주입 가능
  - 확장 포인트 명확화 (OCP)

---


## 5. Exception Handling & Logging

### 5.1 Global Response Format

모든 API 응답은 아래 `ApiResponse` 레코드를 사용한다.

```java
public record ApiResponse<T>(
    String result, // "SUCCESS", "ERROR"
    String message,
    T data
) {}
```

### 5.2 Exception Hierarchy & Error Codes

- **Error Code Format:** `STRING_TYPE` (Enum style) (ex. `QUEUE_EXPIRED`, `SEAT_ALREADY_RESERVED`)
- **Structure:**
    - `BusinessException`: 최상위 클래스 (ErrorCode 필드 포함)
    - **Standard:** `EntityNotFoundException` (404), `InvalidValueException` (400)
    - **Domain Specific:**
        - `QueueCapacityExceededException` (Qxxx)
        - `SeatAlreadyReservedException` (Bxxx)

### 5.3 Logging Levels

- **ERROR:** 시스템 장애, DB 연결 실패, 500 에러 (Stack Trace 필수)
- **WARN:** 비즈니스 예외 (4xx), 예상 가능한 예외 (Stack Trace 제외 권장)
- **INFO:** 주요 비즈니스 흐름 성공, 상태 변경
- **DEBUG:** 개발 단계의 상세 로그 (운영 환경에서는 비활성화)

### 5.4 Logging Policy (PII & Context)

로그에 포함할 식별자와 레벨별 정책:

| 레벨 | 성공/실패 | 포함 정보 | PII 정책 |
|------|----------|----------|----------|
| DEBUG | 성공 | `userId`, `concertId`, `seatId` | 허용 |
| WARN | 실패 | `concertId`, `seatId` | `userId` 제외 |
| ERROR | 실패 | `concertId`, 에러 메시지 | `userId` 제외, Stack Trace 포함 |

```java
// ✅ Good
log.debug("Reservation created: reservationId={}, seatId={}", reservationId, seatId);
log.warn("Reservation expired: reservationId={}", reservationId);

// ❌ Bad (실패 로그에 userId 포함)
log.warn("Reservation failed: userId={}, reservationId={}", userId, reservationId);
```

---

## 5.5 var Pattern (Selective Usage)

Java 21의 `var`는 **가독성을 해치지 않는 범위**에서 선택적으로 사용한다.

**사용 권장:**
```java
var reservation = reservationRepository.findById(id);
var user = userRepository.findById(userId);
var result = transactionTemplate.execute(status -> ...);
```

**사용 지양 (타입 명시 필요):**
```java
int count = 0;              // 기본 타입
Instant now = Instant.now(); // 시간 타입 명확성
Duration timeout = Duration.ofSeconds(30);
BigDecimal price = new BigDecimal("10000");
```

---


## 6. Testing Strategy

### 6.1 Principles

- **BDD Style:** `Given` / `When` / `Then` 주석 패턴을 준수한다.
- **Coverage:** 커버리지 수치보다 **중요 비즈니스 로직(Happy Path + Edge Case)** 검증에 집중한다.


### 6.2 Test Code Conventions (Acceptance Test)

**Package Structure:**
```text
src/test/java/.../acceptance
├── steps        # Cucumber Step Definitions
└── support      # Test Adapters & Configuration
src/test/resources/features
└── *-acceptance.feature
```

**Naming Rules:**
- **Feature File:** `*-acceptance.feature` (e.g., `booking-acceptance.feature`)
- **Step Class:** `*AcceptanceSteps` (e.g., `BookingAcceptanceSteps`)
- **Http Adapter:** `*HttpAdapter` (Pure HTTP Client for User Action)
- **Test Adapter:** `*TestAdapter` (Helper for Data Setup/Verify)

**Implementation Rules:**
- **Black-Box:** `HttpAdapter`는 내부 로직이나 Repository에 접근하지 않고 **오직 HTTP**로만 통신한다.
- **Isolation:** 각 시나리오는 서로 격리되어야 하며(`@ScenarioScope`), 데이터 초기화는 `TestAdapter`를 통해 수행한다.
- **Concurrency:** 동시성 테스트는 Java 21 **Virtual Threads**를 활용한다.

### 6.3 Tools

- **Unit Test:** JUnit 5, AssertJ, Mockito
- **Integration Test:** `@SpringBootTest`, **Testcontainers** (Redis, MySQL, Kafka)
- **Acceptance Test:** Cucumber, RestAssured
- **Load Test:** k6 (API Level), nGrinder (Scenario Level)

---

## 7. Resilience Pattern (Circuit Breaker & Retry)

외부 시스템(PG Mock 등) 연동 시 `Resilience4j` 어노테이션을 활용한다.

- **Retry:** `@Retry(name = "payment")`
    - **대상:** `ConnectTimeoutException`, `502 Bad Gateway` (서버 도달 전 실패)
    - **제외:** `ReadTimeoutException`, `4xx Client Error` (중복 결제 방지)
    - **Backoff:** 200ms + Jitter
- **CircuitBreaker:** `@CircuitBreaker(name = "payment")`
    - **Slow Call:** 1초 이상 지연 시 느린 호출로 간주.
    - **Threshold:** 50% 이상 실패/지연 시 즉시 차단(Open).

---

## 8. Redis Conventions

### 8.1 Key Naming

- **Pattern:** `{domain}:{type}:{identifier}`
- **Examples:**
    - `queue:wait` (Waiting ZSet)
    - `queue:active` (Active ZSet)
    - `active:token:{userId}` (Meta Hash)
    - `seat:{seatId}` (Lock Key)

### 8.2 Atomicity Rules

- **Counters/Status:** 카운트 증가, 상태 변경 등 동시성 이슈가 있는 연산은 반드시 **`HINCRBY`** 또는 **Lua Script**를 사용한다.
- **Serialization:** JSON 직렬화/역직렬화가 빈번한 메타데이터는 `String` 대신 **`Hash`** 구조를 사용하여 CPU 부하를 줄인다.

### 8.3 Cleanup Strategy

- **Scanning:** 전체 스캔(`ZSCAN`, `KEYS`)을 금지한다.
- **Expiration:** 만료 처리는 **`ZREMRANGEBYSCORE`**를 사용하여 O(log N) 성능을 유지한다.

---

## 9. Security Conventions (Mock)

### 9.1 Mock Authentication Strategy (MVP)

- **Principle:** 유저 인증은 API Gateway 등 앞단에서 이미 수행되었다고 가정한다.
- **Implementation:**
    - `X-User-Id` 헤더를 신뢰하여 `Long` 타입의 User ID로 파싱한다.
    - 별도의 `SecurityContext`나 `Filter Chain`을 통한 검증을 생략한다.
- **Constraint:**
    - 프로덕션 코드에 하드코딩된 유저 정보나 비밀번호를 포함하지 않는다.
    - 추후 Spring Security 도입 시 영향을 최소화하도록 컨트롤러에서만 헤더를 참조한다.