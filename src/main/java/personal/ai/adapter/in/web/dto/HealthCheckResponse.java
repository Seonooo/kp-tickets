package personal.ai.adapter.in.web.dto;

/**
 * Health Check 응답 데이터
 *
 * @param database 데이터베이스 상태 ("UP" 또는 "DOWN")
 * @param redis    Redis 상태 ("UP" 또는 "DOWN")
 * @param kafka    Kafka 상태 ("UP" 또는 "DOWN")
 */
public record HealthCheckResponse(
        String database,
        String redis,
        String kafka
) {
}
