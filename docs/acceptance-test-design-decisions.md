# Queue Service 인수 테스트 설계 결정 기록

## 1. 문제 정의

### 기존 테스트의 문제점

기존 `queue.feature` + `QueueSteps.java` 조합은 **인수 테스트가 아닌 통합 테스트**였다.

| 구분 | 기존 방식 | 문제점 |
|:---|:---|:---|
| **상태 세팅** | `MoveToActiveQueueUseCase` 직접 호출 | 내부 구현에 의존 |
| **데이터 초기화** | `QueueRepository.clearAll()` 직접 호출 | API 경계 위반 |
| **API 호출** | `UseCase.enter(command)` 직접 호출 | HTTP를 거치지 않음 |

```java
// 기존 방식: 내부 Bean 직접 사용
@And("사용자가 활성 상태이다")
public void 사용자가_활성_상태이다(String userId) {
    moveToActiveQueueUseCase.moveWaitingToActive(concertId);  // ← 내부 호출
}
```

---

## 2. 고민했던 내용

### 2.1 별도 Source Set vs 기존 구조 유지

**Option A: `acceptanceTest` Source Set 분리**
- 장점: 완전한 격리, 별도 Gradle task
- 단점: 구조 복잡, 설정 추가 필요

**Option B: 기존 `src/test/` 구조 유지**
- 장점: 단순함, 기존 인프라 재사용
- 단점: 통합 테스트와 혼재

→ **결정: Option B** (기존 구조 유지, `@acceptance` 태그로 구분)

### 2.2 시나리오 작성 스타일

**Option A: API 중심 (기술적)**
```gherkin
When POST /api/v1/queue/enter API를 호출한다
Then 응답 코드는 201이다
```

**Option B: 비즈니스 중심 (자연어)**
```gherkin
When 대기열에 등록을 요청한다
Then 대기열 등록이 완료된다
```

→ **결정: Option B** (기획자/비개발자도 이해 가능하도록)

### 2.3 Adapter 추상화 레이어

**Option A: Step에서 직접 RestAssured 사용**
- 장점: 코드 단순
- 단점: HTTP 호출 코드 중복, 변경 시 여러 곳 수정

**Option B: QueueHttpAdapter 분리**
- 장점: 단일 책임, 재사용성, 변경 용이
- 단점: 파일 하나 추가

→ **결정: Option B** (Adapter 패턴으로 추상화)

### 2.4 RestAssured 포트 주입 방식

**Option A: `@LocalServerPort` 필드 주입**
```java
@LocalServerPort
private int port;
```
- 문제: `@Component`에서 사용 시 Bean 생성 시점에 포트 미확정 → 실패

**Option B: `@Value("${local.server.port}")` 생성자 주입**
```java
public QueueHttpAdapter(@Value("${local.server.port}") int port)
```
- 문제: 동일하게 Bean 생성 시점 문제 발생

**Option C: `Environment.getProperty()` Lazy 조회**
```java
private int getPort() {
    return environment.getProperty("local.server.port", Integer.class, 8081);
}
```
- 장점: 호출 시점에 포트 조회 (서버 시작 후)

→ **결정: Option C** (런타임 시점에 동적으로 포트 조회)

### 2.5 스케줄러 상태 전환 처리 (WAITING → READY)

스케줄러가 주기적으로 대기열 사용자를 READY 상태로 전환하는데, 테스트에서 이를 어떻게 대기할 것인가?

**Option A: `Thread.sleep()` 고정 대기**
```java
Thread.sleep(2000);  // 스케줄러 주기(1초) × 2
```
| 장점 | 단점 |
|:---|:---|
| 구현 단순 | 고정 시간 → 느린 테스트 |
| 이해하기 쉬움 | 환경에 따라 불안정 (CI 느릴 때 실패) |
| | 일찍 완료되어도 계속 대기 |

**Option B: Awaitility 폴링** ✅ 선택
```java
await().atMost(10, SECONDS)
    .pollInterval(500, MILLISECONDS)
    .untilAsserted(() -> assertThat(status).isEqualTo("READY"));
```
| 장점 | 단점 |
|:---|:---|
| 상태 기반 → 빠른 종료 | 폴링 리소스 소비 (500ms마다 HTTP 호출) |
| 타임아웃 명확 | 외부 라이브러리 의존 |
| 디버깅 용이 | |

**Option C: Virtual Thread + 폴링 (Java 21)**
```java
Thread.ofVirtual().start(() -> {
    while (!isReady()) {
        Thread.sleep(Duration.ofMillis(100));
    }
    latch.countDown();
});
latch.await(10, SECONDS);
```
| 장점 | 단점 |
|:---|:---|
| OS 스레드 점유 없음 | 구현 복잡도 증가 |
| 짧은 폴링 간격 가능 | Awaitility 대비 가독성 낮음 |

**Option D: 테스트 전용 트리거 API**
```java
@PostMapping("/test/trigger-scheduler")
public void triggerScheduler() { ... }
```
| 장점 | 단점 |
|:---|:---|
| 즉시 실행 → 가장 빠름 | 테스트 코드가 프로덕션에 포함 |
| 결정적 (deterministic) | 실제 스케줄러 동작 검증 안됨 |

→ **결정: Option B** (Awaitility) - 단일 사용자 상태 대기에 적합

---

### 2.6 동시성 테스트의 병렬 요청 처리 (Java 21 Virtual Thread)

50명의 사용자가 동시에 대기열에 진입하는 시나리오에서 어떻게 병렬 요청을 보낼 것인가?

**Option A: CompletableFuture (기존)**
```java
List<CompletableFuture<Response>> futures = new ArrayList<>();
for (int i = 0; i < 50; i++) {
    futures.add(CompletableFuture.supplyAsync(() -> 
        httpAdapter.enterQueue(concertId, userId)));
}
CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
```
| 장점 | 단점 |
|:---|:---|
| Java 8+ 호환 | ForkJoinPool 크기 제한 (기본 CPU 코어 수) |
| 익숙한 API | 50개 요청 시 스레드 풀 병목 가능 |

**Option B: Virtual Thread (Java 21)** ✅ 권장
```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<Response>> futures = new ArrayList<>();
    for (int i = 0; i < 50; i++) {
        futures.add(executor.submit(() -> 
            httpAdapter.enterQueue(concertId, userId)));
    }
    for (Future<Response> future : futures) {
        responses.add(future.get());
    }
}
```
| 장점 | 단점 |
|:---|:---|
| 스레드 풀 제한 없음 | Java 21+ 필요 |
| 진정한 동시 실행 (1:1 요청:스레드) | 새 API 학습 필요 |
| OS 스레드 점유 없음 | |
| 실제 트래픽 패턴과 유사 | |

**왜 Virtual Thread가 동시성 테스트에 더 적합한가?**

```
CompletableFuture (ForkJoinPool):
┌─────────────────────────────────────────────┐
│  ForkJoinPool (기본 8 스레드)                │
│  ┌───┬───┬───┬───┬───┬───┬───┬───┐         │
│  │ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │ 7 │ 8 │→ 병목!  │
│  └───┴───┴───┴───┴───┴───┴───┴───┘         │
│       ↓ 50개 요청이 8개 스레드에서 순차 처리  │
└─────────────────────────────────────────────┘

Virtual Thread:
┌─────────────────────────────────────────────┐
│  Virtual Threads (50개 생성)                 │
│  ┌───┬───┬───┬───┬ ... ┬───┬───┬───┐       │
│  │ 1 │ 2 │ 3 │ 4 │     │48 │49 │50 │       │
│  └───┴───┴───┴───┴ ... ┴───┴───┴───┘       │
│       ↓ 50개 요청이 진정한 동시 실행          │
└─────────────────────────────────────────────┘
```

→ **결정: Option B** (Virtual Thread) - 동시성 테스트의 Happy Path에 적합

> **참고**: 단일 상태 대기는 Awaitility, 대규모 동시 요청은 Virtual Thread로 역할 분담

---

## 3. 최종 구조

```
src/test/
├── java/.../acceptance/
│   ├── steps/
│   │   ├── QueueSteps.java           # 기존 통합 테스트용
│   │   └── QueueAcceptanceSteps.java # 인수 테스트용 (HTTP only)
│   └── support/
│       ├── QueueTestAdapter.java     # 기존 UseCase 기반
│       └── QueueHttpAdapter.java     # HTTP 기반
└── resources/features/
    ├── queue.feature                 # 기존 통합 테스트
    └── queue-acceptance.feature      # 인수 테스트 (@acceptance)
```

---

## 4. 핵심 설계 원칙

| 원칙 | 적용 |
|:---|:---|
| **Black-Box** | 내부 Bean 호출 금지, HTTP API만 사용 |
| **Adapter 추상화** | `QueueHttpAdapter`로 HTTP 호출 캡슐화 |
| **비즈니스 언어** | 기획자가 읽을 수 있는 시나리오 작성 |
| **테스트 격리** | 시나리오마다 고유 ID 생성 (`UUID`) |
| **Lazy Resolution** | 포트를 런타임에 동적 조회 |

---

## 5. 실행 방법

```powershell
# 인수 테스트만 실행
./gradlew :queue-service:test --tests "*CucumberTest*" "-Dcucumber.filter.tags=@acceptance"

# 전체 테스트 실행
./gradlew :queue-service:test
```

---

## 3. Booking Service 인수 테스트 설계

Booking Service 역시 Queue Service와 동일한 철학을 따르되, 복잡한 데이터 의존성을 해결하기 위해 **Two-Adapter Pattern**을 명시적으로 도입한다.

### 3.1 Adapter 역할 분리 (Two-Adapter Pattern)

| Adapter | 역할 | 사용 예시 | 의존성 |
|:---|:---|:---|:---|
| **BookingHttpAdapter** | **API 호출 (User Action)** | `.reserveSeat()` | `RestAssured` (Pure HTTP) |
| **BookingTestAdapter** | **데이터 Setup & 검증 (Helper)** | `.createSeat()`, `.verifyOutbox()` | `Repository`, `JPA` |

- **Q: 왜 하나로 합치지 않는가?**
    - A: 역할이 명확히 다르다.
        - `HttpAdapter`는 사용자 관점(Black-box)이며, 내부 DB 구조를 몰라야 한다.
        - `TestAdapter`는 테스트 관점(White-box)이며, 효율적인 테스트를 위해 백도어가 필요하다.

### 3.2 테스트 안정성 확보

- **RestAssured 설정:**
    - Static 설정(`RestAssured.port = ...`)을 금지하고, **매 요청마다 Builder 패턴**으로 포트를 명시한다.
    - 이유: 병렬 테스트 및 멀티 모듈 환경에서의 Thread-Safety 보장.

- **Payment Mocking:**
    - Happy Path 검증 시 안정성을 위해 Mock 성공률을 **100%**로 고정한다.
    - (설정 파일 또는 코드 상수로 관리)
