package personal.ai.common.health;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Health Check 공통 유틸리티 서비스
 * 각 인프라 컴포넌트의 상태를 확인하는 재사용 가능한 메서드 제공
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HealthCheckService {

    private final RedisTemplate<String, String> redisTemplate;
    private final KafkaAdmin kafkaAdmin;

    /**
     * Redis 연결 상태 확인
     *
     * @return "UP" if Redis is reachable, "DOWN" otherwise
     */
    public String checkRedis() {
        try {
            String response = redisTemplate.execute((RedisConnection connection) -> {
                return connection.ping();
            });
            return "PONG".equals(response) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.error("Redis health check failed", e);
            return "DOWN";
        }
    }

    /**
     * Kafka 연결 상태 확인
     *
     * @return "UP" if Kafka cluster is reachable, "DOWN" otherwise
     */
    public String checkKafka() {
        try {
            try (AdminClient adminClient =
                        AdminClient.create(kafkaAdmin.getConfigurationProperties())) {

                var clusterInfo = adminClient.describeCluster();
                var nodes = clusterInfo.nodes().get(5, java.util.concurrent.TimeUnit.SECONDS);

                return (nodes != null && !nodes.isEmpty()) ? "UP" : "DOWN";
            }
        } catch (Exception e) {
            log.error("Kafka health check failed", e);
            return "DOWN";
        }
    }

    /**
     * 데이터베이스 연결 상태 확인
     * DataSource를 사용하는 서비스에서만 호출 가능
     *
     * @param dataSource the DataSource to check
     * @return "UP" if database is reachable, "DOWN" otherwise
     */
    public String checkDatabase(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(1) ? "UP" : "DOWN";
        } catch (Exception e) {
            log.error("Database health check failed", e);
            return "DOWN";
        }
    }
}
