# /test

테스트를 실행합니다.

---

## Usage

```
/test                         # 전체 테스트 실행
/test core                    # core-service 테스트
/test queue                   # queue-service 테스트
/test ReservationServiceTest  # 특정 테스트 클래스
/test --coverage              # 커버리지 리포트 포함
```

---

## Commands

### 전체 테스트
```bash
./gradlew test
```

### 모듈별 테스트
```bash
# Core Service
./gradlew :core-service:test

# Queue Service
./gradlew :queue-service:test
```

### 특정 테스트 실행
```bash
# 클래스 지정
./gradlew test --tests "ReservationServiceTest"

# 패턴 지정
./gradlew test --tests "*Integration*"

# 메서드 지정
./gradlew test --tests "ReservationServiceTest.reserve_success"
```

### 커버리지 리포트
```bash
./gradlew jacocoTestReport
# 결과: build/reports/jacoco/test/html/index.html
```

### 실패한 테스트만 재실행
```bash
./gradlew test --rerun-tasks
```

---

## Test Categories

| 카테고리 | 설명 | 명령어 |
|----------|------|--------|
| Unit | 단위 테스트 | `./gradlew test --tests "*Test"` |
| Integration | 통합 테스트 | `./gradlew test --tests "*IntegrationTest"` |
| Acceptance | 인수 테스트 | `./gradlew test --tests "*AcceptanceTest"` |

---

## Coverage Requirements

| 대상 | 최소 커버리지 |
|------|-------------|
| Domain | 90% |
| Application | 85% |
| Repository | 80% |
| Controller | 70% |
| **전체** | **80%** |

---

## Troubleshooting

### 테스트 실패 시
1. 로그 확인: `build/reports/tests/test/index.html`
2. 특정 테스트 디버그: `./gradlew test --tests "TestClass" --info`
3. 테스트 격리 확인: Testcontainers 상태

### Testcontainers 이슈
```bash
# Docker 상태 확인
docker ps

# 컨테이너 정리
docker system prune -f
```

### 느린 테스트
```bash
# 병렬 실행
./gradlew test --parallel

# 빌드 캐시 활용
./gradlew test --build-cache
```
