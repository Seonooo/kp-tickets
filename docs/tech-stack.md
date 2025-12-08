# Technical Specifications & Stack

이 문서는 **콘서트 티켓팅 서비스** 구현에 필요한 기술 스택, 버전, 라이브러리 및 설정을 정의한다.
개발자는 본 문서에 정의된 버전을 엄격히 준수해야 하며, 임의로 하위 버전을 사용하거나 호환되지 않는 라이브러리를 추가해서는 안 된다.

---

## 1. Core Language & Framework

### 1.1 Java
- **Version:** **OpenJDK 21 (LTS)**
- **Key Features:**
    - **Virtual Threads:** 활성화 필수. 고성능 동시성 처리를 위해 Platform Thread 대신 사용한다.
    - **Records:** DTO는 전적으로 `record` 타입을 사용하여 불변성을 보장한다.
    - **Pattern Matching & Switch:** 가독성 향상을 위해 모던 문법을 적극 활용한다.
    - **SequencedCollection:** `List.getFirst()`, `getLast()` 등 신규 API 사용.

### 1.2 Spring Boot
- **Version:** **3.4.12+**
- **Modules:**
    - `spring-boot-starter-web` (Spring MVC, Servlet Container)
    - `spring-boot-starter-data-jpa`
    - `spring-boot-starter-data-redis`
    - `spring-boot-starter-security`
    - `spring-boot-starter-validation`
    - `spring-boot-starter-actuator` (Monitoring)
- **Configuration:**
    - `spring.threads.virtual.enabled=true` (필수 설정)
    - `server.tomcat.threads.max=...` (Virtual Thread 사용 시 톰캣 스레드 풀 튜닝 불필요, 기본값 유지)

---

## 2. Data Persistence & Caching

### 2.1 Database (RDBMS)
- **Product:** **MySQL 8.0** (Production), **H2** (Local/Test - MySQL Mode)
- **ORM:** Hibernate 6.x (Spring Data JPA)
- **Connection Pool:** HikariCP
- **Policies:**
    - **OSIV:** `spring.jpa.open-in-view=false` (DB 커넥션 고갈 방지)
    - **DDL Auto:** Production(`validate`), Local(`update` or `create-drop`)

### 2.2 Cache & Queue (NoSQL)
- **Product:** **Redis 7.x** (Cluster Mode 권장)
- **Client:** Lettuce (Spring Data Redis Default)
- **Serialization:**
    - Key: `StringRedisSerializer`
    - Value: `GenericJackson2JsonRedisSerializer` (JSON) 또는 `StringRedisSerializer` (Token Hash 구조 사용 시)
- **Critical Commands:**
    - `SETNX`, `ZADD`, `ZRANK`, `ZREMRANGEBYSCORE`, `HINCRBY`, `HMSET`, `EVAL` (Lua)

---

## 3. Infrastructure & Messaging

### 3.1 Message Broker
- **Product:** **Apache Kafka 3.6+**
- **Client:** Spring Kafka
- **Topic Strategy:**
    - `booking.payment.completed`: 결제 완료 이벤트 (Partitions: 3+, Replication: 2+)
- **Consumer Config:**
    - `enable.auto.commit=false` (Manual Ack 권장)
    - `isolation.level=read_committed` (Transactional Consumer)

### 3.2 External API Client
- **Interface:** **`RestClient`** (Spring Framework 6.1+ / Boot 3.2+)
- **Why:** `RestTemplate`은 유지보수 모드이며, `WebClient`는 Reactive 의존성(Netty)을 가져오므로, 가상 스레드 친화적인 `RestClient`를 표준으로 사용한다.

### 3.3 Resilience & Fault Tolerance (Planned)
- **Library:** **Resilience4j** (Spring Boot 3 Starter)
- **Target:** 외부 API 호출 (`PaymentMockService`, `RestClient`)
- **Modules:** `Retry`, `CircuitBreaker`
- **Annotation:** `@Retry`, `@CircuitBreaker` (AOP 기반 적용)
- *Note: 현재 구현 단계에서는 미적용 상태임.*

---

## 4. Testing Strategy

### 4.1 Frameworks
- **Test Runner:** JUnit 5 (Jupiter)
- **Assertion:** AssertJ (`assertThat`)
- **Mocking:** Mockito (`@MockBean`, `@SpyBean`)

### 4.2 Integration Testing (Critical)
- **Tool:** **Testcontainers**
- **Policy:**
    - 인메모리 Redis(Embedded Redis) 대신 **Docker 기반의 Testcontainers**를 사용하여 실제 운영 환경과 동일한 Redis 7.x, MySQL 8.0 환경에서 테스트한다.
    - Kafka 역시 Testcontainers(`Confluentinc`)를 사용한다.

### 4.3 Load Testing (Optional)
- **Tool:** k6 or JMeter
- **Target:** 대기열 진입 및 폴링 성능, 동시 좌석 선점 정합성 검증.

---

## 5. Build & Dev Tools

- **Build Tool:** **Gradle 8.5+**
- **DSL:** Groovy DSL (`build.gradle`)
- **Utils:**
    - **Lombok:** (`@RequiredArgsConstructor`, `@Getter`, `@ToString` 위주 사용)
    - **Jackson:** (`ObjectMapper` - JavaTimeModule 등록 필수)

---

## 6. Security Specification (Mock)

- **Strategy:** **Header-Based Mock Authentication**
- **Rationale:** MVP 단계의 복잡도 제어를 위해 실제 인증(JWT) 대신 **Trusted Gateway** 패턴을 가정한다.
- **Mechanism:**
    - 모든 요청은 API Gateway(또는 앞단)에서 이미 인증되었다고 가정한다.
    - `X-User-Id` 헤더를 신뢰하여 유저를 식별한다.
    - 별도의 검증 로직이나 `Spring Security` 필터 체인은 사용하지 않는다.
- **Future Work:**
    - Spring Security 도입 및 JWT 검증 로직 구현.