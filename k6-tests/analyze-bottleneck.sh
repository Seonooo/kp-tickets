#!/bin/bash
# ============================================================================
# 병목 분석 스크립트 - 테스트 실행 중 메트릭 수집
# ============================================================================

set -e

CORE_METRICS_URL="http://localhost:8080/actuator/metrics"
QUEUE_METRICS_URL="http://localhost:8081/actuator/metrics"
RESULTS_DIR="k6-tests/results/bottleneck-analysis"

echo "============================================================"
echo "Performance Bottleneck Analysis"
echo "============================================================"

# 결과 디렉토리 생성
mkdir -p "$RESULTS_DIR"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
REPORT_FILE="$RESULTS_DIR/bottleneck_${TIMESTAMP}.txt"

echo "Results will be saved to: $REPORT_FILE"
echo ""

# 메트릭 수집 함수
collect_metric() {
    local service=$1
    local url=$2
    local metric_name=$3

    curl -s "${url}/${metric_name}" | grep -oP '(?<="value":)[0-9.]+' | head -1
}

# 병목 분석 리포트 생성
generate_report() {
    echo "============================================================" | tee -a "$REPORT_FILE"
    echo "Bottleneck Analysis Report - $(date)" | tee -a "$REPORT_FILE"
    echo "============================================================" | tee -a "$REPORT_FILE"
    echo "" | tee -a "$REPORT_FILE"

    # 1. Database Connection Pool (HikariCP)
    echo "📊 1. DATABASE CONNECTION POOL (HikariCP)" | tee -a "$REPORT_FILE"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$REPORT_FILE"

    ACTIVE=$(collect_metric "core" "$CORE_METRICS_URL" "hikaricp.connections.active")
    IDLE=$(collect_metric "core" "$CORE_METRICS_URL" "hikaricp.connections.idle")
    MAX=$(collect_metric "core" "$CORE_METRICS_URL" "hikaricp.connections.max")
    PENDING=$(collect_metric "core" "$CORE_METRICS_URL" "hikaricp.connections.pending")
    TIMEOUT=$(collect_metric "core" "$CORE_METRICS_URL" "hikaricp.connections.timeout")
    ACQUIRE_MS=$(collect_metric "core" "$CORE_METRICS_URL" "hikaricp.connections.acquire")

    TOTAL=$((${ACTIVE:-0} + ${IDLE:-0}))
    USAGE_PCT=$(awk "BEGIN {printf \"%.1f\", ($TOTAL / ${MAX:-10}) * 100}")

    echo "Active Connections:   $ACTIVE" | tee -a "$REPORT_FILE"
    echo "Idle Connections:     $IDLE" | tee -a "$REPORT_FILE"
    echo "Total Used:           $TOTAL / $MAX ($USAGE_PCT%)" | tee -a "$REPORT_FILE"
    echo "Pending Requests:     $PENDING" | tee -a "$REPORT_FILE"
    echo "Timeout Count:        $TIMEOUT" | tee -a "$REPORT_FILE"
    echo "Acquire Time (avg):   ${ACQUIRE_MS}ms" | tee -a "$REPORT_FILE"

    # 병목 판정
    if (( $(echo "$USAGE_PCT > 80" | bc -l) )); then
        echo "⚠️  WARNING: Connection pool usage > 80% - BOTTLENECK!" | tee -a "$REPORT_FILE"
    fi
    if (( ${PENDING:-0} > 0 )); then
        echo "⚠️  WARNING: $PENDING requests waiting for connections - BOTTLENECK!" | tee -a "$REPORT_FILE"
    fi
    if (( $(echo "${ACQUIRE_MS:-0} > 100" | bc -l) )); then
        echo "⚠️  WARNING: Connection acquire time > 100ms - BOTTLENECK!" | tee -a "$REPORT_FILE"
    fi

    echo "" | tee -a "$REPORT_FILE"

    # 2. Redis Connection Pool (Lettuce)
    echo "📊 2. REDIS CONNECTION POOL (Lettuce)" | tee -a "$REPORT_FILE"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$REPORT_FILE"

    REDIS_FIRST_RESPONSE=$(collect_metric "core" "$CORE_METRICS_URL" "lettuce.command.firstresponse?tag=command:GET")
    REDIS_COMPLETION=$(collect_metric "core" "$CORE_METRICS_URL" "lettuce.command.completion?tag=command:GET")

    echo "First Response Time:  ${REDIS_FIRST_RESPONSE}ms" | tee -a "$REPORT_FILE"
    echo "Completion Time:      ${REDIS_COMPLETION}ms" | tee -a "$REPORT_FILE"

    if (( $(echo "${REDIS_COMPLETION:-0} > 50" | bc -l) )); then
        echo "⚠️  WARNING: Redis command time > 50ms - BOTTLENECK!" | tee -a "$REPORT_FILE"
    fi

    echo "" | tee -a "$REPORT_FILE"

    # 3. JVM Heap Memory
    echo "📊 3. JVM HEAP MEMORY" | tee -a "$REPORT_FILE"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$REPORT_FILE"

    HEAP_USED=$(collect_metric "core" "$CORE_METRICS_URL" "jvm.memory.used?tag=area:heap")
    HEAP_MAX=$(collect_metric "core" "$CORE_METRICS_URL" "jvm.memory.max?tag=area:heap")

    HEAP_USED_MB=$(awk "BEGIN {printf \"%.0f\", $HEAP_USED / 1048576}")
    HEAP_MAX_MB=$(awk "BEGIN {printf \"%.0f\", $HEAP_MAX / 1048576}")
    HEAP_USAGE_PCT=$(awk "BEGIN {printf \"%.1f\", ($HEAP_USED / $HEAP_MAX) * 100}")

    echo "Heap Used:            ${HEAP_USED_MB}MB / ${HEAP_MAX_MB}MB ($HEAP_USAGE_PCT%)" | tee -a "$REPORT_FILE"

    if (( $(echo "$HEAP_USAGE_PCT > 85" | bc -l) )); then
        echo "⚠️  WARNING: Heap usage > 85% - BOTTLENECK!" | tee -a "$REPORT_FILE"
    fi

    echo "" | tee -a "$REPORT_FILE"

    # 4. GC (Garbage Collection)
    echo "📊 4. GARBAGE COLLECTION" | tee -a "$REPORT_FILE"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$REPORT_FILE"

    GC_PAUSE=$(collect_metric "core" "$CORE_METRICS_URL" "jvm.gc.pause")

    echo "GC Pause Time (avg):  ${GC_PAUSE}ms" | tee -a "$REPORT_FILE"

    if (( $(echo "${GC_PAUSE:-0} > 100" | bc -l) )); then
        echo "⚠️  WARNING: GC pause > 100ms - BOTTLENECK!" | tee -a "$REPORT_FILE"
    fi

    echo "" | tee -a "$REPORT_FILE"

    # 5. HTTP Thread Pool
    echo "📊 5. HTTP THREAD POOL" | tee -a "$REPORT_FILE"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$REPORT_FILE"

    THREADS_LIVE=$(collect_metric "core" "$CORE_METRICS_URL" "jvm.threads.live")
    THREADS_PEAK=$(collect_metric "core" "$CORE_METRICS_URL" "jvm.threads.peak")
    HTTP_ACTIVE=$(collect_metric "core" "$CORE_METRICS_URL" "http.server.requests.active")

    echo "Live Threads:         $THREADS_LIVE" | tee -a "$REPORT_FILE"
    echo "Peak Threads:         $THREADS_PEAK" | tee -a "$REPORT_FILE"
    echo "Active HTTP Requests: $HTTP_ACTIVE" | tee -a "$REPORT_FILE"

    echo "" | tee -a "$REPORT_FILE"

    # 6. HTTP Request Duration by Endpoint
    echo "📊 6. HTTP REQUEST DURATION (by endpoint)" | tee -a "$REPORT_FILE"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$REPORT_FILE"

    # Get top 5 slowest endpoints
    curl -s "$CORE_METRICS_URL/http.server.requests" | \
        grep -oP '"uri":"[^"]+"|"value":[0-9.]+' | \
        paste -d' ' - - | \
        sort -k2 -rn | \
        head -5 | tee -a "$REPORT_FILE"

    echo "" | tee -a "$REPORT_FILE"

    # 7. Circuit Breaker Status
    echo "📊 7. CIRCUIT BREAKER STATUS" | tee -a "$REPORT_FILE"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" | tee -a "$REPORT_FILE"

    CB_STATE=$(collect_metric "core" "$CORE_METRICS_URL" "resilience4j.circuitbreaker.state")
    CB_FAILURE_RATE=$(collect_metric "core" "$CORE_METRICS_URL" "resilience4j.circuitbreaker.failure.rate")

    if [ "$CB_STATE" == "1" ]; then
        echo "⚠️  Circuit Breaker: OPEN (blocking requests!)" | tee -a "$REPORT_FILE"
    else
        echo "✓ Circuit Breaker: CLOSED (healthy)" | tee -a "$REPORT_FILE"
    fi
    echo "Failure Rate:         ${CB_FAILURE_RATE}%" | tee -a "$REPORT_FILE"

    echo "" | tee -a "$REPORT_FILE"
    echo "============================================================" | tee -a "$REPORT_FILE"
}

# 테스트 전 베이스라인 수집
echo "📸 Collecting baseline metrics (before test)..."
generate_report

echo ""
echo "🚀 Starting k6 load test..."
echo "   (Metrics will be collected every 10 seconds)"
echo ""

# 백그라운드에서 k6 테스트 실행
docker-compose run --rm k6 run /scripts/queue-e2e-circulation-test.js &
K6_PID=$!

# 테스트 실행 중 메트릭 주기적 수집
sleep 15  # 테스트 초기화 대기
for i in {1..7}; do
    echo "📊 Collecting metrics... ($i/7)" | tee -a "$REPORT_FILE"
    generate_report
    sleep 10
done

# k6 테스트 완료 대기
wait $K6_PID

echo ""
echo "✅ Analysis complete! Report saved to: $REPORT_FILE"
echo ""
echo "📈 Summary of bottlenecks found:"
grep "⚠️" "$REPORT_FILE" | sort | uniq -c | sort -rn

echo ""
echo "Next steps:"
echo "  1. Review full report: cat $REPORT_FILE"
echo "  2. Check Grafana dashboards: http://localhost:3000"
echo "  3. Apply targeted optimizations based on bottleneck data"
