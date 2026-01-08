#!/bin/bash
# ============================================================================
# 병목 즉시 분석 스크립트
# ============================================================================

CORE_URL="http://localhost:8080/actuator/metrics"
QUEUE_URL="http://localhost:8081/actuator/metrics"

get_metric() {
    curl -s "$1/$2" 2>/dev/null | grep -oP '(?<="value":)[0-9.]+' | head -1
}

echo "============================================================"
echo "BOTTLENECK ANALYSIS - $(date)"
echo "============================================================"
echo ""

# 1. Database Connection Pool
echo "1️⃣  DATABASE CONNECTION POOL (HikariCP)"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

DB_ACTIVE=$(get_metric "$CORE_URL" "hikaricp.connections.active")
DB_IDLE=$(get_metric "$CORE_URL" "hikaricp.connections.idle")
DB_MAX=$(get_metric "$CORE_URL" "hikaricp.connections.max")
DB_PENDING=$(get_metric "$CORE_URL" "hikaricp.connections.pending")
DB_ACQUIRE=$(get_metric "$CORE_URL" "hikaricp.connections.acquire")

DB_TOTAL=$((${DB_ACTIVE%.*} + ${DB_IDLE%.*}))
DB_PCT=$(awk "BEGIN {printf \"%.0f\", ($DB_TOTAL / ${DB_MAX%.*}) * 100}" 2>/dev/null || echo "0")

printf "Active:    %6.0f connections\n" "${DB_ACTIVE:-0}"
printf "Idle:      %6.0f connections\n" "${DB_IDLE:-0}"
printf "Usage:     %6.0f / %.0f (%s%%)\n" "$DB_TOTAL" "${DB_MAX:-10}" "$DB_PCT"
printf "Pending:   %6.0f requests waiting\n" "${DB_PENDING:-0}"
printf "Acquire:   %6.1f ms (avg)\n" "${DB_ACQUIRE:-0}"

# 병목 판정
if [ "${DB_PCT:-0}" -gt 80 ]; then
    echo "❌ BOTTLENECK: Connection pool > 80% utilization!"
fi
if [ "${DB_PENDING%.*}" -gt 0 ]; then
    echo "❌ BOTTLENECK: Requests waiting for DB connections!"
fi
if (( $(echo "${DB_ACQUIRE:-0} > 100" | bc -l 2>/dev/null || echo 0) )); then
    echo "❌ BOTTLENECK: DB connection acquire time > 100ms!"
fi

echo ""

# 2. JVM Heap Memory
echo "2️⃣  JVM HEAP MEMORY"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

HEAP_USED=$(get_metric "$CORE_URL" "jvm.memory.used?tag=area:heap")
HEAP_MAX=$(get_metric "$CORE_URL" "jvm.memory.max?tag=area:heap")

HEAP_USED_MB=$(awk "BEGIN {printf \"%.0f\", ${HEAP_USED:-0} / 1048576}")
HEAP_MAX_MB=$(awk "BEGIN {printf \"%.0f\", ${HEAP_MAX:-1} / 1048576}")
HEAP_PCT=$(awk "BEGIN {printf \"%.0f\", (${HEAP_USED:-0} / ${HEAP_MAX:-1}) * 100}")

printf "Used:      %6.0f MB / %.0f MB (%s%%)\n" "$HEAP_USED_MB" "$HEAP_MAX_MB" "$HEAP_PCT"

if [ "${HEAP_PCT:-0}" -gt 85 ]; then
    echo "❌ BOTTLENECK: Heap usage > 85%!"
fi

echo ""

# 3. GC Performance
echo "3️⃣  GARBAGE COLLECTION"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

GC_PAUSE=$(get_metric "$CORE_URL" "jvm.gc.pause")

printf "Pause:     %6.1f ms (avg)\n" "${GC_PAUSE:-0}"

if (( $(echo "${GC_PAUSE:-0} > 100" | bc -l 2>/dev/null || echo 0) )); then
    echo "❌ BOTTLENECK: GC pause > 100ms!"
fi

echo ""

# 4. HTTP Active Requests
echo "4️⃣  HTTP THREAD POOL"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

HTTP_ACTIVE=$(get_metric "$CORE_URL" "http.server.requests.active")
THREADS=$(get_metric "$CORE_URL" "jvm.threads.live")

printf "Active:    %6.0f HTTP requests\n" "${HTTP_ACTIVE:-0}"
printf "Threads:   %6.0f live threads\n" "${THREADS:-0}"

echo ""

# 5. CPU Usage
echo "5️⃣  CPU USAGE"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

PROCESS_CPU=$(get_metric "$CORE_URL" "process.cpu.usage")
SYSTEM_CPU=$(get_metric "$CORE_URL" "system.cpu.usage")

PROCESS_CPU_PCT=$(awk "BEGIN {printf \"%.0f\", ${PROCESS_CPU:-0} * 100}")
SYSTEM_CPU_PCT=$(awk "BEGIN {printf \"%.0f\", ${SYSTEM_CPU:-0} * 100}")

printf "Process:   %6s%%\n" "$PROCESS_CPU_PCT"
printf "System:    %6s%%\n" "$SYSTEM_CPU_PCT"

if [ "${PROCESS_CPU_PCT:-0}" -gt 90 ]; then
    echo "❌ BOTTLENECK: CPU usage > 90%!"
fi

echo ""
echo "============================================================"
echo ""

# Summary
echo "📊 BOTTLENECK SUMMARY:"
echo ""
if [ "${DB_PCT:-0}" -gt 80 ] || [ "${DB_PENDING%.*}" -gt 0 ]; then
    echo "  🔴 DATABASE CONNECTION POOL - Primary bottleneck"
    echo "     → Increase DB_POOL_MAX_SIZE from 10 to 50+"
elif [ "${HEAP_PCT:-0}" -gt 85 ]; then
    echo "  🔴 JVM HEAP MEMORY - Primary bottleneck"
    echo "     → Increase heap size (Xmx) from 2G to 3G+"
elif [ "${PROCESS_CPU_PCT:-0}" -gt 90 ]; then
    echo "  🔴 CPU SATURATION - Primary bottleneck"
    echo "     → Scale out (add more service instances)"
else
    echo "  🟢 No obvious bottlenecks detected"
    echo "     → Performance issues may be in application logic"
fi

echo ""
