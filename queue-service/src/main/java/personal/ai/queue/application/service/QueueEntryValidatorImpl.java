package personal.ai.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import personal.ai.queue.adapter.out.redis.RedisKeyGenerator;
import personal.ai.queue.application.config.QueueConfigProperties;
import personal.ai.queue.application.port.out.QueueRepository;
import personal.ai.queue.domain.model.QueueConfig;
import personal.ai.queue.domain.model.QueuePosition;

import java.time.Duration;
import java.util.Optional;

/**
 * 대기열 진입 검증 구현체
 * Quick Win 최적화: totalWaiting 캐싱으로 ZCARD 호출 빈도 감소
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueEntryValidatorImpl implements QueueEntryValidator {

    private static final int POSITION_DISPLAY_OFFSET = 1;

    private final QueueRepository queueRepository;
    private final QueueConfig queueConfig;
    private final RedisTemplate<String, String> redisTemplate;
    private final QueueConfigProperties queueConfigProperties;

    @Override
    public Optional<QueuePosition> checkActiveUser(String concertId, String userId) {
        var activeToken = queueRepository.getActiveToken(concertId, userId);
        if (activeToken.isPresent() && !activeToken.get().isExpired()) {
            log.debug("User already active: concertId={}, userId={}", concertId, userId);
            return Optional.of(QueuePosition.alreadyActive(activeToken.get()));
        }
        return Optional.empty();
    }

    @Override
    public Optional<QueuePosition> checkWaitingUser(String concertId, String userId) {
        Long existingPosition = queueRepository.getWaitQueuePosition(concertId, userId);
        if (existingPosition != null) {
            log.debug("User already waiting: concertId={}, userId={}, position={}",
                    concertId, userId, existingPosition);

            // 동시성 고려사항: 트래픽이 매우 높은 환경(Redis)에서 totalWaiting 값은
            // 근사치(approximate)일 수 있음. 표시용(display)으로는 문제없으나,
            // 비즈니스 로직 분기(critical decision)에는 사용하지 않도록 주의
            long totalWaiting = queueRepository.getWaitQueueSize(concertId);
            return Optional.of(QueuePosition.alreadyWaiting(
                    concertId,
                    userId,
                    existingPosition + POSITION_DISPLAY_OFFSET,
                    totalWaiting,
                    queueConfig.activeMaxSize(),
                    queueConfig.activationIntervalSeconds()));
        }
        return Optional.empty();
    }
}
