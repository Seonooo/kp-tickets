@echo off
REM ============================================================================
REM E2E 테스트 실행 스크립트 (데이터 초기화 포함) - Windows
REM ============================================================================

echo ============================================================
echo E2E Test with Data Initialization
echo ============================================================

REM 1. 테스트 데이터 초기화
echo.
echo Step 1: Initializing test data...
docker exec -i concert-db mysql -uroot -prootpassword concert_db < k6-tests\setup-test-data.sql

if %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to initialize test data
    exit /b 1
)

REM 2. 데이터 확인
echo.
echo Step 2: Verifying data...
docker exec concert-db mysql -uroot -prootpassword concert_db -e "SELECT 'Concerts' as table_name, COUNT(*) as count FROM concerts UNION ALL SELECT 'Schedules', COUNT(*) FROM concert_schedules UNION ALL SELECT 'Seats (AVAILABLE)', COUNT(*) FROM seats WHERE status = 'AVAILABLE';"

REM 3. Redis 캐시 플러시 (깔끔한 시작)
echo.
echo Step 3: Flushing Redis caches...
docker exec concert-redis-queue redis-cli -a redispassword FLUSHALL
docker exec concert-redis-core redis-cli -a redispassword FLUSHALL

REM 4. k6 테스트 실행
echo.
echo Step 4: Running k6 E2E test...
echo ============================================================
docker-compose run --rm k6 run /scripts/queue-e2e-circulation-test.js

echo.
echo ============================================================
echo Test completed!
echo ============================================================
