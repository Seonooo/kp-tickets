# 병목 측정 가이드

## 📊 측정 방법

### Option 1: Grafana 대시보드 (추천)

**실시간 시각화로 병목을 한눈에 파악**

1. **Grafana 접속**: http://localhost:3000
2. **대시보드 임포트**: `monitoring/grafana-dashboard-application.json`
3. **k6 테스트 실행**: `docker-compose run --rm k6 run /scripts/queue-e2e-circulation-test.js`
4. **실시간 관찰**:
   - DB Connection Pool 사용률
   - Heap Memory 사용률
   - GC Pause Time
   - HTTP Request Duration (P50, P95, P99)
   - 각 엔드포인트별 응답 시간

### Option 2: Prometheus 쿼리 (명령줄)

**부하 테스트 실행 중 또는 직후에 실행**

```bash
# 1. DB Connection Pool 병목 확인
curl -s "http://localhost:9090/api/v1/query?query=hikaricp_connections_active" | jq '.data.result[0].value[1]'
curl -s "http://localhost:9090/api/v1/query?query=hikaricp_connections_max" | jq '.data.result[0].value[1]'
curl -s "http://localhost:9090/api/v1/query?query=hikaricp_connections_pending" | jq '.data.result[0].value[1]'

# 사용률 계산
curl -s "http://localhost:9090/api/v1/query?query=100*(hikaricp_connections_active/hikaricp_connections_max)" | jq '.data.result[0].value[1]'
```

**병목 판정 기준:**
- ✅ Usage < 70%: 여유 있음
- ⚠️ Usage 70-90%: 주의 필요
- ❌ Usage > 90% OR Pending > 0: **병목!**

```bash
# 2. JVM Heap Memory 병목 확인
curl -s 'http://localhost:9090/api/v1/query?query=100*(jvm_memory_used_bytes{area="heap"}/jvm_memory_max_bytes{area="heap"})' | jq '.data.result[0].value[1]'
```

**병목 판정 기준:**
- ✅ < 70%: 정상
- ⚠️ 70-85%: 주의
- ❌ > 85%: **병목!**

```bash
# 3. GC Pause Time 병목 확인 (평균)
curl -s 'http://localhost:9090/api/v1/query?query=rate(jvm_gc_pause_seconds_sum[5m])/rate(jvm_gc_pause_seconds_count[5m])' | jq '.data.result[0].value[1]'
```

**병목 판정 기준:**
- ✅ < 50ms: 정상
- ⚠️ 50-100ms: 주의
- ❌ > 100ms: **병목!**

```bash
# 4. HTTP Request Duration (P95)
curl -s 'http://localhost:9090/api/v1/query?query=histogram_quantile(0.95,rate(http_server_requests_seconds_bucket[5m]))' | jq '.data.result'
```

```bash
# 5. Redis Command Latency
curl -s 'http://localhost:9090/api/v1/query?query=rate(lettuce_command_completion_seconds_sum[5m])/rate(lettuce_command_completion_seconds_count[5m])' | jq '.data.result[0].value[1]'
```

**병목 판정 기준:**
- ✅ < 10ms: 정상
- ⚠️ 10-50ms: 주의
- ❌ > 50ms: **병목!**

### Option 3: 간편 스크립트 (실시간 측정)

**테스트와 동시에 실행하여 병목 지점 추적**

```bash
# Terminal 1: k6 테스트 실행
docker-compose run --rm k6 run /scripts/queue-e2e-circulation-test.js

# Terminal 2: 실시간 병목 모니터링 (5초마다)
watch -n 5 "
  echo '=== DB Pool ===';
  curl -s 'http://localhost:9090/api/v1/query?query=hikaricp_connections_active' | grep -oP '(?<=\")[0-9.]+(?=\"\])';
  echo '=== Heap % ===';
  curl -s 'http://localhost:9090/api/v1/query?query=100*(jvm_memory_used_bytes{area=\"heap\"}/jvm_memory_max_bytes{area=\"heap\"})' | grep -oP '(?<=\")[0-9.]+(?=\"\])';
  echo '=== GC Pause ms ===';
  curl -s 'http://localhost:9090/api/v1/query?query=1000*rate(jvm_gc_pause_seconds_sum[1m])/rate(jvm_gc_pause_seconds_count[1m])' | grep -oP '(?<=\")[0-9.]+(?=\"\])';
"
```

## 📈 병목 분석 프로세스

### 1. 베이스라인 수집 (테스트 전)

```bash
# 유휴 상태 메트릭 저장
curl -s http://localhost:8080/actuator/metrics/hikaricp.connections.active > baseline.txt
curl -s http://localhost:8080/actuator/metrics/jvm.memory.used?tag=area:heap >> baseline.txt
```

### 2. 부하 테스트 실행

```bash
docker-compose run --rm k6 run /scripts/queue-e2e-circulation-test.js
```

### 3. 피크 시점 메트릭 수집 (테스트 중/직후)

```bash
# Prometheus에서 최근 5분간 최대값 조회
curl -s 'http://localhost:9090/api/v1/query?query=max_over_time(hikaricp_connections_active[5m])'
curl -s 'http://localhost:9090/api/v1/query?query=max_over_time(jvm_memory_used_bytes{area="heap"}[5m])'
```

### 4. 결과 분석

**병목 우선순위 결정:**

1. **DB Connection Pool**
   - Pending > 0 → 즉시 해결 필요 (가장 심각)
   - Usage > 90% → Connection Pool 증가

2. **JVM Heap Memory**
   - Usage > 85% → Heap 크기 증가 또는 메모리 누수 확인

3. **GC Pause**
   - Pause > 100ms → GC 튜닝 또는 Heap 증가

4. **Redis Latency**
   - > 50ms → Redis 성능 문제 또는 네트워크 지연

5. **HTTP Duration**
   - P95 > 목표치 → Application 로직 최적화 필요

## 🎯 병목별 해결 방안

### DB Connection Pool 병목

```yaml
# .env
DB_POOL_MAX_SIZE=100  # 기존 50 → 100
DB_POOL_MIN_IDLE=20   # 기존 5 → 20
```

### JVM Heap 병목

```yaml
# docker-compose.yml
core-service:
  environment:
    JAVA_TOOL_OPTIONS: "-Xms2g -Xmx4g"  # 2GB → 4GB
  deploy:
    resources:
      limits:
        memory: 5G  # 3G → 5G
```

### Redis 병목

```yaml
# docker-compose.yml
redis-core:
  deploy:
    resources:
      limits:
        memory: 2G  # 1G → 2G
```

```yaml
# .env
REDIS_POOL_MAX_ACTIVE=100  # 20 → 100
```

### CPU 병목

```bash
# Scale Out - 서비스 인스턴스 증가
docker-compose up -d --scale core-service=3
```

## 📊 측정 결과 예시

```
=== BEFORE OPTIMIZATION ===
DB Pool Usage:        92% (46/50) ❌ BOTTLENECK
Heap Usage:           78%
GC Pause:             65ms
Pending Requests:     15 ❌ BOTTLENECK

=== AFTER: DB_POOL_MAX_SIZE=100 ===
DB Pool Usage:        48% (48/100) ✅ IMPROVED
Heap Usage:           76%
GC Pause:             60ms
Pending Requests:     0 ✅ RESOLVED

Throughput:           26 req/s → 45 req/s (+73%)
P95 Latency:          3.64s → 1.85s (-49%)
```

## 🔄 반복 측정

병목을 하나씩 해결하면서 **재측정**하여 개선 효과를 확인:

1. 베이스라인 측정
2. 병목 #1 해결 (예: DB Pool 증가)
3. 재측정 → 개선 확인
4. 병목 #2 해결 (예: Heap 증가)
5. 재측정 → 개선 확인
6. ...

**데이터 기반 최적화 = 추측 제거 + 근거 있는 의사결정**
