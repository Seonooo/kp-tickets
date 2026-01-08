package personal.ai.queue.adapter.out.redis;

/**
 * Redis Key 생성 유틸리티
 * 
 * Redis Cluster 호환:
 * - Hash Tag {concertId}를 사용하여 같은 콘서트의 모든 키가 같은 노드에 저장됨
 * - Lua Script Multi-Key 연산이 가능해짐
 * 
 * Convention: {prefix}:{hashTag}:{suffix}
 * Hash Tag: {concertId} 부분이 해시 계산에 사용됨
 */
public class RedisKeyGenerator {

    // Hash Tag 적용된 Prefix
    // {concertId}가 Hash Tag로 사용됨
    private static final String WAIT_QUEUE_FORMAT = "queue:wait:{%s}";
    private static final String ACTIVE_QUEUE_FORMAT = "queue:active:{%s}";
    private static final String ACTIVE_TOKEN_FORMAT = "active:token:{%s}:%s";
    private static final String STATS_TOTAL_WAITING_FORMAT = "stats:totalWaiting:{%s}";

    // 패턴 매칭용 Prefix (SCAN 용)
    private static final String WAIT_QUEUE_PREFIX = "queue:wait:";
    private static final String ACTIVE_QUEUE_PREFIX = "queue:active:";
    private static final String ACTIVE_TOKEN_PREFIX = "active:token:";

    /**
     * Wait Queue Key (Redis Cluster 호환)
     * queue:wait:{concertId}
     * 
     * Hash Tag: {concertId}
     */
    public static String waitQueueKey(String concertId) {
        return String.format(WAIT_QUEUE_FORMAT, concertId);
    }

    /**
     * Active Queue Key (Redis Cluster 호환)
     * queue:active:{concertId}
     * 
     * Hash Tag: {concertId}
     */
    public static String activeQueueKey(String concertId) {
        return String.format(ACTIVE_QUEUE_FORMAT, concertId);
    }

    /**
     * Active Token Key (Redis Cluster 호환)
     * active:token:{concertId}:userId
     * 
     * Hash Tag: {concertId}
     * 같은 콘서트의 Wait Queue, Active Queue, Token이 모두 같은 노드에 저장됨
     */
    public static String activeTokenKey(String concertId, String userId) {
        return String.format(ACTIVE_TOKEN_FORMAT, concertId, userId);
    }

    /**
     * Active Token Prefix (Lua Script용)
     * Lua Script에서 토큰 키 생성 시 사용
     */
    public static String activeTokenPrefix() {
        return ACTIVE_TOKEN_PREFIX;
    }

    /**
     * Total Waiting Cache Key (Redis Cluster 호환)
     * stats:totalWaiting:{concertId}
     *
     * Hash Tag: {concertId}
     * Quick Win 최적화: 전체 대기 인원을 캐싱하여 ZCARD 호출 빈도 감소
     */
    public static String totalWaitingCacheKey(String concertId) {
        return String.format(STATS_TOTAL_WAITING_FORMAT, concertId);
    }

    /**
     * Wait Queue 패턴 (모든 콘서트)
     * queue:wait:{*}
     */
    public static String waitQueuePattern() {
        return WAIT_QUEUE_PREFIX + "{*}";
    }

    /**
     * Active Queue 패턴 (모든 콘서트)
     * queue:active:{*}
     */
    public static String activeQueuePattern() {
        return ACTIVE_QUEUE_PREFIX + "{*}";
    }

    /**
     * Key에서 Concert ID 추출
     * Hash Tag 형식 고려: queue:wait:{concertId} → concertId
     */
    public static String extractConcertId(String key, String prefix) {
        if (key.startsWith(prefix)) {
            String remaining = key.substring(prefix.length());
            // Hash Tag 제거: {concertId} → concertId
            if (remaining.startsWith("{") && remaining.contains("}")) {
                return remaining.substring(1, remaining.indexOf("}"));
            }
            return remaining;
        }
        throw new IllegalArgumentException("Invalid key format: " + key);
    }
}
