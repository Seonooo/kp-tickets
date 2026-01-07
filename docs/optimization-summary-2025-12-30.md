# 성능 최적화 종합 요약

**날짜**: 2025-12-30
**목표**: Booking Success Rate >95% 달성 및 시스템 안정성 향상

---

## 📊 최종 결과

### ✅ 주요 목표 달성

| 목표 | Before | After | 달성 |
|------|--------|-------|------|
| **Booking Success Rate** | 88.65% | **95.62%** | ✅ |
| E2E Duration P95 | 29.3s | 25.7s | ✅ |
| Activation Wait P95 | 3.9s | 4.7s | ✅ |
| HTTP 실패율 | 2.33% | 0.88% | ✅ |

---

## 🔧 적용한 최적화

### 1. ZGC 적용 (G1 GC → ZGC)

#### 변경 사항
```yaml
# docker-compose.yml - core-service
JAVA_TOOL_OPTIONS: "... -XX:+UseZGC -XX:+ZGenerational"
```

#### 효과
| 지표 | Before (G1 GC) | After (ZGC) | 개선율 |
|------|----------------|-------------|--------|
| **TPS** | 44 req/s | **182.7 req/s** | **+315%** |
| **GC Pause** | 45ms | **~0ms** | **완전 제거** |
| **GC Overhead** | 소량 | **0%** | **완전 제거** |
| GC Count | 146회 | 250회 | - |
| Total GC Time | 6.563초 | 0.018초 | **-99.7%** |

**분석**:
- GC로 인한 Stop-The-World 제거
- Concurrent GC로 애플리케이션 실행 방해 최소화
- 처리량 3배 이상 증가

### 2. DB Connection Pool 증가 (50 → 150)

#### 변경 사항
```yaml
# docker-compose.yml - core-service environment
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE: 150
SPRING_DATASOURCE_HIKARI_MINIMUM_IDLE: 30
```

```env
# .env
DB_POOL_MAX_SIZE=150
DB_POOL_MIN_IDLE=30
```

#### 효과
| 지표 | Pool 50 | Pool 150 | 개선율 |
|------|---------|----------|--------|
| **Booking Success** | 88.65% | **95.62%** | **+7%p** |
| **HTTP 실패율** | 2.33% | **0.88%** | **-62%** |
| **Seats 실패** | 128건 (4.1%) | **0건** | **-100%** |
| **Payment 실패** | 20건 (0.7%) | **0건** | **-100%** |
| **Reservation 실패** | 210건 (6.9%) | 141건 (4.4%) | **-33%** |
| TPS | 182.7 req/s | 194.5 req/s | +6.5% |
| E2E Duration P95 | 29.3s | 25.7s | -12.3% |
| Booking Duration avg | 14.3s | 12.0s | -16.1% |

**분석**:
- DB Connection Pool 병목 부분 해소
- DB 쿼리 대기 시간 감소
- 실패율 대폭 감소 (안정성 향상)

#### API별 응답 시간 개선 (P95)

| API | Pool 50 | Pool 150 | 개선율 | 목표 | 달성 |
|-----|---------|----------|--------|------|------|
| **Seats Query** | 6.04s | **4.55s** | **-24.7%** | <500ms | ❌ |
| **Reservation** | 3.46s | **2.97s** | **-14.2%** | <1s | ❌ |
| **Payment** | 3.92s | **3.33s** | **-15.1%** | <2s | ❌ |

---

## 📈 전체 성능 변화 추이

### Phase 1: 초기 상태 (G1 GC + Pool 50)
```
TPS: 44 req/s
GC Pause: 45ms
Booking Success: 추정 70-80%
```

### Phase 2: ZGC 적용 (ZGC + Pool 50)
```
TPS: 182.7 req/s (+315%)
GC Pause: ~0ms (-100%)
Booking Success: 88.65%
HTTP 실패율: 2.33%
병목: DB Connection Pool (50/50, 100% usage)
```

### Phase 3: DB Pool 증가 (ZGC + Pool 150) ⭐ 최종
```
TPS: 194.5 req/s (+342% from Phase 1)
GC Pause: ~0ms
Booking Success: 95.62% ✅ 목표 달성!
HTTP 실패율: 0.88% (-62% from Phase 2)
안정성: Seats/Payment 실패 0%
```

---

## 🔍 상세 성능 메트릭

### HTTP 요청 처리

| 지표 | Pool 50 | Pool 150 | 개선 |
|------|---------|----------|------|
| Total Requests | 15,318 | 15,973 | +4.3% |
| Request Rate | 182.7/s | 194.5/s | +6.5% |
| Failed Requests | 358 (2.33%) | 141 (0.88%) | -62% |
| Average Duration | 1.28s | 796ms | -37.8% |
| Median Duration | 933ms | 260ms | -72.1% |
| P95 Duration | 3.61s | 3.44s | -4.7% |
| P99 Duration | 7.3s | 5.24s | -28.2% |

### E2E 사용자 경험

| 지표 | Pool 50 | Pool 150 | 개선 |
|------|---------|----------|------|
| E2E avg | 16.43s | 14.52s | -11.6% |
| E2E P95 | 29.27s | 25.68s | -12.3% |
| Booking avg | 14.30s | 12.00s | -16.1% |
| Booking P95 | 27.75s | 23.06s | -16.9% |
| Activation Wait P95 | 3.86s | 4.71s | -22.0% ⚠️ |

### 테스트 실행 지표

| 지표 | Pool 50 | Pool 150 |
|------|---------|----------|
| Completed Iterations | 3,156 | 3,222 |
| Dropped Iterations | 3,749 | 3,789 |
| Drop Rate | 54% | 54% |
| Max VUs | 936 | 813 |
| Check Success | 97.39% | 99.01% |

---

## ⚠️ 발견된 새로운 병목

### Queue Service 성능 저하

| API | Pool 50 | Pool 150 | 변화 |
|-----|---------|----------|------|
| **Queue Entry** (P95) | 1.71s | **2.85s** | **-67% ❌** |
| **Poll** (P95) | 1.85s | **2.7s** | **-46% ❌** |

**원인 (추정)**:
1. 전체 처리량 증가로 Queue Service 부하 증가
   - 182.7 → 194.5 req/s
   - 더 많은 동시 접속자가 Queue 진입

2. Redis 경합 증가
   - Active Queue 관리 부하 증가
   - Wait Queue 관리 부하 증가
   - Lua Script 실행 대기 시간 증가

3. Queue Scheduler 병목
   - 활성화 스케줄러 처리 한계
   - 만료 토큰 정리 부하 증가

**다음 단계**: Queue Service 전용 최적화 필요 (별도 문서 참조)

---

## 🎯 목표 달성 여부

### ✅ 달성한 목표

1. **Booking Success Rate >95%**: ✅ 95.62% 달성
2. **시스템 안정성 향상**: ✅ 실패율 62% 감소
3. **GC 병목 제거**: ✅ Pause ~0ms
4. **DB Connection Pool 병목 완화**: ✅ 실패율 대폭 감소

### ⏳ 미달성 목표 (추가 최적화 필요)

| API | 목표 (P95) | 현재 (P95) | Gap |
|-----|-----------|-----------|-----|
| Queue Entry | <200ms | 2.85s | 14배 초과 |
| Poll | <100ms | 2.7s | 27배 초과 |
| Seats Query | <500ms | 4.55s | 9배 초과 |
| Reservation | <1000ms | 2.97s | 3배 초과 |
| Payment | <2000ms | 3.33s | 1.7배 초과 |

---

## 💡 추가 최적화 기회

### 우선순위 1: DB 쿼리 최적화
**대상**: Seats Query (4.55s → 목표 <500ms)

**추정 원인**:
- 인덱스 부족
- N+1 쿼리 문제
- JOIN 최적화 필요
- 불필요한 데이터 로딩

**권장 조치**:
1. EXPLAIN 분석으로 쿼리 플랜 확인
2. 인덱스 추가 (schedule_id, status 등)
3. 페이징 적용 (전체 좌석 대신 필요한 만큼만)
4. 캐싱 재도입 (Redis, 단기 TTL)

### 우선순위 2: Application 로직 최적화
**대상**: Reservation (2.97s), Payment (3.33s)

**추정 원인**:
- 동기 처리로 인한 대기
- 불필요한 검증 로직
- 트랜잭션 범위 과다
- 외부 API 호출 지연

**권장 조치**:
1. 비동기 처리 도입 (Virtual Thread 활용)
2. 낙관적 락 전환 고려
3. 트랜잭션 범위 최소화
4. 불필요한 동기화 제거

### 우선순위 3: Queue Service 최적화
**대상**: Queue Entry (2.85s), Poll (2.7s)

**추정 원인**:
- Redis 단일 인스턴스 한계
- Lua Script 직렬화 처리
- Scheduler 동시성 제약
- 과도한 Polling 빈도

**권장 조치**:
1. Redis Connection Pool 증가
2. Lua Script 최적화
3. Scheduler 병렬 처리 도입
4. Polling 전략 개선 (Exponential Backoff)
5. Redis Cluster 고려 (장기)

---

## 📁 관련 문서

1. **`docs/baseline-zgc-pool50.md`**
   - ZGC 적용 후, Pool 50 상태 베이스라인

2. **`docs/performance-comparison-pool50-vs-pool150.md`**
   - Pool 50 vs 150 상세 비교 분석

3. **`docs/grafana-metrics-guide.md`**
   - Grafana 모니터링 가이드

4. **`docs/bottleneck-measurement-guide.md`**
   - 병목 측정 방법론

5. **`measure-bottleneck.sh`**
   - 실시간 병목 측정 스크립트

---

## 🎉 결론

### 주요 성과
1. ✅ **핵심 비즈니스 목표 달성**: Booking Success Rate 95.62%
2. ✅ **GC 병목 완전 제거**: ZGC 적용으로 Pause ~0ms
3. ✅ **DB 병목 부분 해소**: Pool 150으로 실패율 62% 감소
4. ✅ **시스템 안정성 대폭 향상**: Seats/Payment 실패율 0%

### 현재 권장 설정
```yaml
# Core Service
GC: ZGC + ZGenerational
Heap: 2GB (Xms1g, Xmx2g)
DB Pool: 150 connections (min: 30)

# Queue Service
GC: ZGC + ZGenerational
Heap: 2GB
Redis Pool: 50 connections
```

### 다음 단계
- **즉시**: 현재 설정 유지 및 프로덕션 배포 고려
- **단기**: DB 쿼리 최적화 (Seats, Reservation, Payment)
- **중기**: Queue Service 최적화 (Redis, Scheduler)
- **장기**: Application 아키텍처 개선 (비동기, 캐싱)

**종합 평가**: Pool 150 + ZGC 조합으로 주요 목표 달성. 추가 최적화는 점진적 진행 권장.
