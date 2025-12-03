package personal.ai.acceptance.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers 설정 클래스
 * 실제 MySQL, Redis, Kafka 컨테이너를 사용하여 통합 테스트 수행
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfiguration {

    /**
     * MySQL 컨테이너
     * Spring Boot 3.1+의 @ServiceConnection을 사용하여 자동으로 DataSource 설정
     */
    @Bean
    @ServiceConnection
    MySQLContainer<?> mysqlContainer() {
        return new MySQLContainer<>(DockerImageName.parse("mysql:8.0.36"))
                .withDatabaseName("concert_db")
                .withUsername("test_user")
                .withPassword("test_password")
                .withReuse(true);  // 재사용으로 테스트 속도 향상
    }

    /**
     * Redis 컨테이너
     * @ServiceConnection을 사용하여 자동으로 Redis 설정
     */
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                .withExposedPorts(6379)
                .withReuse(true);
    }

    /**
     * Kafka 컨테이너
     * @ServiceConnection을 사용하여 자동으로 Kafka 설정
     */
    @Bean
    @ServiceConnection
    KafkaContainer kafkaContainer() {
        return new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1"))
                .withReuse(true);
    }
}
