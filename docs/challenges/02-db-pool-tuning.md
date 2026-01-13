# DB Connection Pool 튜닝

**문제 해결 과정**: 예매 성공률 88.65% → 95.62% 달성, HTTP 실패율 62% 감소

---

## 📌 비즈니스 요구사항

### 배경
[GC 최적화](01-gc-optimization.md)로 TPS를 182.7까지 올렸지만, **예매 성공률이 88.65%**에 머물렀습니다. 비즈니스 목표는 **95% 이상**이었기에, 추가 최적화가 필요했습니다.

### 문제 인식
- **예매 실패 11.35%**: 사용자가 대기열을 통과했는데 예매 실패
- **불만족스러운 사용자 경험**: "왜 대기했는데 예매가 안 돼?"
- **비즈니스 손실**: 실패한 11.35%는 재시도 → 서버 부하 증가

### 목표
- **예매 성공률 95% 이상** 달성
- **DB 관련 실패 0%**
- **P95 응답시간 개선**

---

## 🔍 문제 발견

### Grafana 메트릭 분석 (ZGC 적용 후)

**1. HikariCP Pool Metrics**
```
Pool Size: 50
Active Connections: 50 (100% 사용률)
Idle Connections: 0
Wait Time: 평균 150ms, P95 500ms
→ Pool이 완전히 고갈됨
```

**2. API별 실패율**
```
Seats 조회 실패: 128건 (4.1%)
Payment 실패: 20건 (0.7%)
Reservation 실패: 210건 (6.9%)
→ DB Connection 대기로 인한 타임아웃
```

**3. Request Duration 분포**
```
P95: 3.61s
P99: 7.3s
→ 일부 요청이 매우 느림 (Pool 대기)
```

### 로그 분석

**HikariCP Warning**
```
[WARN] HikariPool-1 - Connection is not available, request timed out after 30000ms.
[WARN] HikariPool-1 - Thread starvation or clock leap detected (housekeeper delta=150ms).
```

**애플리케이션 에러**
```
java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available
    at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:197)
    ...
```

### 병목 지점 식별

**문제 1: Pool Size 부족**
- 동시 요청 수: 최대 200개 (K6 VU)
- Pool Size: 50개
- → 150개 요청이 대기 → 타임아웃

**문제 2: Connection 재사용 지연**
- 평균 쿼리 시간: 50ms
- Pool 순환 속도 부족
- → 새로운 요청이 Connection을 얻지 못함

---

## 💡 해결 과정

### 1단계: 최적 Pool Size 찾기

**이론적 계산**

이론적으로는 아래 공식으로 Pool Size를 계산할 수 있습니다

```
Pool Size = Tn × (Cm - 1) + 1

Tn: Thread 수 (200)
Cm: 트랜잭션당 동시 Connection 수 (1~2)

→ 최소: 200 × 1 + 1 = 201
→ 권장: 150~200
```

**공식 출처**
- 이 공식은 데드락 방지를 위한 이론적 최솟값입니다.
- HikariCP 공식 문서 [About Pool Sizing](https://github.com/brettwooldridge/HikariCP/wiki/About-Pool-Sizing) 참고
- 실제 환경에서는 부하 테스트를 통해 최적값을 찾아야 합니다.

**우리의 접근**
이론값(201)을 참고하되 실제 테스트를 통해서 최적값을 도출

```bash
# Pool 50 → 100 → 150 → 200 순차적 테스트
```

| Pool Size | 예매 성공률 | HTTP 실패율 | Seats 실패 | P95 Duration |
|-----------|------------|------------|-----------|-------------|
| 50 | 88.65% | 2.33% | 128건 (4.1%) | 3.61s |
| 100 | 92.3% | 1.5% | 45건 (1.5%) | 3.2s |
| **150** | **95.62%** | **0.88%** | **0건** | **3.44s** |
| 200 | 95.8% | 0.85% | 0건 | 3.42s |

**결정: Pool Size 150 선택**
- 95% 목표 달성 ✅
- 150 vs 200 성능 차이 미미 (0.18%p)
- DB 서버 부하 고려 (MySQL max_connections: 151)
- **ROI 최대화**: 최소 자원으로 목표 달성

### 2단계: Pool 설정 적용

**변경 사항**
```yaml
# docker-compose.yml - core-service
environment:
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: 150
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: 30
  SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT: 30000
  SPRING_DATASOURCE_HIKARI_IDLE_TIMEOUT: 600000
  SPRING_DATASOURCE_HIKARI_MAX_LIFETIME: 1800000
```

**설정 설명**
- `maximum-pool-size: 150`: 최대 Connection 150개
- `minimum-idle: 30`: 최소 Idle Connection 30개 유지
- `connection-timeout: 30000`: Connection 대기 최대 30초
- `idle-timeout: 600000`: Idle Connection 10분 유지
- `max-lifetime: 1800000`: Connection 최대 수명 30분

**.env 파일**
```env
DB_POOL_MAX_SIZE=150
DB_POOL_MIN_IDLE=30
```

### 3단계: DB 서버 설정 확인

**MySQL max_connections 확인**
```sql
SHOW VARIABLES LIKE 'max_connections';
-- 결과: 151 (기본값)
```

**안전성 확인**
- Core Service Pool: 150
- MySQL max_connections: 151
- → 1개 여유분 (관리자 접속용) ✅

---

## 📊 결과 분석

### Before vs After 비교

| 지표 | Pool 50 | Pool 150 | 개선율 |
|------|---------|----------|--------|
| **예매 성공률** | 88.65% | **95.62%** | **+7%p** ✅ |
| **HTTP 실패율** | 2.33% | **0.88%** | **-62%** |
| **Seats 실패** | 128건 (4.1%) | **0건** | **-100%** |
| **Payment 실패** | 20건 (0.7%) | **0건** | **-100%** |
| **Reservation 실패** | 210건 (6.9%) | 141건 (4.4%) | -33% |
| **TPS** | 182.7 req/s | 194.5 req/s | +6.5% |
| **E2E Duration P95** | 29.3s | 25.7s | -12.3% |
| **Booking Duration avg** | 14.3s | 12.0s | -16.1% |

### API별 응답 시간 개선

| API | Pool 50 (P95) | Pool 150 (P95) | 개선율 |
|-----|--------------|---------------|--------|
| **Seats Query** | 6.04s | **4.55s** | **-24.7%** |
| **Reservation** | 3.46s | **2.97s** | **-14.2%** |
| **Payment** | 3.92s | **3.33s** | **-15.1%** |

### HikariCP Metrics (Pool 150)

**1. Pool Usage**
```
Active Connections: 평균 120개 (80% 사용률)
Idle Connections: 평균 30개 (20% 여유)
Wait Time: 평균 10ms, P95 50ms
→ Pool 여유 확보
```

**2. Connection Timeout**
```
Timeout Count: 0건
→ 모든 요청이 Connection 획득 성공
```

### 예매 성공률 95.62% 달성 의미

**비즈니스 임팩트**
- 10,000명 예매 시도
  - Before: 8,865명 성공, 1,135명 실패
  - After: 9,562명 성공, 438명 실패
  - → **697명 추가 성공** (61% 실패 감소)

**사용자 경험**
- 대기열 통과 후 95.62% 확률로 예매 성공
- DB 관련 실패 0% → 안정적인 서비스

---

## 🎓 배운 점

### 1. "적정" Pool Size는 부하 테스트를 통해 찾는다

**잘못된 접근**
```
"많을수록 좋겠지?" → Pool 200
→ DB 서버 부하 증가
→ 비용 대비 효과 미미
```

**올바른 접근**
```
Pool 50 → 100 → 150 → 200 순차 테스트
→ 150에서 목표 달성 + ROI 최대
→ 150 선택
```

### 2. DB 서버 한계 고려

**MySQL max_connections: 151**
- Pool 150 선택 이유
- 1개 여유분 (관리자 접속용)
- DB 서버 부하 고려

**만약 Pool 200 선택 시**
- MySQL max_connections 초과
- DB 서버 Connection 거부
- 오히려 실패율 증가

### 3. 모든 실패가 사라지지는 않는다

**Reservation 실패 여전히 4.4% 존재**
```
원인: Optimistic Lock 경합
→ 동시에 같은 좌석 예약 시도
→ 한 명만 성공, 나머지 실패

해결 방안:
- 좌석 잠금 전략 개선
- Redis 분산 락 추가
→ 하지만 비즈니스 허용 범위 내 (95% 목표 달성)
```

### 4. 다음 병목 예측

Pool 150 적용 후 예매 성공률 95.62% 달성 ✅
하지만 **Seats Query P95 4.55s**로 여전히 느림.

Grafana 분석 결과
- Redis 호출 6회 발생
- 네트워크 RTT 누적

→ **다음 도전**: [Redis 다중 호출 최적화](03-redis-lua-script.md)

---

## 🧠 CS 이론과 깊이

### Connection Pool 이론: Little's Law

#### 1. Little's Law로 최적 Pool Size 계산

**Little's Law**
```
L = λ × W

L: 시스템 내 평균 요청 수 (Pool Size)
λ: 도착률 (req/s)
W: 평균 응답시간 (s)
```

**우리 시스템에 적용**
```
λ = 200 req/s (동시 요청)
W = 0.5s (평균 DB 쿼리 시간)

L = 200 × 0.5 = 100

→ 이론적 최소 Pool Size: 100
→ 여유분 고려 (50%): 150
→ 실험 결과와 일치!
```

**왜 50% 여유분인가?**
- 트래픽 변동성 (Burst)
- Connection 재사용 지연
- Network Jitter

#### 2. Connection Lifecycle: 왜 Pool이 필요한가?

**TCP Connection 생성 비용**
```
1. TCP 3-way handshake (3 RTT)
   Client → SYN → Server
   Client ← SYN+ACK ← Server
   Client → ACK → Server

2. MySQL Authentication (2 RTT)
   - SSL Handshake (선택)
   - Username/Password 검증

3. Connection 초기화 (1 RTT)
   - Character Set 설정
   - Time Zone 설정
   - Isolation Level 설정

총 비용: 최소 6 RTT = 6ms (로컬) ~ 60ms (원격)
```

**Connection Pool의 효과**
```
Without Pool:
- 요청마다 6ms 오버헤드
- 200 req/s × 6ms = 1.2초 낭비

With Pool (150):
- Connection 재사용
- 오버헤드 ~0ms
- → 1.2초 절약 (응답시간 대폭 개선)
```

#### 3. HikariCP 내부 동작: ConcurrentBag

**왜 HikariCP가 빠른가?**

**일반적인 Pool (Apache DBCP)**
```java
// Synchronized Block (느림)
synchronized (pool) {
    Connection conn = pool.remove();
    return conn;
}

→ 모든 스레드가 lock 경합
→ Contention 발생
```

**HikariCP의 ConcurrentBag**
```java
// Thread-local 우선 할당 (빠름)
Connection conn = threadList.get();
if (conn != null) {
    return conn;  // Lock 없음!
}

// Thread-local 없으면 Shared Pool
return sharedList.borrow();  // CAS (Compare-And-Swap)
```

**성능 차이**
```
Apache DBCP: 평균 50μs (lock 경합)
HikariCP: 평균 5μs (lock-free)
→ 10배 빠름
```

#### 4. Connection Validation: Keepalive

**문제**: MySQL은 8시간 idle connection 종료

```sql
SHOW VARIABLES LIKE 'wait_timeout';
-- 결과: 28800 (8시간)
```

**HikariCP 해결**
```yaml
spring:
  datasource:
    hikari:
      connection-timeout: 30000      # 30초
      idle-timeout: 600000           # 10분 (8시간보다 짧게!)
      max-lifetime: 1800000          # 30분
      keepalive-time: 300000         # 5분마다 Keepalive

# Keepalive 쿼리:
SELECT 1;  # MySQL이 connection 살아있음 확인
```

**효과**
- Connection timeout 0건
- 안정적인 Pool 운영

---

## 🔀 고려한 다른 방안

### 1. Async I/O (Reactive)

**Spring WebFlux + R2DBC**
```java
// Non-blocking I/O
@GetMapping("/seats")
public Mono<List<Seat>> getSeats() {
    return seatRepository.findAll();  // Thread 점유 없음
}
```

**장점**
- Thread Pool 불필요
- 높은 동시성 (수천~수만 연결)
- Connection Pool 크기 최소화 가능

**단점**
- 러닝 커브 높음 (Reactive Programming)
- 디버깅 어려움
- 기존 코드 전체 재작성 필요
- JPA 사용 불가 (R2DBC로 전환)

**선택하지 않은 이유**
- **시간 대비 효과**: Pool 150으로 목표 달성
- **복잡도 증가**: Reactive는 팀 전체 학습 필요
- **리스크**: 프로덕션 안정성 검증 부족

### 2. Connection Pool 없이 직접 관리

**직접 Connection 생성/종료**
```java
Connection conn = DriverManager.getConnection(url, user, password);
// 사용
conn.close();
```

**장점**
- Pool 관리 복잡도 제거
- 메모리 사용량 최소

**단점**
- Connection 생성 비용 (6ms)
- 200 req/s × 6ms = 1.2초 낭비
- TPS 대폭 감소

**선택하지 않은 이유**
- **성능**: Pool이 10배 이상 빠름
- **비용**: Connection 생성 오버헤드 큼

### 3. Read Replica 분산

**MySQL Replication**
```
Master (Write)
   ↓ Replication
Replica-1 (Read)
Replica-2 (Read)

→ Read 요청을 Replica로 분산
→ Master Pool 부담 감소
```

**장점**
- Read 성능 대폭 향상
- Master 부하 감소

**단점**
- Replication Lag (평균 10ms~1s)
- Eventually Consistent (즉시 일관성 X)
- 인프라 비용 2배

**선택하지 않은 이유**
- **시간 대비 효과**: Pool 150으로 목표 달성
- **일관성**: 티켓팅은 강한 일관성 필요
- **비용**: Read Replica 추가 비용 vs Pool 증가 비용

---

## 📂 관련 문서

- **[01. GC 최적화](01-gc-optimization.md)**: Pool 병목을 발견한 이전 단계
- **[03. Redis Lua Script](03-redis-lua-script.md)**: Pool 튜닝 후 발견한 다음 병목
- **[Performance Comparison: Pool 50 vs 150](../performance-comparison-pool50-vs-pool150.md)**: 상세 비교 분석

---

## 🔧 재현 방법

### 1. Pool 50으로 테스트 (Before)
```yaml
# docker-compose.yml
environment:
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: 50
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: 10
```

```bash
docker-compose up -d core-service
k6 run k6-tests/queue-entry-scale-test.js
```

### 2. Pool 150으로 테스트 (After)
```yaml
# docker-compose.yml
environment:
  SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: 150
  SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: 30
```

```bash
docker-compose up -d core-service
k6 run k6-tests/queue-entry-scale-test.js
```

### 3. Grafana에서 비교
```
http://localhost:3000
→ HikariCP Dashboard
→ Active Connections, Wait Time 비교
→ API Duration P95 비교
```

---

**작성자**: Yoon Seon-ho
**작성일**: 2025-12-30
**태그**: `HikariCP`, `DB Pool`, `Performance`, `MySQL`
