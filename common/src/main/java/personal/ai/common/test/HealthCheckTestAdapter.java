package personal.ai.common.test;

import io.restassured.response.Response;

/**
 * Health Check 테스트 어댑터 인터페이스 (Port)
 * Hexagonal Architecture - 각 서비스가 자신의 Health Check 검증 방식을 구현
 */
public interface HealthCheckTestAdapter {

    /**
     * 헬스 체크 API 호출
     * 공통 엔드포인트: /api/v1/health
     */
    Response callHealthCheckApi();

    /**
     * HTTP 응답 상태 코드 검증
     */
    void verifyStatusCode(Response response, int expectedStatusCode);

    /**
     * API 응답 결과 검증
     */
    void verifyResult(Response response, String expectedResult);

    /**
     * 데이터베이스 상태 정보 검증
     * Database를 사용하는 서비스만 구현
     */
    void verifyDatabaseStatus(Response response);

    /**
     * Redis 상태 정보 검증
     * Redis를 사용하는 모든 서비스가 구현
     */
    void verifyRedisStatus(Response response);

    /**
     * Kafka 상태 정보 검증
     * Kafka를 사용하는 모든 서비스가 구현
     */
    void verifyKafkaStatus(Response response);
}
