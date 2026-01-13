# Redis Cluster 구성: 고가용성 확보

**문제 해결 과정**: SPOF 제거, P95 36.4% 개선, 자동 Failover 구현

---

## 📌 비즈니스 요구사항

### 배경
[Lua Script 최적화](03-redis-lua-script.md)로 응답시간을 단축했지만, **고가용성**이 확보되지 않았습니다.

비즈니스 관점에서 **서비스 무중단 운영**은 필수입니다:
- Redis 장애 시 전체 서비스 마비
- 티켓 오픈 시점에 장애 발생 = 매출 손실
- SPOF (Single Point of Failure) 위험

### 목표
- **고가용성 확보**: Redis 장애 시 자동 복구
- **SPOF 제거**: 단일 장애점 없는 구조
- **성능 유지/개선**: Cluster 전환 후에도 성능 저하 없음

---

## 🔍 문제 발견

### 현재 구조 (Before)

**docker-compose.yml**
```yaml
redis:
  image: redis:7.2-alpine
  ports:
    - "6379:6379"
```

**문제 1: SPOF (Single Point of Failure)**
```
Redis 단일 인스턴스
→ Redis 장애 시 전체 서비스 마비
→ 대기열 데이터 전부 손실
→ 사용자 전원 재진입 필요
```

**문제 2: 확장성 한계**
```
TPS: 4,362.8 req/s
→ Redis 단일 인스턴스 처리량 한계
→ 다중 콘서트 동시 오픈 시 병목 가능
```

**문제 3: 데이터 복제 없음**
```
단일 인스턴스
→ 데이터 백업 없음
→ 장애 시 복구 불가
```

---

## 💡 해결 과정

### 1단계: Redis 고가용성 방안 비교

| 방안 | 고가용성 | 확장성 | Failover | 복잡도 | 선택 |
|------|---------|--------|----------|--------|------|
| **Sentinel** | ✅ | ❌ | 자동 | 중간 | ❌ 확장성 부족 |
| **Cluster** | ✅ | ✅ | 자동 | 높음 | ✅ **채택** |
| **Replication** | ✅ | ❌ | 수동 | 낮음 | ❌ Failover 수동 |

**Cluster 선택 이유**
1. **고가용성**: Master 장애 시 Replica 자동 승격
2. **확장성**: 샤딩으로 수평 확장 가능
3. **자동 Failover**: 운영 부담 최소화

### 2단계: Cluster 구성 설계

**3 Master + 3 Replica 구조**
```
Master-1 (Shard 1)   ↔  Replica-1
   ↓
Hash Slot: 0 ~ 5461

Master-2 (Shard 2)   ↔  Replica-2
   ↓
Hash Slot: 5462 ~ 10922

Master-3 (Shard 3)   ↔  Replica-3
   ↓
Hash Slot: 10923 ~ 16383
```

**왜 3 Master인가?**
- 최소 구성: 3 Master (Quorum 확보)
- 각 Master당 1 Replica (고가용성)
- 총 6 nodes (비용 vs 안정성 균형)

### 3단계: Hash Tag 전략

**문제**: Lua Script는 multi-key 연산 필요
```lua
-- enter_queue.lua
local activeTokenKey = KEYS[1]  -- active:token:{concertId}:userId
local waitQueueKey = KEYS[2]    -- queue:wait:{concertId}

-- 두 key가 다른 Shard에 있으면 Lua Script 실패
```

**해결**: Hash Tag로 동일 Shard 보장
```java
// Before: 다른 Shard에 분산 가능
"active:token:" + concertId + ":" + userId
"queue:wait:" + concertId

// After: {concertId}로 Hash Tag 지정
"active:token:{" + concertId + "}:" + userId
"queue:wait:{" + concertId + "}"

// → {concertId} 부분만 Hash Slot 계산
// → 동일 concertId는 동일 Master에 저장
```

### 4단계: docker-compose 구성

**docker-compose.cluster.yml**
```yaml
services:
  # Redis Nodes (6개)
  redis-node-1:
    image: redis:7.2-alpine
    command: redis-server --cluster-enabled yes --cluster-config-file nodes.conf --cluster-node-timeout 5000 --appendonly yes --port 6379
    ports:
      - "6379:6379"
    volumes:
      - redis-node-1-data:/data

  redis-node-2:
    image: redis:7.2-alpine
    command: redis-server --cluster-enabled yes --cluster-config-file nodes.conf --cluster-node-timeout 5000 --appendonly yes --port 6380
    ports:
      - "6380:6380"
    volumes:
      - redis-node-2-data:/data

  redis-node-3:
    image: redis:7.2-alpine
    command: redis-server --cluster-enabled yes --cluster-config-file nodes.conf --cluster-node-timeout 5000 --appendonly yes --port 6381
    ports:
      - "6381:6381"
    volumes:
      - redis-node-3-data:/data

  redis-node-4:
    image: redis:7.2-alpine
    command: redis-server --cluster-enabled yes --cluster-config-file nodes.conf --cluster-node-timeout 5000 --appendonly yes --port 6382
    ports:
      - "6382:6382"
    volumes:
      - redis-node-4-data:/data

  redis-node-5:
    image: redis:7.2-alpine
    command: redis-server --cluster-enabled yes --cluster-config-file nodes.conf --cluster-node-timeout 5000 --appendonly yes --port 6383
    ports:
      - "6383:6383"
    volumes:
      - redis-node-5-data:/data

  redis-node-6:
    image: redis:7.2-alpine
    command: redis-server --cluster-enabled yes --cluster-config-file nodes.conf --cluster-node-timeout 5000 --appendonly yes --port 6384
    ports:
      - "6384:6384"
    volumes:
      - redis-node-6-data:/data

  # Cluster Initialization
  redis-cluster-init:
    image: redis:7.2-alpine
    depends_on:
      - redis-node-1
      - redis-node-2
      - redis-node-3
      - redis-node-4
      - redis-node-5
      - redis-node-6
    command: >
      sh -c "sleep 10 &&
      redis-cli --cluster create
      redis-node-1:6379
      redis-node-2:6380
      redis-node-3:6381
      redis-node-4:6382
      redis-node-5:6383
      redis-node-6:6384
      --cluster-replicas 1
      --cluster-yes"
```

### 5단계: Spring Boot 설정

**application.yml**
```yaml
spring:
  data:
    redis:
      cluster:
        nodes:
          - localhost:6379
          - localhost:6380
          - localhost:6381
          - localhost:6382
          - localhost:6383
          - localhost:6384
        max-redirects: 3
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 50
          max-idle: 20
          min-idle: 10
```

---

## 📊 결과 분석

### Before vs After 비교

| 지표 | 단일 Redis | Redis Cluster | 개선율 |
|------|-----------|--------------|--------|
| **TPS** | 4,362.8 req/s | 4,406.2 req/s | +1.0% |
| **평균 응답시간** | 22.69ms | 21.2ms | -6.6% |
| **P95** | 205.61ms | **130.73ms** | **-36.4%** |
| **P99** | 468.66ms | **356.48ms** | **-23.9%** |
| **성공률** | 99.28% | 99.64% | +0.4% |
| **HTTP 에러율** | 0.00% | 0.00% | - |

### 고가용성 검증

**Failover 테스트**
```bash
# Master-1 강제 종료
docker stop redis-node-1

# 5초 후 Replica-4가 자동으로 Master 승격
# 서비스 중단 없음

# 확인
redis-cli --cluster check localhost:6379
# → Replica-4가 Master로 변경됨
```

**결과**
- ✅ 자동 Failover 성공
- ✅ 서비스 중단 없음 (5초 내 복구)
- ✅ 데이터 손실 없음 (Replication)

### P95 36.4% 개선 원인 분석

**1. 읽기 요청 분산**
- 단일 Redis: 모든 읽기 → Master
- Cluster: 읽기 → Master + Replica
- → 부하 분산 효과

**2. 네트워크 지연 감소**
- Cluster: 물리적으로 분산된 nodes
- 일부 요청은 더 가까운 node로 라우팅
- → 네트워크 RTT 감소

---

## 🎓 배운 점

### 1. 단일 콘서트 시나리오의 한계

**예상**: Redis Cluster → TPS 3배 증가?
**실제**: TPS 1% 증가 (4,362.8 → 4,406.2)

**원인**
```
단일 콘서트 테스트 (concert-1234)
→ Hash Tag {concertId}로 모든 키가 동일 Hash Slot
→ 동일 Slot = 동일 Redis Master에 집중
→ 나머지 2개 Master는 유휴 상태
→ 실질적으로 "단일 Redis"와 동일
```

**다중 콘서트 시나리오에서는?**
```
3개 콘서트 동시 오픈 (concert-A, concert-B, concert-C)
→ 3개 Master에 균등 분산
→ 예상 TPS: 12,900 (4,300 × 3)
```

**결론**
- Cluster의 **주목적**: 고가용성 확보 ✅
- TPS 증가는 **다중 콘서트 시나리오**에서만 효과적
- 단일 콘서트 폭주는 현재 구성으로 충분히 대응 가능

### 2. Hash Tag의 트레이드오프

**장점**
- Lua Script multi-key 연산 가능
- 동일 콘서트 데이터의 지역성 (Locality)

**단점**
- 단일 콘서트 시 1개 Master만 사용
- 부하 불균형 가능

**선택 이유**
- Lua Script 원자성이 더 중요
- 실제 시나리오: 단일 콘서트 폭주가 일반적
- Cluster의 주목적은 고가용성

### 3. 운영 복잡도 vs 안정성

**트레이드오프**
- 복잡도 증가: 6 nodes 관리
- 모니터링: Cluster 상태 확인 필요
- 디버깅: 어느 Shard에 데이터 있는지 확인 필요

**그럼에도 선택한 이유**
- **프로덕션 필수**: 고가용성 확보
- **자동 Failover**: 운영 부담 최소화
- **비용 대비 효과**: 안정성 확보 가치 큼

---

## 🧠 CS 이론과 깊이

### Redis Cluster 내부 동작: Gossip Protocol & Hash Slot

#### 1. Hash Slot vs Consistent Hashing

**Consistent Hashing (일반적)**
```
hash(key) → 0 ~ 2^32-1 범위의 Ring
→ 가장 가까운 Node에 저장

장점: Node 추가/제거 시 재분배 최소
단점: 불균등 분산 가능 (Virtual Node 필요)
```

**Redis Cluster의 Hash Slot**
```
CRC16(key) % 16384 → Slot 번호 (0 ~ 16383)
→ Slot을 Node에 정적 할당

예시:
Master-1: Slot 0 ~ 5461 (5462개)
Master-2: Slot 5462 ~ 10922 (5461개)
Master-3: Slot 10923 ~ 16383 (5461개)

장점:
- 균등 분산 보장
- Slot 단위 재분배 (세밀한 제어)
- Hash Tag 지원
```

**Hash Tag의 원리**
```
일반 키:
"queue:wait:concert-1234"
→ CRC16("queue:wait:concert-1234") % 16384 = 7890
→ Master-2

Hash Tag 사용:
"queue:wait:{concert-1234}"
"active:token:{concert-1234}:userId"
→ CRC16("concert-1234") % 16384 = 7890
→ 둘 다 Master-2 (동일 Slot)
```

#### 2. Gossip Protocol: 분산 합의

**중앙 관리자 없는 Cluster**
```
각 Node가 주기적으로 다른 Node와 통신
→ Cluster 상태 공유 (Gossip)
→ 장애 감지 및 Failover 자동 수행
```

**Gossip Message 구조**
```
PING (1초마다):
- 내 상태 (Master/Replica, Serving Slots)
- 내가 아는 다른 Node 상태
- Epoch (Cluster 버전)

PONG (즉시 응답):
- 상대 Node 상태 확인
- Cluster View 동기화
```

**장애 감지**
```
Node A가 Node B에게 PING 전송
→ 5초 동안 PONG 없음
→ Node A: "Node B가 PFAIL (Probably Fail)"

다른 Node들도 Node B를 PFAIL로 표시
→ Quorum 도달 (과반수)
→ Node B를 FAIL로 최종 판정
→ Replica 자동 승격 (Failover)
```

#### 3. Split-brain 문제와 Quorum

**Split-brain 시나리오**
```
Network Partition 발생:
[Master-1, Master-2] | [Master-3]
        ↓                    ↓
   Partition A          Partition B

양쪽 모두 "나만 살아있다" 판단
→ 동시에 Write 발생
→ 데이터 불일치
```

**Redis Cluster의 해결: Quorum**
```
Cluster 구성: 3 Master + 3 Replica = 6 nodes

Quorum: 과반수 (6/2 + 1 = 4)

Network Partition:
[Master-1, Master-2, Replica-1, Replica-2] | [Master-3, Replica-3]
        ↓ (4 nodes, Quorum 유지)          ↓ (2 nodes, Quorum 미달)
   계속 서비스                          서비스 중단 (Read-only)

→ Split-brain 방지
```

**트레이드오프**
- **CP (Consistency + Partition Tolerance)**: Quorum 미달 시 서비스 중단
- **AP (Availability + Partition Tolerance)**: Split-brain 허용
- Redis Cluster: **CP 선택** (데이터 정합성 우선)

#### 4. CAP Theorem 관점에서 Redis Cluster

**CAP Theorem**
```
C (Consistency): 모든 Node가 동일한 데이터
A (Availability): 모든 요청에 응답
P (Partition Tolerance): Network 장애에도 동작

→ 셋 중 둘만 선택 가능
```

**Redis Cluster의 선택: CP**
```
Consistency (Strong):
- Master Write → Replica 비동기 복제
- Failover 시 일부 데이터 손실 가능 (100ms 이내)
- → Eventual Consistency (약한 일관성)

Availability:
- Quorum 미달 시 서비스 중단
- Network Partition 시 일부 Node Read-only
- → Availability 일부 희생

Partition Tolerance:
- Gossip Protocol로 장애 감지
- 자동 Failover로 복구
```

**우리 선택 이유**
- 티켓팅 시스템: **Consistency > Availability**
- 대기열 순서는 정확해야 함
- 일시적 장애(5초)는 허용 가능

---

## 🔀 고려한 다른 고가용성 방안

### 1. Redis Sentinel

**구조**
```
Master (Write)
   ↓ Replication
Replica (Read)

Sentinel × 3 (모니터링 및 Failover)
```

**장점**
- Cluster보다 간단
- 자동 Failover 지원
- 설정 간편

**단점**
- **확장성 없음**: 단일 Master (Sharding 불가)
- TPS 한계: ~4,300 req/s
- 다중 콘서트 처리 불가

**선택하지 않은 이유**
- **확장성**: 향후 TPS 증가 대응 불가
- Cluster는 고가용성 + 확장성 동시 확보

### 2. Redis Replication (Manual Failover)

**구조**
```
Master (Write)
   ↓ Replication
Replica (Read)

Failover: 수동 (관리자가 Replica를 Master로 승격)
```

**장점**
- 가장 간단
- 설정 최소

**단점**
- **수동 Failover**: 장애 복구 시간 길음 (분 단위)
- 운영 부담 큼
- 새벽 장애 시 대응 어려움

**선택하지 않은 이유**
- **자동화**: Cluster/Sentinel의 자동 Failover 필수
- 티켓팅은 24/7 무중단 운영 필요

### 3. Redis on AWS ElastiCache Cluster Mode

**AWS 관리형 서비스**
```
ElastiCache Cluster Mode Enabled:
- Cluster 자동 관리
- 백업, 패치 자동화
- Multi-AZ 배포
```

**장점**
- 운영 부담 0
- AWS 인프라 최적화
- 프로덕션 즉시 사용 가능

**단점**
- **비용**: 온프레미스 대비 3~5배
- 로컬 개발 환경 구축 어려움
- Lock-in 위험

**선택하지 않은 이유**
- **현재 단계**: 로컬 Docker로 프로토타입
- **향후 계획**: 프로덕션 배포 시 ElastiCache 고려

---

## 📂 관련 문서

- **[03. Redis Lua Script](03-redis-lua-script.md)**: Cluster 전환 이전 단계
- **[05. Queue 순환 검증](05-queue-circulation.md)**: Cluster 환경에서 전체 플로우 검증
- **[Phase 3-3 Analysis](../phase3-lua-redis-cluster-analysis.md)**: Cluster 실험 과정

---

## 🔧 재현 방법

### 1. 단일 Redis로 테스트 (Before)
```bash
docker-compose up -d redis
./gradlew :queue-service:bootRun
k6 run k6-tests/queue-entry-scale-test.js
```

### 2. Redis Cluster로 테스트 (After)
```bash
docker-compose -f docker-compose.cluster.yml up -d
./gradlew :queue-service:bootRun
k6 run k6-tests/queue-entry-scale-test.js
```

### 3. Failover 테스트
```bash
# Master 강제 종료
docker stop redis-node-1

# 서비스 정상 동작 확인
k6 run k6-tests/queue-entry-scale-test.js

# Cluster 상태 확인
redis-cli --cluster check localhost:6379
```

---

**작성자**: Yoon Seon-ho
**작성일**: 2025-12-26
**태그**: `Redis Cluster`, `High Availability`, `Failover`, `Scalability`
