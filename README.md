# Concert Ticketing Service 🎫

**대규모 동시 접속 트래픽을 처리하는 고성능 티켓팅 시스템**
TPS 342% 개선 · 예매 성공률 95.62% 달성 · 30만 명 동시 처리

---

## 🎯 프로젝트 배경

콘서트 티켓팅 시장에서는 **순간적인 트래픽 폭주**가 발생합니다. 인기 아티스트의 티켓 오픈 시점에는 수십만 명이 동시에 접속하여, 일반적인 시스템으로는 **서버 다운**, **불공정한 선착순**, **낮은 예매 성공률** 문제가 발생합니다.

### 비즈니스 목표
- **대규모 트래픽 안정적 처리**: 30만 명 동시 접속 대응
- **선착순 공정성 보장**: 대기열 순서 보장, 먼저 온 사람이 먼저 예매
- **예매 성공률 95% 이상**: 사용자가 대기열을 통과했다면 높은 확률로 예매 성공
- **빠른 응답시간**: 사용자 이탈 방지 (P95 < 500ms)
- **시스템 안정성**: 실패율 최소화, 고가용성 확보

---

## 📊 핵심 성과

### 성능 개선 (Before → After)

| 지표 | Before | After | 개선율 |
|------|--------|-------|--------|
| **처리량 (TPS)** | 44 req/s | **194.5 req/s** | **+342%** |
| **예매 성공률** | 88.65% | **95.62%** | **+7%p** |
| **GC Pause Time** | 45ms | **< 1ms** | **사용자 체감 불가** |
| **HTTP 실패율** | 2.33% | **0.88%** | **-62%** |
| **대기열 진입 P95** | 419ms | **3.13ms** | **-99.3%** |
| **활성화 대기 P95** | - | **3.009초** | 목표(30초)의 10% |

### 대용량 트래픽 처리 검증
- ✅ **30만 명 동시 대기열 진입** 처리 완료
- ✅ **초당 2,000명** 지속적 진입 처리 (3분간)
- ✅ **대기열 순환율 85.6%** (Entry 8,509명 vs Exit 7,281명)
- ✅ **제거 성공률 100%** (29,391회 완료)

### 코드 최적화
- ✅ **Redis 호출 83% 감소** (6회 → 1회, Lua 스크립트 통합)
- ✅ **평균 응답시간 38.7% 단축** (네트워크 RTT 절감)

---

## 🔧 주요 기술적 도전과 해결 과정

### 1. [GC로 인한 처리량 저하 문제](docs/challenges/01-gc-optimization.md)
> **문제**: G1 GC의 Stop-The-World로 TPS 44 req/s에 머무름
> **해결**: ZGC 적용으로 GC Pause 사용자 체감 불가 수준으로 감소 (< 1ms)
> **결과**: **TPS 315% 개선** (44 → 182.7 req/s), **GC Time 99.7% 감소**

### 2. [DB Connection Pool 병목 문제](docs/challenges/02-db-pool-tuning.md)
> **문제**: HikariCP Pool 50 사용 시 100% 사용률로 병목
> **해결**: Pool Size 3배 증가 (50 → 150)
> **결과**: **예매 성공률 95.62% 달성**, **Seats/Payment 실패율 0%**

### 3. [Redis 다중 호출로 인한 네트워크 RTT 문제](docs/challenges/03-redis-lua-script.md)
> **문제**: 대기열 진입 시 Redis 6회 호출로 네트워크 RTT 발생
> **해결**: Lua Script로 6회 호출을 1회로 통합
> **결과**: **Redis 호출 83% 감소**, **평균 응답시간 38.7% 단축**

### 4. [Redis 단일 인스턴스 한계 돌파](docs/challenges/04-redis-cluster.md)
> **문제**: Redis 단일 인스턴스 사용 시 TPS 4,400 한계 및 SPOF 위험
> **해결**: Redis Cluster (3 Master + 3 Replica) 구성
> **결과**: **고가용성 확보**, **P95 36.4% 개선**

### 5. [Active Queue 순환 안정성 검증](docs/challenges/05-queue-circulation.md)
> **문제**: 대기열 진입 성능만 측정, 전체 라이프사이클 미검증
> **해결**: 진입 → 폴링 → 사용 → 제거 전체 플로우 테스트
> **결과**: **순환율 85.6%**, **제거 성공률 100%**, **폴링 타임아웃 0.003%**

---

## 🏗️ 아키텍처 및 기술 스택

### 시스템 아키텍처

```
사용자 (300,000명)
    ↓
Queue Service (대기열 관리)
    ├─ Redis Cluster (3 Master + 3 Replica)
    │   ├─ Lua Script (원자적 연산)
    │   └─ Hash Tag 전략 (동일 콘서트 동일 Shard)
    └─ Scheduler (Wait → Active 자동 전환)
    ↓
Core Service (예매/결제)
    ├─ HikariCP (Pool 150)
    ├─ MySQL (Optimistic Lock)
    └─ Kafka (Outbox Pattern)
```

### 기술 스택

| 영역 | 기술 | 선택 이유 |
|------|------|----------|
| **Language** | Java 21 | Virtual Threads로 경량 동시성, Record로 간결한 DTO |
| **Framework** | Spring Boot 3.4 | 최신 생태계, Observability 지원 |
| **Cache** | Redis 7.2 Cluster | Lua Script 지원, 고가용성 |
| **DB** | MySQL 8.0 | ACID 보장, 트랜잭션 격리 수준 제어 |
| **Message Queue** | Kafka | Outbox Pattern, 이벤트 소싱 |
| **GC** | ZGC | Zero Pause, 대용량 힙에서도 일정한 성능 |
| **Testing** | Cucumber, K6 | BDD 인수 테스트, 부하 테스트 |
| **Architecture** | Hexagonal, Modular Monolith | MSA 전환 가능, 의존성 격리 |

### 핵심 설계 원칙

**1. Fail-Fast Booking**
- Redis 분산 락으로 초고속 좌석 선점
- 락 획득 실패 시 즉시 응답하여 사용자 대기 시간 최소화

**2. Hybrid Queue**
- **Waiting Queue** (ZSET): 대기 순서 관리, 공정성 보장
- **Active Queue** (Hash): 예매 권한 부여, 빠른 조회

**3. Outbox Pattern**
- 예약 완료 → Kafka 이벤트 발행 (트랜잭션 내)
- 데이터 정합성 보장, At-Least-Once 전달 보장

---

## 📈 성능 테스트 결과

### Phase별 개선 과정

| Phase | 최적화 내용 | TPS | P95 | 예매 성공률 |
|-------|------------|-----|-----|------------|
| **Baseline** | G1 GC + Pool 50 | 44 | 419ms | ~80% |
| **Phase 1** | ZGC 적용 | 182.7 | 292ms | 88.65% |
| **Phase 2** | Pool 150 | 194.5 | 130.73ms | **95.62%** ✅ |
| **Phase 3** | Lua Script | 194.5 | 3.13ms | 95.62% |
| **Phase 4** | Queue 순환 검증 | - | - | - |

### 상세 메트릭

**HTTP 요청 처리**
- Total Requests: 15,973건 (3분)
- Request Rate: 194.5 req/s
- Failed Requests: 141건 (0.88%)
- P95 Duration: 3.44s

**Queue Service API**
- Queue Entry P95: **3.13ms** (목표 200ms의 1.6%)
- Queue Poll P95: **3.47ms** (목표 100ms의 3.5%)
- Queue Remove P95: **3.70ms** (목표 100ms의 3.7%)

**E2E 사용자 경험**
- E2E P95: 25.68s (-12.3%)
- Booking P95: 23.06s (-16.9%)

---

## 📚 Documentation

### 문제 해결 과정 (상세)
- **[01. GC 최적화](docs/challenges/01-gc-optimization.md)**: G1 GC → ZGC 전환 과정 및 시행착오
- **[02. DB Pool 튜닝](docs/challenges/02-db-pool-tuning.md)**: Pool Size 최적값 찾기 실험
- **[03. Redis Lua Script](docs/challenges/03-redis-lua-script.md)**: Redis 호출 83% 감소 과정
- **[04. Redis Cluster](docs/challenges/04-redis-cluster.md)**: 고가용성 확보 및 Hash Tag 전략
- **[05. Queue 순환 검증](docs/challenges/05-queue-circulation.md)**: 전체 라이프사이클 테스트

### 시스템 설계
- **[Architecture](docs/architecture.md)**: 시스템 아키텍처 및 설계 원칙
- **[ER Diagram](docs/erd.md)**: DB 및 Redis 스키마
- **[Business Logic](docs/biz-logic.md)**: 도메인 비즈니스 규칙
- **[Tech Stack](docs/tech-stack.md)**: 기술 스택 상세

### 성능 테스트
- **[Performance Test Summary](docs/PERFORMANCE_TEST_SUMMARY.md)**: 성능 테스트 전체 과정
- **[Optimization Summary](docs/optimization-summary-2025-12-30.md)**: 최종 최적화 요약

---

## 🚀 Quick Start

### Prerequisites
- Java 21 (JDK)
- Docker & Docker Compose

### Setup & Run
```bash
# 1. Infrastructure (Redis, MySQL, Kafka)
docker-compose up -d

# 2. Run Services
./gradlew :core-service:bootRun
./gradlew :queue-service:bootRun
```

### Testing
```bash
# 전체 테스트 (Unit + Integration + Acceptance)
./gradlew test

# 성능 테스트 (K6)
k6 run k6-tests/queue-circulation-test.js
```

---

## 🎓 배운 점

### 1. 성능 최적화는 데이터 측정(모니터링)을 근거로 한다.
- Grafana로 병목 지점 실시간 모니터링 (GC, DB Pool, Redis)
- K6로 다양한 시나리오 부하 테스트
- **추측이 아닌 데이터 기반 의사결정**

### 2. 트레이드오프를 고려하여 우리의 비즈니스에는 어떤 것이 어울리는가 생각한다.
- ZGC: 메모리 증가 vs 처리량 3배 → **처리량 선택**
- Lua Script: 복잡도 증가 vs 성능 30% 개선 → **성능 선택**
- Redis Cluster: 운영 복잡도 vs 고가용성 → **안정성 선택**

### 3. 비즈니스 문제를 코드로 해결한다.
- "예매 성공률 95%" → DB Pool 튜닝, 실패율 모니터링
- "공정한 선착순" → ZSET 순서 보장, Lua Script 원자성
- "빠른 응답" → Redis 호출 최소화, ZGC로 Pause 제거

### 4. 전체 시스템의 구성을 생각해야한다.
- Queue Service만 최적화해도 Core Service가 병목이면 의미 없음
- 단계별 검증 (Queue 순환 → Core 최적화 → E2E 자동화)
- **부분 최적화가 아닌 End-to-End 최적화**

---

## 📝 기술 블로그

프로젝트 개발 과정에서 작성한 기술 블로그 글입니다.

1. **[티켓팅 동시성 시나리오에서 고민한 과정들](https://velog.io/@seonho/티켓팅-동시성-시나리오에서-고민한-과정들)**
   - 대규모 동시 접속 환경에서의 동시성 제어 전략

2. **[JWT에서 Redis Cluster로 변경한 이유](https://velog.io/@seonho/JWT에서-Redis-Cluster로-변경한-이유)**
   - 인증 방식 선택과 고가용성 확보 과정

3. **[Troubleshooting: 동시 접속 30만 CCU 속에서 살아남기](https://velog.io/@seonho/Troubleshooting-동시-접속-30만CCU-속에서-살아남기)**
   - 30만 명 동시 접속 부하 테스트 및 트러블슈팅

4. **[대규모 좌석 조회 API 성능 최적화: 5.23s → 829ms](https://velog.io/@seonho/대규모-좌석-조회-API-성능-최적화-5.23s에서-829ms까지)**
   - 좌석 조회 API 성능 개선 (84% 단축)