package personal.ai.core.acceptance.support;

import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Cucumber와 Spring Boot를 통합하기 위한 설정 클래스
 * Testcontainers를 사용하여 실제 MySQL, Redis, Kafka 컨테이너로 테스트
 * agent.md Testing Strategy - BDD Style
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({ TestContainersConfiguration.class, TestQueueServiceClientConfig.class, BookingTestAdapter.class,
        BookingHttpAdapter.class })
public class CucumberSpringConfiguration {
}
