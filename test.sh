#!/bin/bash
# Concert Ticketing 통합 테스트 실행 스크립트
# Testcontainers + Docker 환경

set -e

echo ""
echo "========================================="
echo "  Concert Ticketing Test Suite"
echo "  Testcontainers + Docker 환경"
echo "========================================="
echo ""
echo "이 스크립트는 다음을 수행합니다:"
echo "  1. Docker 컨테이너 내에서 Gradle 실행 (Linux 환경)"
echo "  2. Testcontainers가 MySQL, Redis, Kafka를 자동으로 시작"
echo "  3. 모든 테스트 실행 (Unit + Cucumber BDD)"
echo "  4. 테스트 완료 후 자동으로 정리"
echo ""
echo "필수 요구사항:"
echo "  - Docker Desktop 실행 중"
echo ""
read -p "계속하시겠습니까? (y/n) " -n 1 -r
echo ""

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "테스트 실행 취소"
    exit 0
fi

echo ""
echo "테스트 시작..."
echo ""

# 이전 테스트 컨테이너 정리
echo "이전 테스트 환경 정리 중..."
docker-compose -f docker-compose.test.yml down -v 2>/dev/null || true

echo ""

# 테스트 실행
docker-compose -f docker-compose.test.yml up --abort-on-container-exit --exit-code-from test-runner

# 종료 코드 저장
EXIT_CODE=$?

echo ""
echo "테스트 환경 정리 중..."
docker-compose -f docker-compose.test.yml down -v

echo ""
if [ $EXIT_CODE -eq 0 ]; then
    echo "✅ 테스트 성공!"
else
    echo "❌ 테스트 실패 (Exit Code: $EXIT_CODE)"
fi

exit $EXIT_CODE
