package personal.ai.core.acceptance.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import personal.ai.core.booking.application.port.out.QueueServiceClient;

/**
 * Test용 QueueServiceClient Mock 설정
 * 실제 Queue Service 호출 없이 테스트 가능하도록 함
 */
@TestConfiguration
@Profile("test")
public class TestQueueServiceClientConfig {

    @Bean
    @Primary
    public QueueServiceClient mockQueueServiceClient() {
        return (userId, queueToken) -> {
            // "INVALID" 토큰이 들어오면 예외 발생
            if (queueToken != null && queueToken.contains("INVALID")) {
                throw new personal.ai.common.exception.BusinessException(
                        personal.ai.common.exception.ErrorCode.QUEUE_TOKEN_INVALID,
                        "유효하지 않은 대기열 토큰입니다.");
            }
            // 만료된 토큰
            if (queueToken != null && queueToken.contains("EXPIRED")) {
                throw new personal.ai.common.exception.BusinessException(
                        personal.ai.common.exception.ErrorCode.QUEUE_TOKEN_EXPIRED,
                        "만료된 대기열 토큰입니다.");
            }
            // 그 외에는 성공 (유효한 토큰으로 간주)
            // Do nothing - pass
        };
    }
}
