# E2E Tests - Concert Booking System

콘서트 예매 시스템의 End-to-End 기능 테스트 모듈입니다.

## 목적

- **기능 정확성 검증**: BDD 시나리오를 통한 비즈니스 로직 검증
- **통합 테스트**: Queue Service + Core Service + Kafka 통합 검증
- **회귀 테스트 방지**: CI/CD 파이프라인에서 자동 실행
- **병목 식별**: 소규모 부하 테스트를 통한 성능 병목 지점 파악

## 기술 스택

- **BDD Framework**: Cucumber 7.18.0
- **Test Runner**: JUnit 5 Platform
- **API Client**: REST Assured 5.4.0
- **Assertions**: AssertJ 3.25.3
- **비동기 검증**: Awaitility 4.2.0

## 프로젝트 구조

```
e2e-tests/
├── src/test/
│   ├── java/personal/ai/e2e/
│   │   ├── client/                  # API 클라이언트
│   │   │   ├── QueueServiceClient.java
│   │   │   └── CoreServiceClient.java
│   │   ├── context/                 # 테스트 컨텍스트
│   │   │   └── TestContext.java
│   │   ├── steps/                   # Cucumber Step Definitions
│   │   │   └── BookingFlowSteps.java
│   │   └── CucumberTestRunner.java  # 테스트 실행기
│   └── resources/
│       └── features/                # BDD 시나리오
│           └── booking-flow.feature
└── pom.xml
```

## 테스트 시나리오

### 1. 정상적인 예매 플로우
```gherkin
대기열 진입 → 활성화 대기 → 좌석 조회 → 예약 → 결제 → Queue 자동 제거
```

### 2. 실패 시나리오
- 대기열 없이 예매 시도
- 만료된 Queue 토큰 사용
- 동일 좌석 동시 예약 (동시성 테스트)

### 3. 병목 테스트
- 10명 동시 예매 시 단계별 응답 시간 측정
- 병목 구간 식별

## 실행 방법

### 1. 사전 준비

서비스가 실행 중이어야 합니다:

```bash
# Docker 환경 시작
docker-compose -f docker-compose.cluster.yml up -d

# 서비스 상태 확인
docker-compose -f docker-compose.cluster.yml ps
```

### 2. E2E 테스트 실행

```bash
cd e2e-tests

# Maven으로 실행
mvn clean test

# 특정 태그만 실행 (예: @병목테스트)
mvn clean test -Dcucumber.filter.tags="@병목테스트"
```

### 3. 환경 변수 설정 (선택사항)

```bash
# Queue Service URL 변경
export QUEUE_URL=http://localhost:8081

# Core Service URL 변경
export CORE_URL=http://localhost:8080

# 테스트 실행
mvn clean test
```

## 테스트 결과 확인

### 1. 콘솔 출력
```
✓ Core Service is healthy
✓ Queue Service is healthy
✓ Queue entry successful
✓ Received queue position: 0
✓ Queue status is READY/ACTIVE
...
```

### 2. HTML 리포트
```
target/cucumber-reports/cucumber.html
```

브라우저에서 열어 시각적으로 확인 가능

### 3. JSON 리포트
```
target/cucumber-reports/cucumber.json
```

CI/CD 도구와 통합 가능

## K6 부하 테스트와의 연계

### E2E 테스트 통과 후 K6 실행

1. **E2E 테스트로 기능 검증**
   ```bash
   cd e2e-tests
   mvn clean test
   # ✓ 모든 시나리오 통과 확인
   ```

2. **K6로 병목 프로파일링** (100명 규모)
   ```bash
   cd ../k6-tests
   docker-compose -f ../docker-compose.cluster.yml run --rm k6 run /scripts/bottleneck-profiling-test.js
   ```

3. **병목 개선 후 재검증**
   - E2E 테스트 재실행 (기능 정확성 유지 확인)
   - K6 테스트 재실행 (성능 개선 확인)

## 문제 해결

### 테스트 실패 시

1. **서비스 상태 확인**
   ```bash
   curl http://localhost:8081/actuator/health
   curl http://localhost:8080/actuator/health
   ```

2. **로그 확인**
   ```bash
   docker logs concert-queue-service
   docker logs concert-core-service
   ```

3. **테스트 데이터 확인**
   - DB에 concert, schedule, seats 데이터가 존재하는지 확인

### 타임아웃 발생 시

`booking-flow.feature`의 대기 시간 조정:
```gherkin
Then 최대 5분 내에 "READY" 상태가 된다  # 2분 → 5분
```

## 다음 단계

1. ✅ E2E 테스트 모듈 생성 완료
2. ⏭️ K6 병목 프로파일링 테스트 작성 (100명 규모)
3. ⏭️ 병목 지점 분석 및 개선
4. ⏭️ CI/CD 파이프라인 통합

## 참고 자료

- [Cucumber Documentation](https://cucumber.io/docs/cucumber/)
- [REST Assured Documentation](https://rest-assured.io/)
- [AssertJ Documentation](https://assertj.github.io/doc/)
