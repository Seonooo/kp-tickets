package personal.ai.queue.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import personal.ai.queue.domain.model.QueueStatus;

import java.time.Instant;
import java.util.List;

/**
 * Redis Lua 스크립트 실행을 캡슐화하는 실행자
 * 모든 원자적 Redis 작업을 Lua 스크립트로 처리합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisLuaScriptExecutor {

    private static final String INITIAL_EXTEND_COUNT = "0";
    private static final double MIN_SCORE = 0.0;
    private static final String ACTIVE_TOKEN_PREFIX = "active:token:";

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<Long> addToActiveQueueScript;
    private final RedisScript<Long> removeExpiredTokensScript;
    private final RedisScript<Long> updateTokenExpirationScript;
    private final RedisScript<Long> removeFromActiveQueueScript;
    private final RedisScript<String> moveToActiveQueueScript;
    private final RedisScript<Long> activateTokenScript;

    /**
     * Active Queue에 토큰을 추가합니다 (원자적 작업).
     *
     * @param activeQueueKey Active Queue의 Redis 키
     * @param tokenKey 토큰의 Redis 키
     * @param userId 사용자 ID
     * @param token 토큰 값
     * @param expiredAt 만료 시각
     * @param ttlSeconds TTL (초)
     * @return 성공 여부 (1: 성공, 0: 실패)
     */
    public boolean executeAddToActiveQueue(
            String activeQueueKey,
            String tokenKey,
            String userId,
            String token,
            Instant expiredAt,
            long ttlSeconds) {

        String score = String.valueOf(expiredAt.getEpochSecond());
        String status = QueueStatus.READY.name();
        String expiredAtStr = String.valueOf(expiredAt.getEpochSecond());

        Long result = redisTemplate.execute(
                addToActiveQueueScript,
                List.of(activeQueueKey, tokenKey),
                userId, score, token, status, INITIAL_EXTEND_COUNT, expiredAtStr, String.valueOf(ttlSeconds)
        );

        boolean success = result != null && result == 1L;
        if (success) {
            log.debug("Executed addToActiveQueue script: userId={}, token={}", userId, token);
        } else {
            log.warn("Failed to add to active queue: userId={}, result={}", userId, result);
        }

        return success;
    }

    /**
     * 만료된 토큰들을 제거합니다 (원자적 작업).
     *
     * @param activeQueueKey Active Queue의 Redis 키
     * @param concertId 콘서트 ID
     * @return 제거된 토큰 수
     */
    public Long executeRemoveExpiredTokens(String activeQueueKey, String concertId) {
        long now = Instant.now().getEpochSecond();

        Long removedCount = redisTemplate.execute(
                removeExpiredTokensScript,
                List.of(activeQueueKey),
                String.valueOf(MIN_SCORE),
                String.valueOf(now),
                ACTIVE_TOKEN_PREFIX,
                concertId
        );

        if (removedCount != null && removedCount > 0) {
            log.debug("Executed removeExpiredTokens script: concertId={}, removed={}", concertId, removedCount);
        }

        return removedCount != null ? removedCount : 0L;
    }

    /**
     * 토큰의 만료 시간을 업데이트합니다 (원자적 작업).
     *
     * @param activeQueueKey Active Queue의 Redis 키
     * @param tokenKey 토큰의 Redis 키
     * @param userId 사용자 ID
     * @param expiredAt 새로운 만료 시각
     * @param ttlSeconds TTL (초)
     * @return 성공 여부 (1: 성공, 0: 실패)
     */
    public boolean executeUpdateTokenExpiration(
            String activeQueueKey,
            String tokenKey,
            String userId,
            Instant expiredAt,
            long ttlSeconds) {

        Long result = redisTemplate.execute(
                updateTokenExpirationScript,
                List.of(activeQueueKey, tokenKey),
                userId,
                String.valueOf(expiredAt.getEpochSecond()),
                String.valueOf(ttlSeconds)
        );

        boolean success = result != null && result == 1L;
        if (success) {
            log.debug("Executed updateTokenExpiration script: userId={}, expiredAt={}", userId, expiredAt);
        } else {
            log.warn("Failed to execute updateTokenExpiration script");
            if (log.isDebugEnabled()) {
                log.debug("Update token expiration script failed: userId={}", userId);
            }
        }

        return success;
    }

    /**
     * Active Queue에서 토큰을 제거합니다 (원자적 작업).
     *
     * @param activeQueueKey Active Queue의 Redis 키
     * @param tokenKey 토큰의 Redis 키
     * @param userId 사용자 ID
     * @return 성공 여부 (1: 성공, 0: 실패)
     */
    public boolean executeRemoveFromActiveQueue(String activeQueueKey, String tokenKey, String userId) {
        Long result = redisTemplate.execute(
                removeFromActiveQueueScript,
                List.of(activeQueueKey, tokenKey),
                userId
        );

        boolean success = result != null && result == 1L;
        if (success) {
            log.debug("Executed removeFromActiveQueue script: userId={}", userId);
        }

        return success;
    }

    /**
     * Wait Queue에서 Active Queue로 토큰들을 이동합니다 (원자적 작업).
     *
     * @param waitQueueKey Wait Queue의 Redis 키
     * @param activeQueueKey Active Queue의 Redis 키
     * @param concertId 콘서트 ID
     * @param count 이동할 개수
     * @param expiredAt 만료 시각
     * @param ttlSeconds TTL (초)
     * @return 이동된 사용자 ID들의 JSON 배열 문자열
     */
    public String executeMoveToActiveQueue(
            String waitQueueKey,
            String activeQueueKey,
            String concertId,
            int count,
            Instant expiredAt,
            long ttlSeconds) {

        String jsonResult = redisTemplate.execute(
                moveToActiveQueueScript,
                List.of(waitQueueKey, activeQueueKey),
                String.valueOf(count),
                String.valueOf(expiredAt.getEpochSecond()),
                ACTIVE_TOKEN_PREFIX,
                concertId,
                String.valueOf(ttlSeconds)
        );

        if (jsonResult != null && !jsonResult.isEmpty() && !jsonResult.equals("[]")) {
            log.debug("Executed moveToActiveQueue script: concertId={}, result={}", concertId, jsonResult);
        }

        return jsonResult;
    }

    /**
     * 토큰을 활성화합니다 (원자적 작업).
     *
     * @param activeQueueKey Active Queue의 Redis 키
     * @param tokenKey 토큰의 Redis 키
     * @param userId 사용자 ID
     * @param newExpiredAt 새로운 만료 시각
     * @param ttlSeconds TTL (초)
     * @return 성공 여부 (true: 성공 또는 이미 활성화됨, false: 실패)
     */
    public boolean executeActivateToken(
            String activeQueueKey,
            String tokenKey,
            String userId,
            Instant newExpiredAt,
            long ttlSeconds) {

        Long result = redisTemplate.execute(
                activateTokenScript,
                List.of(activeQueueKey, tokenKey),
                userId,
                String.valueOf(newExpiredAt.getEpochSecond()),
                String.valueOf(ttlSeconds)
        );

        if (result != null && result == 1L) {
            log.debug("Executed activateToken script: userId={} (activated)", userId);
            return true;
        } else if (result != null && result == -1L) {
            log.debug("Executed activateToken script: userId={} (already active)", userId);
            return true;
        } else {
            log.warn("Failed to execute activateToken script");
            if (log.isDebugEnabled()) {
                log.debug("Activate token script failed: userId={}", userId);
            }
            return false;
        }
    }
}