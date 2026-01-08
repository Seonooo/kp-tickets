#!/bin/bash
# ============================================================================
# E2E 테스트 실행 스크립트 (데이터 초기화 포함)
# ============================================================================

set -e  # 에러 발생시 스크립트 중단

# 스크립트 디렉토리 확인 (상대 경로 문제 해결)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# 환경변수에서 비밀번호 읽기 (.env 파일 로드)
if [ -f "$SCRIPT_DIR/../.env" ]; then
    export $(grep -v '^#' "$SCRIPT_DIR/../.env" | xargs)
fi

# 비밀번호 기본값 (환경변수 미설정시)
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-rootpassword}"
REDIS_PASSWORD="${REDIS_PASSWORD:-redispassword}"

echo "============================================================"
echo "E2E Test with Data Initialization"
echo "============================================================"

# 1. 테스트 데이터 초기화
echo ""
echo "Step 1: Initializing test data..."
docker exec -i concert-db mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" concert_db < "$SCRIPT_DIR/setup-test-data.sql"

# 데이터 확인
echo ""
echo "Step 2: Verifying data..."
docker exec concert-db mysql -uroot -p"${MYSQL_ROOT_PASSWORD}" concert_db -e "
  SELECT
    'Concerts' as table_name, COUNT(*) as count FROM concerts
  UNION ALL
  SELECT 'Schedules', COUNT(*) FROM concert_schedules
  UNION ALL
  SELECT 'Seats (AVAILABLE)', COUNT(*) FROM seats WHERE status = 'AVAILABLE';
"

# 2. Redis 캐시 플러시 (깔끔한 시작)
echo ""
echo "Step 3: Flushing Redis caches..."
# REDISCLI_AUTH 환경변수 사용으로 명령줄에 비밀번호 노출 방지
docker exec -e REDISCLI_AUTH="${REDIS_PASSWORD}" concert-redis-queue redis-cli FLUSHALL
docker exec -e REDISCLI_AUTH="${REDIS_PASSWORD}" concert-redis-core redis-cli FLUSHALL

# 3. k6 테스트 실행
echo ""
echo "Step 4: Running k6 E2E test..."
echo "============================================================"
docker compose run --rm k6 run /scripts/queue-e2e-circulation-test.js

echo ""
echo "============================================================"
echo "Test completed!"
echo "============================================================"
