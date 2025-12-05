package personal.ai.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.queue.application.port.in.CleanupExpiredTokensUseCase;
import personal.ai.queue.application.port.in.MoveToActiveQueueUseCase;
import personal.ai.queue.application.port.out.QueueRepository;
import personal.ai.queue.domain.model.QueueConfig;
import personal.ai.queue.domain.service.QueueDomainService;

import java.time.Instant;
import java.util.List;

/**
 * Queue Scheduler Service
 * Wait -> Active 전환 및 만료 토큰 정리 로직
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueSchedulerService implements
        MoveToActiveQueueUseCase,
        CleanupExpiredTokensUseCase {

    private final QueueRepository queueRepository;
    private final QueueDomainService domainService;
    private final QueueConfig queueConfig;

    @Override
    public int moveWaitingToActive(String concertId) {
        log.debug("Moving users from wait to active queue: concertId={}", concertId);

        // 현재 Active Queue 크기 확인
        Long currentActiveSize = queueRepository.getActiveQueueSize(concertId);

        // 전환 가능한 인원 계산
        int availableSlots = domainService.calculateBatchSize(currentActiveSize);

        if (availableSlots <= 0) {
            log.debug("No available slots: concertId={}, currentSize={}",
                    concertId, currentActiveSize);
            return 0;
        }

        // Wait Queue에서 Pop하고 Active Queue에 원자적으로 추가
        // Lua Script로 처리하여 데이터 손실 방지
        Instant expiration = domainService.calculateReadyExpiration();
        List<String> movedUserIds = queueRepository.moveToActiveQueueAtomic(
                concertId,
                availableSlots,
                expiration
        );

        if (movedUserIds.isEmpty()) {
            log.debug("No users moved: concertId={}", concertId);
            return 0;
        }

        log.info("Moved users to active queue atomically: concertId={}, moved={}, available={}",
                concertId, movedUserIds.size(), availableSlots);

        return movedUserIds.size();
    }

    @Override
    public int moveAllConcerts() {
        log.debug("Moving all concerts");

        List<String> concertIds = queueRepository.getActiveConcertIds();
        int totalMoved = 0;

        for (String concertId : concertIds) {
            try {
                int moved = moveWaitingToActive(concertId);
                totalMoved += moved;
            } catch (Exception e) {
                log.error("Failed to move users for concertId={}", concertId, e);
            }
        }

        return totalMoved;
    }

    @Override
    public long cleanupExpired(String concertId) {
        log.debug("Cleaning up expired tokens: concertId={}", concertId);

        long removedCount = queueRepository.removeExpiredTokens(concertId);

        if (removedCount > 0) {
            log.info("Removed expired tokens: concertId={}, count={}", concertId, removedCount);
        }

        return removedCount;
    }

    @Override
    public long cleanupAllConcerts() {
        log.debug("Cleaning up all concerts");

        List<String> concertIds = queueRepository.getActiveConcertIds();
        long totalRemoved = 0;

        for (String concertId : concertIds) {
            try {
                long removed = cleanupExpired(concertId);
                totalRemoved += removed;
            } catch (Exception e) {
                log.error("Failed to cleanup tokens for concertId={}", concertId, e);
            }
        }

        return totalRemoved;
    }
}
