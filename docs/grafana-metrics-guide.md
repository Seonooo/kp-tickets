# Grafana 메트릭 모니터링 가이드

## 🎯 Connection Pool 조정 시 확인할 핵심 메트릭

### 1. DB Connection Pool 패널

#### Panel 1: Connection Pool Usage (%)
```promql
100 * (hikaricp_connections_active / hikaricp_connections_max)
```

**Alert 임계값:**
- Warning: > 70%
- Critical: > 90%

#### Panel 2: Active vs Max Connections
```promql
# Active
hikaricp_connections_active

# Max
hikaricp_connections_max

# Idle
hikaricp_connections_idle
```

#### Panel 3: Pending Requests (가장 중요!)
```promql
hikaricp_connections_pending
```

**Alert 임계값:**
- Critical: > 0 (즉시 조치 필요!)

#### Panel 4: Connection Acquire Time
```promql
hikaricp_connections_acquire_seconds
```

**Alert 임계값:**
- Warning: > 0.1s (100ms)
- Critical: > 0.5s (500ms)

---

### 2. 비즈니스 API 성능 (Actuator 제외)

#### Panel 5: API P95 Latency (비즈니스 API만)
```promql
histogram_quantile(0.95,
  sum by(uri) (
    rate(http_server_requests_seconds_bucket{
      uri!~"/actuator/.*",
      uri!~"/api/admin/.*"
    }[5m])
  )
)
```

#### Panel 6: 핵심 API별 응답 시간 (개별)
```promql
# 대기열 진입
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/v1/queue/enter"}[5m]))

# 좌석 조회
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri=~"/api/v1/schedules/.*/seats"}[5m]))

# 예약
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/v1/reservations"}[5m]))

# 결제
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket{uri="/api/v1/payments"}[5m]))
```

#### Panel 7: Request Rate (TPS)
```promql
sum(rate(http_server_requests_seconds_count{
  uri!~"/actuator/.*",
  uri!~"/api/admin/.*"
}[1m]))
```

#### Panel 8: Error Rate
```promql
sum(rate(http_server_requests_seconds_count{
  status=~"5..",
  uri!~"/actuator/.*"
}[1m]))
```

---

### 3. JVM Heap Memory

#### Panel 9: Heap Usage (%)
```promql
100 * (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"})
```

**Alert 임계값:**
- Warning: > 70%
- Critical: > 85%

---

## 📈 Connection Pool 조정 전후 비교

### Before: DB_POOL_MAX_SIZE=50
```
시간    Active  Pending  Usage   P95_Latency  TPS
────────────────────────────────────────────────
10s     45      0        90%     1.5s         40
20s     50      15       100%    3.2s         35
30s     50      89       100%    4.5s         28
40s     50      239      100%    5.8s         22  ❌
```

### After: DB_POOL_MAX_SIZE=150
```
시간    Active  Pending  Usage   P95_Latency  TPS
────────────────────────────────────────────────
10s     48      0        32%     0.8s         55
20s     72      0        48%     1.1s         68
30s     95      0        63%     1.3s         75
40s     110     0        73%     1.5s         82  ✅
```

**개선 효과:**
- Pending: 239 → 0 (완전 해소)
- Usage: 100% → 73% (여유 확보)
- P95 Latency: 5.8s → 1.5s (74% 개선)
- TPS: 22 → 82 (273% 증가)

---

## 🎯 실시간 모니터링 체크리스트

### Connection Pool 증가 후 확인 사항

1. ✅ **Pending Requests = 0**
   - 이전: 239 requests
   - 목표: 0 requests

2. ✅ **Connection Usage < 80%**
   - 이전: 100%
   - 목표: < 80%

3. ✅ **P95 Latency 감소**
   - Seats Query: 4.55s → < 1s
   - Reservation: 3.14s → < 1s
   - Payment: 4.26s → < 2s

4. ✅ **TPS 증가**
   - 이전: 44 req/s
   - 목표: > 80 req/s

5. ✅ **Error Rate 감소**
   - 이전: 5% (seats/reservation/payment failures)
   - 목표: < 1%

---

## 🔧 Grafana Dashboard JSON 수정

현재 대시보드에서 Actuator 제외하려면:

```json
{
  "targets": [
    {
      "expr": "histogram_quantile(0.95, sum by(uri) (rate(http_server_requests_seconds_bucket{uri!~\"/actuator/.*\", uri!~\"/api/admin/.*\"}[5m])))",
      "legendFormat": "{{uri}}"
    }
  ]
}
```

**필터링 패턴:**
- `uri!~"/actuator/.*"` - Actuator 제외
- `uri!~"/api/admin/.*"` - Admin API 제외
- `status!~"5.."` - 500 에러 제외 (에러율 계산 시)

---

## 💡 실전 팁

### 1. Connection Pool 조정 순서
```
1. 현재 메트릭 스냅샷 저장
   → Pending, Usage, P95 Latency 기록

2. Pool Size 증가 (50 → 150)
   → docker-compose 재시작

3. 부하 테스트 실행
   → k6 테스트 1분간 실행

4. Grafana에서 실시간 확인
   → Pending이 0으로 떨어지는지 확인
   → Usage가 80% 이하인지 확인

5. 결과 비교
   → P95 Latency 개선 확인
   → TPS 증가 확인
```

### 2. 최적값 찾기
```
Pool Size = (Peak Active Connections × 1.5) + Buffer

예시:
- Peak Active: 110
- 계산: 110 × 1.5 + 20 = 185
- 권장: 200 (여유 확보)
```

### 3. 모니터링 알림 설정
```promql
# Alert: Connection Pool Saturation
ALERT ConnectionPoolSaturated
  IF hikaricp_connections_pending > 0
  FOR 30s
  LABELS { severity = "critical" }
  ANNOTATIONS {
    summary = "DB Connection Pool is saturated!",
    description = "{{ $value }} requests are waiting for connections"
  }
```

---

## 📊 대시보드 레이아웃 권장

```
┌─────────────────────────────────────────────────┐
│  Connection Pool Overview (Row 1)               │
├──────────────┬──────────────┬──────────────────┤
│ Usage %      │ Active/Max   │ Pending Requests │
│ (Gauge)      │ (Graph)      │ (Graph)          │
└──────────────┴──────────────┴──────────────────┘

┌─────────────────────────────────────────────────┐
│  API Performance (Row 2)                        │
├──────────────┬──────────────────────────────────┤
│ P95 Latency  │ TPS by Endpoint                 │
│ by Endpoint  │ (비즈니스 API만)                  │
│ (Graph)      │ (Graph)                          │
└──────────────┴──────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│  System Resources (Row 3)                       │
├──────────────┬──────────────┬──────────────────┤
│ Heap Usage % │ GC Pause     │ CPU Usage        │
│ (Graph)      │ (Graph)      │ (Graph)          │
└──────────────┴──────────────┴──────────────────┘
```

---

## 🎯 핵심 요약

**Connection Pool 조정 시 Grafana에서 확인할 3가지:**

1. **hikaricp_connections_pending** → 0이 되어야 함!
2. **hikaricp_connections_active / max** → < 80%
3. **http_server_requests P95** (비즈니스 API만) → 목표치 달성

**불필요한 메트릭 제외:**
- `/actuator/*` - 내부 모니터링 엔드포인트
- `/api/admin/*` - 관리자 API
- 이들은 성능 지표를 왜곡시킴
