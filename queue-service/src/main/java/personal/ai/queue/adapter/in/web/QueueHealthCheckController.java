package personal.ai.queue.adapter.in.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import personal.ai.common.dto.ApiResponse;
import personal.ai.common.dto.HealthCheckResponse;
import personal.ai.common.health.HealthCheckService;

/**
 * Queue Service Health Check Controller
 * Redis와 Kafka 상태만 확인 (경량 서비스)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class QueueHealthCheckController {

    private final HealthCheckService healthCheckService;

    @GetMapping("/health")
    public ResponseEntity<ApiResponse<HealthCheckResponse>> healthCheck() {
        log.debug("Queue service health check requested");

        String redisStatus = healthCheckService.checkRedis();
        String kafkaStatus = healthCheckService.checkKafka();

        HealthCheckResponse data = HealthCheckResponse.forQueueService(redisStatus, kafkaStatus);

        boolean healthy = "UP".equals(redisStatus) && "UP".equals(kafkaStatus);

        if (healthy) {
            return ResponseEntity.ok(
                    ApiResponse.success("Queue service is healthy", data)
            );
        } else {
            return ResponseEntity.ok(
                    ApiResponse.error("Some components are unhealthy", data)
            );
        }
    }
}
