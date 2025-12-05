package personal.ai.core.booking.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.application.port.out.SeatLockRepository;

import java.time.Duration;
import java.util.Collections;

/**
 * Redis Seat Lock Adapter
 * Redis SETNX 기반 좌석 선점 구현체
 * Lua Script를 사용한 원자적 락 해제 (소유권 검증)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSeatLockAdapter implements SeatLockRepository {

    private static final String SEAT_LOCK_PREFIX = "seat:lock:";
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> releaseLockScript;

    @Override
    public boolean tryLock(Long seatId, Long userId, int ttlSeconds) {
        String key = SEAT_LOCK_PREFIX + seatId;
        String value = String.valueOf(userId);

        // SETNX + TTL을 원자적으로 수행 (setIfAbsent)
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, value, Duration.ofSeconds(ttlSeconds));

        boolean locked = Boolean.TRUE.equals(success);

        log.debug("Seat lock attempt: seatId={}, userId={}, success={}", seatId, userId, locked);

        return locked;
    }

    @Override
    public void unlock(Long seatId, Long userId) {
        String key = SEAT_LOCK_PREFIX + seatId;
        String value = String.valueOf(userId);

        try {
            // Lua Script를 사용한 원자적 락 해제
            // GET + DELETE를 원자적으로 수행하여 소유권 검증
            Long result = redisTemplate.execute(
                    releaseLockScript,
                    Collections.singletonList(key),
                    value
            );

            if (result != null && result == 1L) {
                log.debug("Seat lock released: seatId={}, userId={}", seatId, userId);
            } else {
                log.warn("Failed to release seat lock (lock not owned or already released): seatId={}, userId={}",
                        seatId, userId);
            }

        } catch (Exception e) {
            log.error("Error releasing seat lock: seatId={}, userId={}", seatId, userId, e);
        }
    }

    @Override
    public boolean isLocked(Long seatId) {
        String key = SEAT_LOCK_PREFIX + seatId;
        Boolean exists = redisTemplate.hasKey(key);

        return Boolean.TRUE.equals(exists);
    }
}
