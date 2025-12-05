package personal.ai.queue.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;
import personal.ai.queue.application.port.out.QueueRepository;
import personal.ai.queue.domain.model.QueueStatus;
import personal.ai.queue.domain.model.QueueToken;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Redis Queue Adapter (Output Port 구현)
 * Hybrid Queue: ZSet(순서 보장) + Hash(O(1) 검증)
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class RedisQueueAdapter implements QueueRepository {

    // Hash Field Names
    private static final String FIELD_TOKEN = "token";
    private static final String FIELD_STATUS = "status";
    private static final String FIELD_EXTEND_COUNT = "extend_count";
    private static final String FIELD_EXPIRED_AT = "expired_at";
    // Redis 상수
    private static final String INITIAL_EXTEND_COUNT = "0";
    private static final long TTL_BUFFER_SECONDS = 60L; // TTL 버퍼 (1분)
    private static final int SCAN_COUNT = 100; // SCAN 명령어 배치 크기
    private static final long INCREMENT_VALUE = 1L; // HINCRBY 증가값
    private static final double MIN_SCORE = 0.0; // ZSet 최소 스코어
    private final RedisTemplate<String, String> redisTemplate;
    private final org.springframework.data.redis.core.script.RedisScript<Long> addToActiveQueueScript;
    private final org.springframework.data.redis.core.script.RedisScript<Long> removeExpiredTokensScript;
    private final org.springframework.data.redis.core.script.RedisScript<Long> updateTokenExpirationScript;
    private final org.springframework.data.redis.core.script.RedisScript<Long> removeFromActiveQueueScript;
    private final org.springframework.data.redis.core.script.RedisScript<String> moveToActiveQueueScript;
    private final org.springframework.data.redis.core.script.RedisScript<Long> activateTokenScript;

    @Override
    public Long addToWaitQueue(String concertId, String userId) {
        String key = RedisKeyGenerator.waitQueueKey(concertId);
        double score = System.currentTimeMillis();

        // ZADD NX: 이미 존재하면 추가하지 않음 (중복 진입 방지)
        Boolean added = redisTemplate.opsForZSet().addIfAbsent(key, userId, score);

        log.debug("Added to wait queue: concertId={}, userId={}, added={}",
                concertId, userId, added);

        // 순번 조회 (0-based)
        return redisTemplate.opsForZSet().rank(key, userId);
    }

    @Override
    public Long getWaitQueuePosition(String concertId, String userId) {
        String key = RedisKeyGenerator.waitQueueKey(concertId);
        return redisTemplate.opsForZSet().rank(key, userId);
    }

    @Override
    public Long getWaitQueueSize(String concertId) {
        String key = RedisKeyGenerator.waitQueueKey(concertId);
        return redisTemplate.opsForZSet().size(key);
    }

    @Override
    public List<String> popFromWaitQueue(String concertId, int count) {
        String key = RedisKeyGenerator.waitQueueKey(concertId);

        // ZPOPMIN: score가 가장 낮은(먼저 들어온) N개를 Pop
        Set<ZSetOperations.TypedTuple<String>> popped = redisTemplate.opsForZSet().popMin(key, count);

        if (popped == null || popped.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> userIds = popped.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        log.debug("Popped from wait queue: concertId={}, count={}, actual={}",
                concertId, count, userIds.size());

        return userIds;
    }

    @Override
    public void addToActiveQueue(String concertId, String userId, String token, Instant expiredAt) {
        // Lua Script를 사용하여 Atomicity 보장
        // KEYS[1]: Active Queue Key (ZSet)
        // KEYS[2]: Token Key (Hash)
        String queueKey = RedisKeyGenerator.activeQueueKey(concertId);
        String tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);

        // ARGV 파라미터 준비
        String score = String.valueOf(expiredAt.getEpochSecond());
        String status = QueueStatus.READY.name();
        String extendCount = INITIAL_EXTEND_COUNT;
        String expiredAtStr = String.valueOf(expiredAt.getEpochSecond());

        // TTL 계산
        long ttlSeconds = expiredAt.getEpochSecond() - Instant.now().getEpochSecond() + TTL_BUFFER_SECONDS;
        String ttl = String.valueOf(ttlSeconds > 0 ? ttlSeconds : 0);

        redisTemplate.execute(addToActiveQueueScript,
                List.of(queueKey, tokenKey),
                userId, score, token, status, extendCount, expiredAtStr, ttl);

        log.debug("Added to active queue (Lua): concertId={}, userId={}, token={}, expiredAt={}",
                concertId, userId, token, expiredAt);
    }

    @Override
    public Optional<QueueToken> getActiveToken(String concertId, String userId) {
        String tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);

        Map<Object, Object> tokenData = redisTemplate.opsForHash().entries(tokenKey);

        if (tokenData.isEmpty()) {
            return Optional.empty();
        }

        try {
            String token = (String) tokenData.get(FIELD_TOKEN);
            String statusStr = (String) tokenData.get(FIELD_STATUS);
            String extendCountStr = (String) tokenData.get(FIELD_EXTEND_COUNT);
            String expiredAtStr = (String) tokenData.get(FIELD_EXPIRED_AT);

            QueueStatus status = QueueStatus.valueOf(statusStr);
            Integer extendCount = Integer.parseInt(extendCountStr);
            Instant expiredAt = Instant.ofEpochSecond(Long.parseLong(expiredAtStr));

            QueueToken queueToken = switch (status) {
                case READY -> QueueToken.ready(concertId, userId, token, expiredAt);
                case ACTIVE -> QueueToken.active(concertId, userId, token, expiredAt, extendCount);
                default -> QueueToken.notFound(concertId, userId);
            };

            return Optional.of(queueToken);

        } catch (Exception e) {
            log.error("Failed to parse token data: concertId={}, userId={}",
                    concertId, userId, e);
            return Optional.empty();
        }
    }

    @Override
    public void updateTokenExpiration(String concertId, String userId, Instant expiredAt) {
        String queueKey = RedisKeyGenerator.activeQueueKey(concertId);
        String tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);

        // TTL 계산
        long ttlSeconds = expiredAt.getEpochSecond() - Instant.now().getEpochSecond() + TTL_BUFFER_SECONDS;

        // Lua Script 실행 (원자적 연산)
        // KEYS[1]: Active Queue Key (ZSet)
        // KEYS[2]: Token Key (Hash)
        // ARGV[1]: User ID
        // ARGV[2]: New Expiration Time (epoch seconds)
        // ARGV[3]: TTL (seconds)
        Long result = redisTemplate.execute(updateTokenExpirationScript,
                List.of(queueKey, tokenKey),
                userId,
                String.valueOf(expiredAt.getEpochSecond()),
                String.valueOf(ttlSeconds > 0 ? ttlSeconds : 0));

        if (result != null && result == 1L) {
            log.debug("Updated token expiration (Lua): concertId={}, userId={}, expiredAt={}",
                    concertId, userId, expiredAt);
        } else {
            log.warn("Failed to update token expiration: concertId={}, userId={} (token not found)",
                    concertId, userId);
        }
    }

    @Override
    public void updateTokenStatus(String concertId, String userId, QueueStatus status) {
        String tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);

        // 상태만 변경 (TTL은 기존 값 유지)
        // TTL 변경이 필요한 경우 updateTokenExpiration()을 별도로 호출
        redisTemplate.opsForHash().put(tokenKey, FIELD_STATUS, status.name());

        log.debug("Updated token status: concertId={}, userId={}, status={}",
                concertId, userId, status);
    }

    @Override
    public Integer incrementExtendCount(String concertId, String userId) {
        String tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);

        // HINCRBY: Atomic increment
        // Note: TTL 갱신은 updateTokenExpiration()에서 처리됨
        //       이 메서드는 연장 횟수 카운팅만 담당
        Long newCount = redisTemplate.opsForHash().increment(tokenKey, FIELD_EXTEND_COUNT, INCREMENT_VALUE);

        log.debug("Incremented extend count: concertId={}, userId={}, count={}",
                concertId, userId, newCount);

        return newCount.intValue();
    }

    @Override
    public Long getActiveQueueSize(String concertId) {
        String key = RedisKeyGenerator.activeQueueKey(concertId);
        return redisTemplate.opsForZSet().size(key);
    }

    @Override
    public Long removeExpiredTokens(String concertId) {
        String queueKey = RedisKeyGenerator.activeQueueKey(concertId);
        long now = Instant.now().getEpochSecond();

        // Lua Script 실행
        // KEYS[1]: Active Queue Key (ZSet)
        // ARGV[1]: Min Score (0)
        // ARGV[2]: Max Score (Current Time)
        // ARGV[3]: Token Key Prefix
        // ARGV[4]: ConcertId

        Long removedCount = redisTemplate.execute(removeExpiredTokensScript,
                List.of(queueKey),
                String.valueOf(MIN_SCORE),
                String.valueOf(now),
                "active:token:",  // RedisKeyGenerator.ACTIVE_TOKEN_PREFIX와 일치
                concertId);

        if (removedCount != null && removedCount > 0) {
            log.debug("Removed expired tokens (Lua): concertId={}, count={}", concertId, removedCount);
        }

        return removedCount != null ? removedCount : 0L;
    }

    @Override
    public void removeFromActiveQueue(String concertId, String userId) {
        String queueKey = RedisKeyGenerator.activeQueueKey(concertId);
        String tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);

        // Lua Script 실행 (원자적 연산)
        // KEYS[1]: Active Queue Key (ZSet)
        // KEYS[2]: Token Key (Hash)
        // ARGV[1]: User ID
        Long result = redisTemplate.execute(removeFromActiveQueueScript,
                List.of(queueKey, tokenKey),
                userId);

        if (result != null && result == 1L) {
            log.debug("Removed from active queue (Lua): concertId={}, userId={}", concertId, userId);
        } else {
            log.debug("No data to remove from active queue: concertId={}, userId={}", concertId, userId);
        }
    }

    @Override
    public void removeFromWaitQueue(String concertId, String userId) {
        String key = RedisKeyGenerator.waitQueueKey(concertId);
        redisTemplate.opsForZSet().remove(key, userId);

        log.debug("Removed from wait queue: concertId={}, userId={}", concertId, userId);
    }

    @Override
    public List<String> moveToActiveQueueAtomic(String concertId, int count, Instant expiredAt) {
        String waitQueueKey = RedisKeyGenerator.waitQueueKey(concertId);
        String activeQueueKey = RedisKeyGenerator.activeQueueKey(concertId);

        // TTL 계산
        long ttlSeconds = expiredAt.getEpochSecond() - Instant.now().getEpochSecond() + TTL_BUFFER_SECONDS;

        // Lua Script 실행 (원자적 연산)
        // KEYS[1]: Wait Queue Key (ZSet)
        // KEYS[2]: Active Queue Key (ZSet)
        // ARGV[1]: Batch Size
        // ARGV[2]: Expiration Time (epoch seconds)
        // ARGV[3]: Token Key Prefix
        // ARGV[4]: Concert ID
        // ARGV[5]: TTL (seconds)
        String jsonResult = redisTemplate.execute(moveToActiveQueueScript,
                List.of(waitQueueKey, activeQueueKey),
                String.valueOf(count),
                String.valueOf(expiredAt.getEpochSecond()),
                "active:token:",
                concertId,
                String.valueOf(ttlSeconds > 0 ? ttlSeconds : 0));

        if (jsonResult == null || jsonResult.isEmpty() || jsonResult.equals("[]")) {
            log.debug("No users moved (Lua): concertId={}", concertId);
            return Collections.emptyList();
        }

        try {
            // JSON 파싱 (간단한 배열 파싱)
            // ["USER-001","USER-002"] 형태
            String[] userIds = jsonResult
                    .replace("[", "")
                    .replace("]", "")
                    .replace("\"", "")
                    .split(",");

            List<String> result = Arrays.stream(userIds)
                    .filter(id -> !id.trim().isEmpty())
                    .collect(Collectors.toList());

            log.debug("Moved users atomically (Lua): concertId={}, count={}", concertId, result.size());
            return result;

        } catch (Exception e) {
            log.error("Failed to parse Lua script result: concertId={}, result={}",
                    concertId, jsonResult, e);
            return Collections.emptyList();
        }
    }

    @Override
    public boolean activateTokenAtomic(String concertId, String userId, Instant newExpiredAt) {
        String queueKey = RedisKeyGenerator.activeQueueKey(concertId);
        String tokenKey = RedisKeyGenerator.activeTokenKey(concertId, userId);

        // TTL 계산
        long ttlSeconds = newExpiredAt.getEpochSecond() - Instant.now().getEpochSecond() + TTL_BUFFER_SECONDS;

        // Lua Script 실행 (원자적 연산)
        // KEYS[1]: Active Queue Key (ZSet)
        // KEYS[2]: Token Key (Hash)
        // ARGV[1]: User ID
        // ARGV[2]: New Expiration Time (epoch seconds)
        // ARGV[3]: TTL (seconds)
        // Return: 1 (성공), 0 (실패), -1 (이미 ACTIVE)
        Long result = redisTemplate.execute(activateTokenScript,
                List.of(queueKey, tokenKey),
                userId,
                String.valueOf(newExpiredAt.getEpochSecond()),
                String.valueOf(ttlSeconds > 0 ? ttlSeconds : 0));

        if (result != null && result == 1L) {
            log.debug("Token activated atomically (Lua): concertId={}, userId={}", concertId, userId);
            return true;
        } else if (result != null && result == -1L) {
            log.debug("Token already active: concertId={}, userId={}", concertId, userId);
            return true;  // 이미 ACTIVE도 성공으로 처리
        } else {
            log.warn("Failed to activate token: concertId={}, userId={} (token not found or invalid state)",
                    concertId, userId);
            return false;
        }
    }

    @Override
    public List<String> getActiveConcertIds() {
        Set<String> concertIds = new HashSet<>();

        // SCAN을 사용하여 안전하게 키 조회 (KEYS 명령어 금지)
        redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
            org.springframework.data.redis.core.Cursor<byte[]> waitCursor = null;
            org.springframework.data.redis.core.Cursor<byte[]> activeCursor = null;

            try {
                // Wait Queue 스캔
                org.springframework.data.redis.core.ScanOptions waitOptions = org.springframework.data.redis.core.ScanOptions
                        .scanOptions()
                        .match(RedisKeyGenerator.waitQueuePattern())
                        .count(SCAN_COUNT)
                        .build();

                waitCursor = connection.scan(waitOptions);

                while (waitCursor.hasNext()) {
                    String key = new String(waitCursor.next());
                    concertIds.add(RedisKeyGenerator.extractConcertId(key, "queue:wait:"));
                }

                // Active Queue 스캔
                org.springframework.data.redis.core.ScanOptions activeOptions = org.springframework.data.redis.core.ScanOptions
                        .scanOptions()
                        .match(RedisKeyGenerator.activeQueuePattern())
                        .count(SCAN_COUNT)
                        .build();

                activeCursor = connection.scan(activeOptions);

                while (activeCursor.hasNext()) {
                    String key = new String(activeCursor.next());
                    concertIds.add(RedisKeyGenerator.extractConcertId(key, "queue:active:"));
                }

            } finally {
                // Cursor 리소스 정리
                if (waitCursor != null) {
                    try {
                        waitCursor.close();
                    } catch (Exception e) {
                        log.warn("Failed to close wait queue cursor", e);
                    }
                }
                if (activeCursor != null) {
                    try {
                        activeCursor.close();
                    } catch (Exception e) {
                        log.warn("Failed to close active queue cursor", e);
                    }
                }
            }

            return null;
        });

        return new ArrayList<>(concertIds);
    }
}
