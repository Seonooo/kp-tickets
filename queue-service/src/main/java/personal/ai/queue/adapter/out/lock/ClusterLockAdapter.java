package personal.ai.queue.adapter.out.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import personal.ai.queue.application.port.out.SchedulerLockPort;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Cluster Lock Adapter
 * Redis Cluster 환경에서 콘서트별 분산 락을 사용하는 어댑터
 *
 * 특징:
 * - 콘서트별로 락을 획득하여 중복 실행 방지
 * - Hash Tag를 사용하여 같은 콘서트의 키가 같은 노드에 저장되도록 보장
 * - Redis Cluster에서 다른 콘서트는 다른 노드에서 병렬 처리
 *
 * 사용 환경:
 * - 운영 환경 (다중 인스턴스 + Redis Cluster)
 */
@Slf4j
@RequiredArgsConstructor
public class ClusterLockAdapter implements SchedulerLockPort {

    private final StringRedisTemplate redisTemplate;
    private final Duration lockTtl;

    // 인스턴스 고유 ID (락 소유자 식별용)
    private final String instanceId = UUID.randomUUID().toString();

    // 락 해제 Lua Script (본인 소유인 경우만 삭제)
    private static final String UNLOCK_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """;

    @Override
    public boolean tryAcquire(String schedulerName, String concertId) {
        String lockKey = buildLockKey(schedulerName, concertId);

        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, instanceId, lockTtl);

            boolean result = Boolean.TRUE.equals(acquired);

            if (result) {
                log.debug("[ClusterLock] Lock acquired: key={}, instanceId={}", lockKey, instanceId);
            } else {
                log.debug("[ClusterLock] Lock not acquired (already held): key={}", lockKey);
            }

            return result;
        } catch (Exception e) {
            log.error("[ClusterLock] Failed to acquire lock: key={}", lockKey, e);
            return false; // 락 획득 실패 시 실행하지 않음 (안전한 방향)
        }
    }

    @Override
    public void release(String schedulerName, String concertId) {
        String lockKey = buildLockKey(schedulerName, concertId);

        try {
            Long released = redisTemplate.execute(
                    new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class),
                    List.of(lockKey),
                    instanceId);

            if (released != null && released > 0) {
                log.debug("[ClusterLock] Lock released: key={}", lockKey);
            } else {
                log.debug("[ClusterLock] Lock not released (not owner or expired): key={}", lockKey);
            }
        } catch (Exception e) {
            log.error("[ClusterLock] Failed to release lock: key={}", lockKey, e);
            // 락 해제 실패 시 TTL에 의해 자동 해제되므로 예외를 던지지 않음
        }
    }

    @Override
    public String getStrategyName() {
        return "cluster";
    }

    /**
     * 락 키 생성
     * Hash Tag {concertId}를 사용하여 같은 콘서트의 락이 같은 노드에 저장되도록 함
     */
    private String buildLockKey(String schedulerName, String concertId) {
        // Hash Tag 적용: {concertId}가 해시 계산에 사용됨
        return String.format("scheduler:lock:%s:{%s}", schedulerName, concertId);
    }
}
