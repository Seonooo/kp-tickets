# /tdd

테스트 주도 개발(TDD) 워크플로우를 시작합니다.

---

## Usage

```
/tdd                          # TDD 가이드 시작
/tdd ReservationService       # 특정 클래스 TDD
/tdd "좌석 예약 기능"           # 기능 설명으로 TDD
```

---

## Process

### Step 1: 에이전트 로드

`.claude/agents/tdd-guide.md` 에이전트를 활성화합니다.

### Step 2: TDD 사이클 실행

```
┌─────────────────────────────────────────┐
│                                         │
│   ┌─────┐    ┌─────┐    ┌──────────┐   │
│   │ RED │ →  │GREEN│ →  │ REFACTOR │   │
│   └─────┘    └─────┘    └──────────┘   │
│      │                        │         │
│      └────────────────────────┘         │
│              (반복)                      │
└─────────────────────────────────────────┘
```

**RED**: 실패하는 테스트 작성
```java
@Test
@DisplayName("좌석 선점 성공 시 PENDING 상태의 예약 생성")
void reserve_success() {
    // Given
    Long seatId = 1L;
    Long userId = 100L;

    // When
    Reservation result = service.reserve(seatId, userId);

    // Then
    assertThat(result.getStatus()).isEqualTo(PENDING);
}
```

**GREEN**: 최소한의 구현
```java
public Reservation reserve(Long seatId, Long userId) {
    return Reservation.create(seatId, userId, PENDING);
}
```

**REFACTOR**: 코드 개선
```java
public Reservation reserve(Long seatId, Long userId) {
    validateSeatAvailable(seatId);
    Seat seat = findSeat(seatId);
    return Reservation.create(seat, userId);
}
```

### Step 3: 테스트 실행

```bash
# 전체 테스트
./gradlew test

# 특정 모듈
./gradlew :core-service:test

# 특정 클래스
./gradlew test --tests "ReservationServiceTest"
```

### Step 4: 커버리지 확인

```bash
./gradlew jacocoTestReport
```

| 대상 | 최소 커버리지 |
|------|-------------|
| Domain | 90% |
| Application | 85% |
| Repository | 80% |
| Controller | 70% |

---

## Test Types

### Unit Test
```java
@ExtendWith(MockitoExtension.class)
class ServiceTest {
    @Mock Repository repository;
    @InjectMocks Service service;
}
```

### Integration Test
```java
@SpringBootTest
@Testcontainers
class RepositoryTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");
}
```

### Acceptance Test (Cucumber)
```gherkin
Feature: 좌석 예약
  Scenario: 예약 성공
    Given 예약 가능한 좌석이 있다
    When 사용자가 좌석을 예약한다
    Then 예약이 생성된다
```

---

## Edge Cases Checklist

- [ ] Null / Empty 값
- [ ] 경계값 (0, MAX, 음수)
- [ ] 예외 경로
- [ ] 동시성 (여러 스레드)
- [ ] 타임아웃
