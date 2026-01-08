#!/bin/bash
# ============================================================================
# 병목 즉시 측정 스크립트 (Prometheus 기반)
# 사용법: ./measure-bottleneck.sh
# ============================================================================

PROM="http://localhost:9090/api/v1/query"

# Prometheus 쿼리 헬퍼
pq() {
    curl -s "$PROM?query=$1" | grep -oP '(?<=value":\[)[0-9.]+,"[0-9.]+"' | cut -d'"' -f2
}

echo "============================================================"
echo "병목 측정 리포트 - $(date '+%Y-%m-%d %H:%M:%S')"
echo "============================================================"
echo ""

# 1. DB Connection Pool
echo "1️⃣  DATABASE CONNECTION POOL (HikariCP)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

DB_ACTIVE=$(pq "hikaricp_connections_active")
DB_MAX=$(pq "hikaricp_connections_max")
DB_PENDING=$(pq "hikaricp_connections_pending")

# Usage 계산
if [ -n "$DB_ACTIVE" ] && [ -n "$DB_MAX" ]; then
    DB_PCT=$(awk "BEGIN {printf \"%.0f\", ($DB_ACTIVE / $DB_MAX) * 100}")
    echo "사용중:     ${DB_ACTIVE} / ${DB_MAX} connections (${DB_PCT}%)"
else
    echo "사용중:     데이터 없음"
    DB_PCT=0
fi

echo "대기중:     ${DB_PENDING:-0} requests"

# 병목 판정
if [ "$DB_PCT" -gt 80 ]; then
    echo "❌ 병목 감지: Connection Pool 사용률 > 80%"
    echo "   → 권장: DB_POOL_MAX_SIZE를 ${DB_MAX}에서 $((DB_MAX * 2))로 증가"
fi

if [ "${DB_PENDING%.*}" -gt 0 ] 2>/dev/null; then
    echo "❌ 병목 감지: ${DB_PENDING} 요청이 Connection 대기 중!"
    echo "   → 권장: 즉시 Connection Pool 증가 필요"
fi

echo ""

# 2. JVM Heap Memory
echo "2️⃣  JVM HEAP MEMORY"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

HEAP_PCT=$(pq '100*(jvm_memory_used_bytes{area="heap"}/jvm_memory_max_bytes{area="heap"})')
HEAP_USED=$(pq 'jvm_memory_used_bytes{area="heap"}')
HEAP_MAX=$(pq 'jvm_memory_max_bytes{area="heap"}')

if [ -n "$HEAP_USED" ] && [ -n "$HEAP_MAX" ]; then
    HEAP_USED_MB=$(awk "BEGIN {printf \"%.0f\", $HEAP_USED / 1048576}")
    HEAP_MAX_MB=$(awk "BEGIN {printf \"%.0f\", $HEAP_MAX / 1048576}")
    HEAP_PCT_INT=${HEAP_PCT%.*}
    echo "사용중:     ${HEAP_USED_MB}MB / ${HEAP_MAX_MB}MB (${HEAP_PCT_INT}%)"
else
    echo "사용중:     데이터 없음"
    HEAP_PCT_INT=0
fi

if [ "$HEAP_PCT_INT" -gt 85 ] 2>/dev/null; then
    echo "❌ 병목 감지: Heap 사용률 > 85%"
    echo "   → 권장: Xmx를 ${HEAP_MAX_MB}MB에서 $((HEAP_MAX_MB * 2))MB로 증가"
fi

echo ""

# 3. GC Pause Time
echo "3️⃣  GARBAGE COLLECTION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# GC pause 평균 (최근 5분)
GC_PAUSE=$(pq 'rate(jvm_gc_pause_seconds_sum[5m])/rate(jvm_gc_pause_seconds_count[5m])')

if [ -n "$GC_PAUSE" ]; then
    GC_PAUSE_MS=$(awk "BEGIN {printf \"%.1f\", $GC_PAUSE * 1000}")
    echo "Pause 평균: ${GC_PAUSE_MS}ms"

    GC_PAUSE_INT=${GC_PAUSE_MS%.*}
    if [ "$GC_PAUSE_INT" -gt 100 ] 2>/dev/null; then
        echo "❌ 병목 감지: GC Pause > 100ms"
        echo "   → 권장: Heap 크기 증가 또는 GC 알고리즘 변경"
    fi
else
    echo "Pause 평균: 데이터 없음 (최근 GC 없음)"
fi

echo ""

# 4. HTTP Request Performance
echo "4️⃣  HTTP 요청 성능 (최근 5분)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

HTTP_RATE=$(pq 'rate(http_server_requests_seconds_count[5m])')
HTTP_P95=$(pq 'histogram_quantile(0.95,rate(http_server_requests_seconds_bucket[5m]))')

if [ -n "$HTTP_RATE" ]; then
    echo "처리율:     $(awk "BEGIN {printf \"%.1f\", $HTTP_RATE}") req/s"
else
    echo "처리율:     0.0 req/s (트래픽 없음)"
fi

if [ -n "$HTTP_P95" ]; then
    HTTP_P95_MS=$(awk "BEGIN {printf \"%.0f\", $HTTP_P95 * 1000}")
    echo "P95 지연:   ${HTTP_P95_MS}ms"
else
    echo "P95 지연:   데이터 없음"
fi

echo ""

# 5. CPU Usage
echo "5️⃣  CPU 사용률"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

PROCESS_CPU=$(pq 'process_cpu_usage')
SYSTEM_CPU=$(pq 'system_cpu_usage')

if [ -n "$PROCESS_CPU" ]; then
    PROCESS_CPU_PCT=$(awk "BEGIN {printf \"%.0f\", $PROCESS_CPU * 100}")
    echo "프로세스:   ${PROCESS_CPU_PCT}%"

    if [ "$PROCESS_CPU_PCT" -gt 90 ] 2>/dev/null; then
        echo "❌ 병목 감지: CPU 사용률 > 90%"
        echo "   → 권장: Scale Out (서비스 인스턴스 증가)"
    fi
else
    echo "프로세스:   데이터 없음"
fi

if [ -n "$SYSTEM_CPU" ]; then
    SYSTEM_CPU_PCT=$(awk "BEGIN {printf \"%.0f\", $SYSTEM_CPU * 100}")
    echo "시스템:     ${SYSTEM_CPU_PCT}%"
fi

echo ""

# 6. 종합 판정
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "📊 종합 병목 분석"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

BOTTLENECK_FOUND=false

if [ "$DB_PCT" -gt 80 ] 2>/dev/null || [ "${DB_PENDING%.*}" -gt 0 ] 2>/dev/null; then
    echo "🔴 주요 병목: DATABASE CONNECTION POOL"
    echo "   현재: ${DB_ACTIVE:-?} / ${DB_MAX:-?} (${DB_PCT}%)"
    echo "   조치: .env에서 DB_POOL_MAX_SIZE 증가"
    BOTTLENECK_FOUND=true
fi

if [ "$HEAP_PCT_INT" -gt 85 ] 2>/dev/null; then
    echo "🔴 주요 병목: JVM HEAP MEMORY"
    echo "   현재: ${HEAP_USED_MB}MB / ${HEAP_MAX_MB}MB (${HEAP_PCT_INT}%)"
    echo "   조치: docker-compose.yml에서 Xmx 증가"
    BOTTLENECK_FOUND=true
fi

if [ "$PROCESS_CPU_PCT" -gt 90 ] 2>/dev/null; then
    echo "🔴 주요 병목: CPU SATURATION"
    echo "   현재: ${PROCESS_CPU_PCT}%"
    echo "   조치: Scale Out 또는 코드 최적화"
    BOTTLENECK_FOUND=true
fi

if ! $BOTTLENECK_FOUND; then
    echo "🟢 명확한 리소스 병목 없음"
    echo "   → 성능 문제는 Application 로직에 있을 가능성 높음"
    echo "   → 각 API 엔드포인트별 상세 프로파일링 권장"
fi

echo ""
echo "============================================================"
echo ""
echo "💡 다음 단계:"
echo "  1. Grafana 대시보드 확인: http://localhost:3000"
echo "  2. 가이드 참조: docs/bottleneck-measurement-guide.md"
echo "  3. 병목 해결 후 재측정하여 개선 확인"
echo ""
