package personal.ai.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Core Service Application
 * User, Booking, Payment 도메인을 포함하는 핵심 비즈니스 서비스
 */
@EnableCaching     // Redis Cache 활성화 (Seats Query 성능 최적화)
@EnableScheduling  // Outbox Scheduler 활성화
@SpringBootApplication(
    scanBasePackages = {
        "personal.ai.core",
        "personal.ai.common"  // common 모듈의 GlobalExceptionHandler 등을 스캔
    }
)
public class CoreServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CoreServiceApplication.class, args);
    }
}
