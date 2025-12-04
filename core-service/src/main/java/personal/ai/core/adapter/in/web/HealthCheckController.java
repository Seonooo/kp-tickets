package personal.ai.core.adapter.in.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import personal.ai.common.dto.ApiResponse;
import personal.ai.common.dto.HealthCheckResponse;
import personal.ai.common.health.HealthCheckService;

import javax.sql.DataSource;

/**
 * Health Check API Controller
 * 애플리케이션 및 인프라 상태를 확인하는 엔드포인트를 제공합니다.
 * <p>
 * agent.md API Design Guidelines:
 * - URI 버전 명시: /api/v1
 * - ApiResponse<T> 포맷 사용
 * - HTTP 200 OK 반환
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class HealthCheckController {

    private final DataSource dataSource;
    private final HealthCheckService healthCheckService;

    /**
     * Health Check 엔드포인트
     * 데이터베이스, Redis, Kafka의 연결 상태를 확인합니다.
     *
     * @return ApiResponse with health check data
     */
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthCheckResponse>> healthCheck() {
        log.debug("Health check requested");

        String databaseStatus = healthCheckService.checkDatabase(dataSource);
        String redisStatus = healthCheckService.checkRedis();
        String kafkaStatus = healthCheckService.checkKafka();

        HealthCheckResponse data = new HealthCheckResponse(
                databaseStatus,
                redisStatus,
                kafkaStatus
        );

        boolean allHealthy = "UP".equals(databaseStatus)
                && "UP".equals(redisStatus)
                && "UP".equals(kafkaStatus);

        if (allHealthy) {
            return ResponseEntity.ok(
                    ApiResponse.success("Application is healthy", data)
            );
        } else {
            return ResponseEntity.ok(
                    ApiResponse.error("Some components are unhealthy", data)
            );
        }
    }
}
