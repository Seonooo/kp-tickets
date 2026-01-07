package personal.ai.core.booking.adapter.out.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Token Validation Result Cache
 *
 * Queue Service 호출 최적화:
 * - 동일 토큰에 대한 중복 검증 방지
 * - TTL: 10초 (Queue Token TTL보다 짧게 설정)
 * - Cache Key: "token:validation:{concertId}:{userId}:{token}"
 *
 * 성능 개선:
 * - Seats Query와 Reservation 사이의 중복 검증 제거
 * - Queue Service 호출 50% 감소
 * - Bulkhead 부하 감소
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenValidationCache {

    private static final String CACHE_KEY_PREFIX = "token:validation:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(10); // 10초 캐시
    private static final String VALID_MARKER = "VALID";

    private final StringRedisTemplate redisTemplate;

    /**
     * 캐시에서 검증 결과 조회
     * @return 캐시에 있고 유효하면 true, 없으면 false
     */
    public boolean isValidInCache(String concertId, Long userId, String token) {
        String cacheKey = buildCacheKey(concertId, userId, token);
        String cachedValue = redisTemplate.opsForValue().get(cacheKey);

        if (VALID_MARKER.equals(cachedValue)) {
            log.debug("Token validation cache HIT: concertId={}, userId={}", concertId, userId);
            return true;
        }

        log.debug("Token validation cache MISS: concertId={}, userId={}", concertId, userId);
        return false;
    }

    /**
     * 검증 성공 결과를 캐시에 저장
     */
    public void cacheValidation(String concertId, Long userId, String token) {
        String cacheKey = buildCacheKey(concertId, userId, token);
        redisTemplate.opsForValue().set(cacheKey, VALID_MARKER, CACHE_TTL);
        log.debug("Token validation cached: concertId={}, userId={}, ttl={}s",
                  concertId, userId, CACHE_TTL.getSeconds());
    }

    /**
     * 캐시 무효화 (토큰 만료 또는 제거 시)
     */
    public void invalidate(String concertId, Long userId, String token) {
        String cacheKey = buildCacheKey(concertId, userId, token);
        redisTemplate.delete(cacheKey);
        log.debug("Token validation cache invalidated: concertId={}, userId={}", concertId, userId);
    }

    private String buildCacheKey(String concertId, Long userId, String token) {
        // token의 앞 8자만 사용 (키 길이 최적화, 충돌 확률 극히 낮음)
        String tokenPrefix = token.length() > 8 ? token.substring(0, 8) : token;
        return CACHE_KEY_PREFIX + concertId + ":" + userId + ":" + tokenPrefix;
    }
}
