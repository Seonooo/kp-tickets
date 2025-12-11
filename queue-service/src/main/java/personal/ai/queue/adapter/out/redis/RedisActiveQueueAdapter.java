package personal.ai.queue.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import personal.ai.queue.domain.exception.QueueDataCorruptionException;
import personal.ai.queue.domain.model.QueueStatus;
import personal.ai.queue.domain.model.QueueToken;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Redis Active Queue 전담 어댑터
 * Active Queue 관련 작업만 담당합니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisActiveQueueAdapter {

    private static final String FIELD_STATUS = "status";
    private static final String FIELD_EXTEND_COUNT = "extend_count";
    private static final long INCREMENT_VALUE = 1L;

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTokenConverter tokenConverter;
    private final RedisLuaScriptExecutor luaScriptExecutor;
    private final RedisConcertIdScanner concertIdScanner;

    /**
     * Active Queue에 토큰을 추가합니다.
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @param token 토큰 값
     * @param expiredAt 만료 시각
     */
    public void addToActiveQueue(String concertId, String userId, String token, Instant expiredAt) {
        var activeQueueKey = RedisKeyGenerator.activeQueueKey(concertId);
        var tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);
        var ttlSeconds = tokenConverter.calculateRemainingTtlSeconds(expiredAt);

        boolean success = luaScriptExecutor.executeAddToActiveQueue(
                activeQueueKey,
                tokenKey,
                userId,
                token,
                expiredAt,
                ttlSeconds
        );

        if (!success) {
            log.warn("Failed to add to active queue: concertId={}, userId={}", concertId, userId);
        }

        log.debug("Added to active queue: concertId={}, userId={}, token={}", concertId, userId, token);
    }

    /**
     * Active Queue에서 토큰을 조회합니다.
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @return QueueToken (없으면 Optional.empty())
     */
    public Optional<QueueToken> getActiveToken(String concertId, String userId) {
        var tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);
        var redisHashData = redisTemplate.opsForHash().entries(tokenKey);

        if (redisHashData.isEmpty()) {
            return Optional.empty();
        }

        try {
            var token = tokenConverter.toQueueToken(redisHashData, concertId, userId);
            return Optional.of(token);
        } catch (Exception e) {
            // 데이터 손상: Redis에 데이터가 존재하지만 형식이 잘못됨
            log.error("Queue data corruption detected - Token data exists but format is invalid: " +
                "concertId={}, userId={}, data={}",
                concertId, userId, redisHashData, e);
            throw new QueueDataCorruptionException(e);
        }
    }

    /**
     * 토큰의 만료 시간을 업데이트합니다.
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @param expiredAt 새로운 만료 시각
     */
    public void updateTokenExpiration(String concertId, String userId, Instant expiredAt) {
        var activeQueueKey = RedisKeyGenerator.activeQueueKey(concertId);
        var tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);
        var ttlSeconds = tokenConverter.calculateRemainingTtlSeconds(expiredAt);

        var success = luaScriptExecutor.executeUpdateTokenExpiration(
                activeQueueKey,
                tokenKey,
                userId,
                expiredAt,
                ttlSeconds
        );

        if (success) {
            log.debug("Updated token expiration: concertId={}, userId={}, expiredAt={}",
                    concertId, userId, expiredAt);
        } else {
            log.warn("Failed to update token expiration");
            if (log.isDebugEnabled()) {
                log.debug("Token expiration update failed: concertId={}, userId={}", concertId, userId);
            }
        }
    }

    /**
     * 토큰의 상태를 업데이트합니다.
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @param status 새로운 상태
     */
    public void updateTokenStatus(String concertId, String userId, QueueStatus status) {
        var tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);
        redisTemplate.opsForHash().put(tokenKey, FIELD_STATUS, status.name());

        log.debug("Updated token status: concertId={}, userId={}, status={}", concertId, userId, status);
    }

    /**
     * 토큰의 연장 횟수를 1 증가시킵니다.
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @return 증가 후의 연장 횟수
     */
    public Integer incrementExtendCount(String concertId, String userId) {
        var tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);
        var newCount = redisTemplate.opsForHash().increment(tokenKey, FIELD_EXTEND_COUNT, INCREMENT_VALUE);

        log.debug("Incremented extend count: concertId={}, userId={}, count={}", concertId, userId, newCount);

        return newCount.intValue();
    }

    /**
     * Active Queue의 크기를 조회합니다.
     *
     * @param concertId 콘서트 ID
     * @return Active Queue에 있는 토큰 수
     */
    public Long getActiveQueueSize(String concertId) {
        var activeQueueKey = RedisKeyGenerator.activeQueueKey(concertId);
        return redisTemplate.opsForZSet().size(activeQueueKey);
    }

    /**
     * 만료된 토큰들을 제거합니다.
     *
     * @param concertId 콘서트 ID
     * @return 제거된 토큰 수
     */
    public Long removeExpiredTokens(String concertId) {
        var activeQueueKey = RedisKeyGenerator.activeQueueKey(concertId);
        var removedCount = luaScriptExecutor.executeRemoveExpiredTokens(activeQueueKey, concertId);

        if (removedCount > 0) {
            log.debug("Removed expired tokens: concertId={}, count={}", concertId, removedCount);
        }

        return removedCount;
    }

    /**
     * Active Queue에서 토큰을 제거합니다.
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     */
    public void removeFromActiveQueue(String concertId, String userId) {
        var activeQueueKey = RedisKeyGenerator.activeQueueKey(concertId);
        var tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);

        var success = luaScriptExecutor.executeRemoveFromActiveQueue(activeQueueKey, tokenKey, userId);

        if (success) {
            log.debug("Removed from active queue: concertId={}, userId={}", concertId, userId);
        } else {
            log.debug("No data to remove from active queue: concertId={}, userId={}", concertId, userId);
        }
    }

    /**
     * Wait Queue에서 Active Queue로 토큰들을 원자적으로 이동합니다.
     *
     * @param concertId 콘서트 ID
     * @param count 이동할 개수
     * @param expiredAt 만료 시각
     * @return 이동된 사용자 ID 리스트
     */
    public List<String> moveToActiveQueueAtomic(String concertId, int count, Instant expiredAt) {
        var waitQueueKey = RedisKeyGenerator.waitQueueKey(concertId);
        var activeQueueKey = RedisKeyGenerator.activeQueueKey(concertId);
        var ttlSeconds = tokenConverter.calculateRemainingTtlSeconds(expiredAt);

        var jsonResult = luaScriptExecutor.executeMoveToActiveQueue(
                waitQueueKey,
                activeQueueKey,
                concertId,
                count,
                expiredAt,
                ttlSeconds
        );

        if (jsonResult == null || jsonResult.isEmpty() || jsonResult.equals("[]")) {
            log.debug("No users moved: concertId={}", concertId);
            return List.of();
        }

        try {
            var movedUserIds = tokenConverter.parseUserIdsFromJson(jsonResult);
            log.debug("Moved users atomically: concertId={}, count={}", concertId, movedUserIds.size());
            return movedUserIds;
        } catch (Exception e) {
            // CRITICAL: Lua 스크립트는 성공했지만 결과 파싱 실패
            // 실제로 사용자들이 이동되었을 수 있으므로 데이터 불일치 상태
            log.error("CRITICAL: Queue data corruption - Lua script succeeded but result parsing failed. " +
                "Users may have been moved but cannot be tracked: concertId={}, jsonResult={}",
                concertId, jsonResult, e);
            throw new QueueDataCorruptionException(e);
        }
    }

    /**
     * 토큰을 원자적으로 활성화합니다.
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @param newExpiredAt 새로운 만료 시각
     * @return 성공 여부
     */
    public boolean activateTokenAtomic(String concertId, String userId, Instant newExpiredAt) {
        var activeQueueKey = RedisKeyGenerator.activeQueueKey(concertId);
        var tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);
        var ttlSeconds = tokenConverter.calculateRemainingTtlSeconds(newExpiredAt);

        var success = luaScriptExecutor.executeActivateToken(
                activeQueueKey,
                tokenKey,
                userId,
                newExpiredAt,
                ttlSeconds
        );

        if (success) {
            log.debug("Token activated atomically: concertId={}, userId={}", concertId, userId);
        } else {
            log.warn("Failed to activate token");
            if (log.isDebugEnabled()) {
                log.debug("Token activation failed: concertId={}, userId={}", concertId, userId);
            }
        }

        return success;
    }

    /**
     * Active Queue에 있는 콘서트 ID 목록을 조회합니다.
     *
     * @return Active Queue의 콘서트 ID 리스트
     */
    public List<String> getActiveQueueConcertIds() {
        return concertIdScanner.scanQueueConcertIds(
                RedisKeyGenerator.activeQueuePattern(),
                "queue:active:"
        );
    }
}