package personal.ai.queue.adapter.in.web.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import personal.ai.queue.application.config.QueueConfigProperties;

import java.io.IOException;
import java.util.Collections;

/**
 * Rate Limit Filter
 * Redis 기반 Token Bucket 알고리즘으로 SSE 구독 요청 제한
 * Lua Script를 사용하여 원자성 보장 (Race Condition 방지)
 *
 * Token Bucket 알고리즘:
 * - 버킷에 최대 N개의 토큰 저장, 일정 속도로 지속적으로 리필
 * - 요청마다 토큰 1개 소비, 부족 시 거부
 * - Burst 트래픽 방지 및 균등한 처리율 보장
 * - Polling 같은 지속적 요청과 대규모 트래픽에 최적
 *
 * 대규모 트래픽 대응:
 * - Fixed Window 대비 윈도우 경계 burst 방지
 * - 시간에 따른 점진적 토큰 리필로 안정적 처리
 */
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:queue:";
    private static final String SUBSCRIBE_PATH = "/api/v1/queue/subscribe";
    private final RedisTemplate<String, String> redisTemplate;
    private final QueueConfigProperties configProperties;
    private final DefaultRedisScript<Long> rateLimitScript;

    /**
     * 생성자: Lua Script 초기화
     * Rate Limiting을 위한 Lua Script를 로드하여 원자성을 보장
     * - 기존 방식(GET + CHECK + DECR)은 Race Condition 발생 가능
     * - Lua Script는 Redis에서 단일 스레드로 실행되어 원자성 보장
     */
    public RateLimitFilter(RedisTemplate<String, String> redisTemplate,
                           QueueConfigProperties configProperties) {
        this.redisTemplate = redisTemplate;
        this.configProperties = configProperties;

        // Lua Script 로드 및 초기화
        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setScriptSource(
                new ResourceScriptSource(new ClassPathResource("scripts/rate_limit_check.lua"))
        );
        this.rateLimitScript.setResultType(Long.class);

        log.info("RateLimitFilter initialized with Lua script for atomic operations");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        // SSE subscribe 엔드포인트만 Rate Limiting 적용
        if (!SUBSCRIBE_PATH.equals(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        String concertId = request.getParameter("concertId");
        String userId = request.getParameter("userId");

        if (concertId == null || userId == null) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
            response.getWriter().write("Missing concertId or userId parameter");
            return;
        }

        // Rate Limit 체크
        if (!checkRateLimit(concertId, userId)) {
            log.warn("Rate limit exceeded: concertId={}, userId={}", concertId, userId);
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());

            // Retry-After: 1개 토큰이 리필되는 시간 (초)
            QueueConfigProperties.Polling pollingConfig = configProperties.polling();
            double retryAfterSeconds = 1.0 / pollingConfig.rateLimitRefillRate();
            response.setHeader("Retry-After", String.valueOf((int) Math.ceil(retryAfterSeconds)));

            response.getWriter().write("Too many requests. Please slow down.");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Token Bucket 알고리즘으로 Rate Limit 체크 (Lua Script 사용 - 원자성 보장)
     *
     * Race Condition 방지:
     * - 기존: GET → 시간 계산 → 리필 → CHECK → DECR (5단계, Race Condition 발생)
     * - 개선: Lua Script로 모든 단계를 원자적으로 실행 (단일 연산)
     *
     * 동작 예시 (Capacity=10, Refill=5 tokens/sec):
     * - 00.0초 - 1st: tokens=10, 소비 → 9 (허용)
     * - 00.0초 - 2nd: tokens=9, 소비 → 8 (허용)
     * - 00.2초 - 3rd: 1토큰 리필(0.2*5), tokens=9, 소비 → 8 (허용)
     * - 버킷이 비어도 시간이 지나면 지속적으로 리필됨
     * - Burst 방지: 한 번에 최대 capacity만큼만 처리 가능
     *
     * @param concertId 콘서트 ID
     * @param userId 사용자 ID
     * @return true: 요청 허용, false: 요청 거부
     */
    private boolean checkRateLimit(String concertId, String userId) {
        String key = RATE_LIMIT_KEY_PREFIX + concertId + ":" + userId;
        QueueConfigProperties.Polling pollingConfig = configProperties.polling();

        try {
            // 현재 시간 (초 단위, 소수점 포함)
            double currentTime = System.currentTimeMillis() / 1000.0;

            // Lua Script 실행 (원자적 연산)
            // KEYS[1]: Rate limit key (예: "rate_limit:queue:CONCERT-001:USER-001")
            // ARGV[1]: Capacity (버킷 최대 용량)
            // ARGV[2]: Refill Rate (초당 리필 토큰 수)
            // ARGV[3]: Current Time (현재 시간, epoch seconds)
            // Return: 1 (허용) or 0 (거부)
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(key),
                    String.valueOf(pollingConfig.rateLimitCapacity()),
                    String.valueOf(pollingConfig.rateLimitRefillRate()),
                    String.valueOf(currentTime)
            );

            boolean allowed = result != null && result == 1L;

            if (allowed) {
                log.debug("Rate limit check passed: concertId={}, userId={}", concertId, userId);
            } else {
                log.debug("Rate limit exceeded: concertId={}, userId={}", concertId, userId);
            }

            return allowed;

        } catch (Exception e) {
            log.error("Rate limit check failed: concertId={}, userId={}", concertId, userId, e);
            // 에러 발생 시 안전하게 허용 (Fail-open 정책)
            // Redis 장애 시에도 서비스 가용성 유지
            return true;
        }
    }
}
