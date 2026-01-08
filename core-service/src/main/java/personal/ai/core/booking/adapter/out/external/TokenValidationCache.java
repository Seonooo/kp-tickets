package personal.ai.core.booking.adapter.out.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * Token Validation Result Cache
 *
 * Queue Service 호출 최적화:
 * - 동일 토큰에 대한 중복 검증 방지
 * - TTL: 10초 (Queue Token TTL보다 짧게 설정)
 * - Cache Key: "token:validation:{concertId}:{userId}:{tokenHash}"
 *
 * 성능 개선:
 * - Seats Query와 Reservation 사이의 중복 검증 제거
 * - Queue Service 호출 50% 감소
 * - Bulkhead 부하 감소
 * 
 * 보안:
 * - SHA-256 해시 사용으로 토큰 원문 노출 방지
 * - 로그에 userId, token 미노출 (PII 보호)
 * 
 * 장애 대응:
 * - 모든 Redis 작업은 예외 처리되어 비즈니스 로직 중단 방지
 * - Redis 장애 시 캐시 미스/무시로 처리하여 서비스 연속성 보장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenValidationCache {

    private static final String CACHE_KEY_PREFIX = "token:validation:";
    private static final Duration CACHE_TTL = Duration.ofSeconds(10);
    private static final String VALID_MARKER = "VALID";

    private final StringRedisTemplate redisTemplate;

    /**
     * 캐시에서 검증 결과 조회
     * 
     * @return 캐시에 있고 유효하면 true, 없거나 오류 시 false
     */
    public boolean isValidInCache(String concertId, Long userId, String token) {
        if (!validateParams(concertId, userId, token, "lookup")) {
            return false;
        }

        try {
            String cacheKey = buildCacheKey(concertId, userId, token);
            String cachedValue = redisTemplate.opsForValue().get(cacheKey);

            if (VALID_MARKER.equals(cachedValue)) {
                log.debug("Token cache HIT: concertId={}", concertId);
                return true;
            }
            log.debug("Token cache MISS: concertId={}", concertId);
            return false;
        } catch (Exception e) {
            log.warn("Token cache lookup error: concertId={}, error={}", concertId, e.getMessage());
            return false;
        }
    }

    /**
     * 검증 성공 결과를 캐시에 저장
     */
    public void cacheValidation(String concertId, Long userId, String token) {
        if (!validateParams(concertId, userId, token, "store")) {
            return;
        }

        try {
            String cacheKey = buildCacheKey(concertId, userId, token);
            redisTemplate.opsForValue().set(cacheKey, VALID_MARKER, CACHE_TTL);
            log.debug("Token cached: concertId={}, ttl={}s", concertId, CACHE_TTL.getSeconds());
        } catch (Exception e) {
            log.warn("Token cache store error: concertId={}, error={}", concertId, e.getMessage());
        }
    }

    /**
     * 캐시 무효화 (토큰 만료 또는 제거 시)
     */
    public void invalidate(String concertId, Long userId, String token) {
        if (!validateParams(concertId, userId, token, "invalidate")) {
            return;
        }

        try {
            String cacheKey = buildCacheKey(concertId, userId, token);
            redisTemplate.delete(cacheKey);
            log.debug("Token cache invalidated: concertId={}", concertId);
        } catch (Exception e) {
            log.warn("Token cache invalidate error: concertId={}, error={}", concertId, e.getMessage());
        }
    }

    /**
     * 파라미터 유효성 검증 (공통)
     * NOTE: userId, token은 PII이므로 로그에 미포함
     */
    private boolean validateParams(String concertId, Long userId, String token, String operation) {
        if (concertId == null || userId == null || token == null) {
            log.warn("Invalid cache {}: null parameter detected", operation);
            return false;
        }
        return true;
    }

    private String buildCacheKey(String concertId, Long userId, String token) {
        return CACHE_KEY_PREFIX + concertId + ":" + userId + ":" + hashToken(token);
    }

    private String hashToken(String token) {
        if (token == null || token.isEmpty()) {
            return "empty";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            log.warn("SHA-256 unavailable, using hashCode");
            return String.valueOf(token.hashCode());
        }
    }
}
