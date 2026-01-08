@echo off
REM ============================================================================
REM E2E 테스트 실행 스크립트 (데이터 초기화 포함) - Windows
REM ============================================================================

REM 환경변수 기본값 설정 (미설정시 기본값 사용)
if "%MYSQL_ROOT_PASSWORD%"=="" set MYSQL_ROOT_PASSWORD=rootpassword
if "%REDIS_PASSWORD%"=="" set REDIS_PASSWORD=redispassword

REM 스크립트 디렉토리 확인
set SCRIPT_DIR=%~dp0

echo ============================================================
echo E2E Test with Data Initialization
echo ============================================================

REM 1. 테스트 데이터 초기화
echo.
echo Step 1: Initializing test data...
docker exec -i concert-db mysql -uroot -p%MYSQL_ROOT_PASSWORD% concert_db < "%SCRIPT_DIR%setup-test-data.sql"

if %ERRORLEVEL% NEQ 0 (
    echo Error: Failed to initialize test data
    exit /b 1
)

REM 2. 데이터 확인
echo.
echo Step 2: Verifying data...
docker exec concert-db mysql -uroot -p%MYSQL_ROOT_PASSWORD% concert_db -e "SELECT 'Concerts' as table_name, COUNT(*) as count FROM concerts UNION ALL SELECT 'Schedules', COUNT(*) FROM concert_schedules UNION ALL SELECT 'Seats (AVAILABLE)', COUNT(*) FROM seats WHERE status = 'AVAILABLE';"

REM 3. Redis 캐시 플러시 (깔끔한 시작)
REM REDISCLI_AUTH 환경변수 사용으로 명령줄에 비밀번호 노출 방지
echo.
echo Step 3: Flushing Redis caches...
docker exec -e REDISCLI_AUTH=%REDIS_PASSWORD% concert-redis-queue redis-cli FLUSHALL
docker exec -e REDISCLI_AUTH=%REDIS_PASSWORD% concert-redis-core redis-cli FLUSHALL

REM 4. k6 테스트 실행
echo.
echo Step 4: Running k6 E2E test...
echo ============================================================
docker compose run --rm k6 run /scripts/queue-e2e-circulation-test.js

echo.
echo ============================================================
echo Test completed!
echo ============================================================
