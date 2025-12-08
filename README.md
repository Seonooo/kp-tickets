# Concert Ticketing Service 🎫

대규모 트래픽을 처리하는 콘서트 대기열 및 예매/결제 시스템 (MSA 기반)

## 📌 Project Overview

본 프로젝트는 고성능/고가용성을 목표로 하는 티켓팅 시스템입니다. **접속 대기열(Queue)**, **좌석 예약(Booking)**, **결제(Payment)** 도메인으로 구성되어 있으며, 트래픽 폭주 상황에서도 안정적인 서비스를 제공하는 것을 목표로 합니다.

- **Architecture:** Hexagonal Architecture, Modular Monolith (MSA Ready)
- **Tech Stack:** Java 21, Spring Boot 3.4, Redis (Lua Script), Kafka, MySQL
- **Key Features:**
  - **Hybrid Queue:** 은행 창구 식(Waiting) + 놀이공원 식(Active) 대기열 혼합
  - **Fail-Fast Booking:** Redis 분산 락을 이용한 초고속 좌석 선점
  - **Reliability:** Outbox Pattern을 통한 데이터 정합성 보장

---

## 🚀 Getting Started

### Prerequisites

- **Java 21 (JDK)**
- **Docker & Docker Compose**

### 💻 Development Setup

#### 1. Infrastructure Setup (Docker)
먼저 필요한 인프라(MySQL, Redis, Kafka)를 실행해야 합니다.

**Option A: Agent Workflow (Recommended)**
에이전트에게 `/setup_env` 명령을 내리거나, `.agent/workflows/setup_env.md`를 참고하여 실행합니다.

**Option B: Manual**
```bash
docker-compose up -d
```

#### 2. Application Execution
```bash
# Core Service (Booking/Payment)
./gradlew :core-service:bootRun

# Queue Service
./gradlew :queue-service:bootRun
```

### 🛠 Manual Setup (Infrastructure Only)

애플리케이션 실행 없이 인프라만 띄우고 싶다면:

```bash
docker-compose up -d
```
- **MySQL:** localhost:3306
- **Redis:** localhost:6379
- **Kafka:** localhost:9092
- **Zookeeper:** localhost:2181

---

## ✅ Testing

### 1. Acceptance Tests (인수 테스트)
**Cucumber**와 **RestAssured**를 활용한 사용자 시나리오 기반의 블랙박스(Black-box) 테스트입니다.

- **Key Features:**
  - **BDD Style:** 비즈니스 언어(Gherkin)로 작성된 시나리오 검증
  - **Concurrency:** Java 21 **Virtual Threads**를 활용한 대규모 동시성 시나리오 포함
  - **Isolation:** **Testcontainers** (Redis, MySQL)를 활용한 완벽한 격리 환경

```bash
# 전체 테스트 실행 (Unit + Integration + Acceptance)
./gradlew test

# Queue Service 인수 테스트 (대기열 진입, 토큰 발급/만료)
./gradlew :queue-service:test --tests "*CucumberTest*"

# Core Service 인수 테스트 (좌석 예약, 동시성 제어, 결제)
./gradlew :core-service:test --tests "*CucumberTest*"
```

### 2. Manual Test (API)
`docs/http-client` 폴더의 `.http` 파일을 사용하여 수동 테스트를 진행할 수 있습니다. (IntelliJ HTTP Client 권장)

---

## 📚 Documentation

상세 문서는 `docs/` 디렉토리에 있습니다.

- **[Architecture](docs/architecture.md)**: 시스템 아키텍처 및 설계 원칙
- **[ER Diagram](docs/erd.md)**: DB 및 Redis 스키마
- **[Business Logic](docs/biz-logic.md)**: 도메인 비즈니스 규칙
- **[Convention](docs/convention.md)**: 코딩 컨벤션 및 테스트 전략
- **[Tech Stack](docs/tech-stack.md)**: 기술 스택 및 라이브러리 상세

---

## 🤝 Contribution

1. Create Feature Branch (`feat/ticket`)
2. Commit Changes (Atomic Commit)
3. Submit Pull Request (Review Required)
