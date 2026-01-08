#!/bin/bash
# ============================================================================
# Prometheus 직접 쿼리로 병목 측정
# ============================================================================

PROM_URL="http://localhost:9090/api/v1/query"

query_prom() {
    local query=$1
    curl -s --data-urlencode "query=$query" "$PROM_URL" | \
        grep -oP '(?<="value":\[)[0-9]+,"[0-9.]+"' | \
        cut -d'"' -f2
}

echo "============================================================"
echo "REAL-TIME BOTTLENECK ANALYSIS (Prometheus)"
echo "============================================================"
echo ""

# 1. Database Connection Pool
echo "1️⃣  DATABASE CONNECTION POOL"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

DB_ACTIVE=$(query_prom "hikaricp_connections_active")
DB_IDLE=$(query_prom "hikaricp_connections_idle")
DB_MAX=$(query_prom "hikaricp_connections_max")
DB_PENDING=$(query_prom "hikaricp_connections_pending")
DB_ACQUIRE=$(query_prom "hikaricp_connections_acquire_seconds")

echo "Active:           $DB_ACTIVE"
echo "Idle:             $DB_IDLE"
echo "Max:              $DB_MAX"
echo "Pending:          $DB_PENDING"
echo "Acquire Time:     ${DB_ACQUIRE}s"

# Usage percentage
DB_USAGE=$(query_prom "100 * (hikaricp_connections_active / hikaricp_connections_max)")
echo "Usage:            ${DB_USAGE}%"

if (( $(echo "$DB_USAGE > 80" | bc -l 2>/dev/null || echo 0) )); then
    echo "❌ BOTTLENECK DETECTED: DB Connection Pool > 80%"
fi

echo ""

# 2. Redis Command Latency
echo "2️⃣  REDIS COMMAND LATENCY"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

REDIS_COMPLETION=$(query_prom "lettuce_command_completion_seconds_sum / lettuce_command_completion_seconds_count")
echo "Avg Completion:   ${REDIS_COMPLETION}s"

if (( $(echo "$REDIS_COMPLETION > 0.05" | bc -l 2>/dev/null || echo 0) )); then
    echo "❌ BOTTLENECK DETECTED: Redis latency > 50ms"
fi

echo ""

# 3. JVM Memory
echo "3️⃣  JVM MEMORY"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

HEAP_USED=$(query_prom 'jvm_memory_used_bytes{area="heap"}')
HEAP_MAX=$(query_prom 'jvm_memory_max_bytes{area="heap"}')
HEAP_PCT=$(query_prom '100 * (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"})')

HEAP_USED_MB=$(awk "BEGIN {printf \"%.0f\", $HEAP_USED / 1048576}" 2>/dev/null || echo "0")
HEAP_MAX_MB=$(awk "BEGIN {printf \"%.0f\", $HEAP_MAX / 1048576}" 2>/dev/null || echo "0")

echo "Heap Used:        ${HEAP_USED_MB}MB / ${HEAP_MAX_MB}MB (${HEAP_PCT}%)"

if (( $(echo "$HEAP_PCT > 85" | bc -l 2>/dev/null || echo 0) )); then
    echo "❌ BOTTLENECK DETECTED: Heap > 85%"
fi

echo ""

# 4. HTTP Request Rate & Duration
echo "4️⃣  HTTP PERFORMANCE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

HTTP_RATE=$(query_prom "rate(http_server_requests_seconds_count[1m])")
HTTP_P95=$(query_prom 'histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[1m]))')

echo "Request Rate:     ${HTTP_RATE} req/s"
echo "P95 Latency:      ${HTTP_P95}s"

echo ""

# 5. Top 3 Slowest Endpoints
echo "5️⃣  SLOWEST ENDPOINTS (P95)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

curl -s --data-urlencode 'query=topk(3, histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m])))' \
    "$PROM_URL" | \
    grep -oP '"uri":"[^"]+"|"value":\[[0-9]+,"[0-9.]+"' | \
    paste - - | \
    sed 's/"uri":"\([^"]*\)".*"value":\[[0-9]*,"\([^"]*\)"/\1: \2s/' | \
    head -3

echo ""

# 6. Circuit Breaker Status
echo "6️⃣  CIRCUIT BREAKER"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

CB_STATE=$(query_prom 'resilience4j_circuitbreaker_state')
CB_FAILURE=$(query_prom 'resilience4j_circuitbreaker_failure_rate')

if [ "$CB_STATE" == "1" ]; then
    echo "❌ Circuit Breaker: OPEN (blocking requests!)"
else
    echo "✓ Circuit Breaker: CLOSED"
fi
echo "Failure Rate:     ${CB_FAILURE}%"

echo ""
echo "============================================================"
