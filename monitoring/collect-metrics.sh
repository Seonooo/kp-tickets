#!/bin/bash
# ============================================================================
# 간단한 메트릭 수집 스크립트
# 사용법: ./collect-metrics.sh <duration_seconds>
# ============================================================================

DURATION=${1:-60}
INTERVAL=5
CORE_URL="http://localhost:8080/actuator/metrics"

echo "Collecting metrics every ${INTERVAL}s for ${DURATION}s..."
echo "timestamp,db_active,db_pending,db_acquire_ms,heap_used_pct,gc_pause_ms,http_active"

for ((i=0; i<$DURATION; i+=$INTERVAL)); do
    TIMESTAMP=$(date +%s)

    # DB Connection Pool
    DB_ACTIVE=$(curl -s "$CORE_URL/hikaricp.connections.active" | grep -oP '(?<="value":)[0-9.]+' || echo "0")
    DB_PENDING=$(curl -s "$CORE_URL/hikaricp.connections.pending" | grep -oP '(?<="value":)[0-9.]+' || echo "0")
    DB_ACQUIRE=$(curl -s "$CORE_URL/hikaricp.connections.acquire" | grep -oP '(?<="value":)[0-9.]+' || echo "0")

    # JVM Heap
    HEAP_USED=$(curl -s "$CORE_URL/jvm.memory.used?tag=area:heap" | grep -oP '(?<="value":)[0-9.]+' || echo "0")
    HEAP_MAX=$(curl -s "$CORE_URL/jvm.memory.max?tag=area:heap" | grep -oP '(?<="value":)[0-9.]+' || echo "1")
    HEAP_PCT=$(awk "BEGIN {printf \"%.1f\", ($HEAP_USED / $HEAP_MAX) * 100}")

    # GC Pause
    GC_PAUSE=$(curl -s "$CORE_URL/jvm.gc.pause" | grep -oP '(?<="value":)[0-9.]+' || echo "0")

    # HTTP Active Requests
    HTTP_ACTIVE=$(curl -s "$CORE_URL/http.server.requests.active" | grep -oP '(?<="value":)[0-9.]+' || echo "0")

    echo "$TIMESTAMP,$DB_ACTIVE,$DB_PENDING,$DB_ACQUIRE,$HEAP_PCT,$GC_PAUSE,$HTTP_ACTIVE"

    sleep $INTERVAL
done
