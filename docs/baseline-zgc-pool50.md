# Baseline Test Results - ZGC + Pool 50

**테스트 일시**: 2025-12-30 18:05
**환경**: ZGC 적용, DB Pool 50 connections
**테스트**: queue-e2e-circulation-test.js (83초, 1220 VUs)

## 🎯 최적화 상태
- ✅ **GC**: ZGC 적용 (Pause ~0ms)
- ❌ **DB Pool**: 50 connections (병목 확인)

## 📊 핵심 성능 지표

### HTTP 처리량
```
TPS:              182.7 req/s
Total Requests:   15,318
Failed Requests:  2.33% (358/15,318)
```

### Iteration 성공률
```
✅ Completed:     3,156
❌ Dropped:       3,749 (54% drop rate!)
   Drop Rate:     44.7 iterations/s
```

### API 응답 시간 (P95)

| API | 목표 | 실제 | 초과율 | 실패율 |
|-----|------|------|--------|--------|
| Queue Entry | <200ms | **1.71s** | 8.5배 ❌ | 0% |
| Poll | <100ms | **1.85s** | 18.5배 ❌ | - |
| **Seats Query** | <500ms | **6.04s** | **12배 ❌** | 4.1% |
| **Reservation** | <1000ms | **3.46s** | **3.5배 ❌** | 6.9% |
| **Payment** | <2000ms | **3.92s** | **2배 ❌** | 0.7% |

### 전체 HTTP Duration
```
Average:  1.28s
Median:   933ms
P90:      2.9s
P95:      3.61s
P99:      7.3s
Max:      17.59s
```

### 비즈니스 메트릭
```
Booking Success Rate:   88.65% (목표: >95%) ❌
Seats Query Failures:   128 (4.1%)
Reservation Failures:   210 (6.9%)
Payment Failures:       20 (0.7%)
```

### E2E Duration
```
Average:  16.4s
Median:   14.9s
P90:      25.8s
P95:      29.3s
Max:      48.3s
```

## 🔴 확인된 병목

### 1. DB Connection Pool 포화 (주요 병목)
- **증상**:
  - Seats Query P95: 6.04s (DB 조회 대기)
  - Reservation P95: 3.46s (DB 쓰기 대기)
  - Payment P95: 3.92s (트랜잭션 대기)
- **원인**: 50 connections로 182 req/s 처리 불가
- **영향**: 54% iteration drop

### 2. 높은 요청 실패율
- HTTP 실패: 2.33%
- Seats 실패: 4.1%
- Reservation 실패: 6.9%
- 모두 DB 대기로 인한 타임아웃 추정

## ✅ ZGC 효과 확인

| 지표 | Before (G1 GC) | After (ZGC) | 개선율 |
|------|----------------|-------------|--------|
| **TPS** | 44 req/s | **182.7 req/s** | **+315%** |
| **GC Pause** | 45ms | **~0ms** | **완전 제거** |

## 🎯 다음 단계

**DB Connection Pool 150으로 증가** ✅ 완료
→ 재테스트 실행하여 병목 해소 확인 필요

### 예상 개선 효과
- Pending Requests: 감소 예상
- Pool Usage: 100% → 60-70%
- API Latency: 50-70% 감소 예상
- Drop Rate: 54% → 10% 이하 예상
- Booking Success: 88.65% → >95% 예상
