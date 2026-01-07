package personal.ai.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.queue.adapter.out.redis.RedisEnterQueueAdapter;
import personal.ai.queue.application.port.in.EnterQueueUseCase;
import personal.ai.queue.domain.model.QueuePosition;

/**
 * Enter Queue Service (SRP)
 * 단일 책임: 대기열 진입
 *
 * Phase 3-2 최적화: Lua 스크립트 통합
 *   - 기존: 6회 Redis 호출 (HGETALL + ZRANK + ZCARD + ZADD + ZRANK + ZCARD)
 *   - 개선: 1회 Lua 스크립트 호출
 *   - 효과: 네트워크 RTT 5회 절약 (약 5ms), 예상 TPS +30~50%
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterQueueService implements EnterQueueUseCase {

    private final RedisEnterQueueAdapter redisEnterQueueAdapter;

    @Override
    public QueuePosition enter(EnterQueueCommand command) {
        String concertId = command.concertId();
        String userId = command.userId();

        log.debug("Enter queue request: concertId={}, userId={}", concertId, userId);

        // Phase 3-2: 단일 Lua 스크립트로 모든 검증 및 진입 처리
        // 1. Active Token 확인
        // 2. Wait Queue 확인
        // 3. 신규 진입 처리
        // → 모두 하나의 원자적 연산으로 처리
        return redisEnterQueueAdapter.enterQueue(concertId, userId);
    }
}
