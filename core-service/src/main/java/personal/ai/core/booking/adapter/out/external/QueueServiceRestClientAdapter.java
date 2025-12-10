package personal.ai.core.booking.adapter.out.external;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;
import personal.ai.core.booking.application.port.out.QueueServiceClient;

/**
 * Queue Service REST Client Adapter
 * Queue Service와 HTTP 통신하는 구현체 (RestClient 사용)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueueServiceRestClientAdapter implements QueueServiceClient {

    private final RestClient queueServiceRestClient;

    /**
     * Queue 토큰 검증
     * Circuit Breaker, Bulkhead, Retry 패턴 적용
     * - Circuit Breaker: 장애 전파 차단 (Fail-Fast)
     *   - 4xx 에러: BusinessException → ignoreExceptions → Circuit 열지 않음
     *   - 5xx 에러, Timeout: 기본 예외 전파 → Circuit 실패로 카운트
     * - Bulkhead: 내부 리소스 보호 (최대 100개 동시 호출)
     * - Retry: 안전한 재시도만 허용 (Connection 실패, 502)
     */
    @Override
    @CircuitBreaker(name = "queueService", fallbackMethod = "validateTokenFallback")
    @Bulkhead(name = "queueService", fallbackMethod = "validateTokenFallback", type = Bulkhead.Type.SEMAPHORE)
    @Retry(name = "queueService")
    public void validateToken(Long userId, String queueToken) {
        log.debug("Validating queue token: userId={}, token={}", userId, queueToken);

        try {
            queueServiceRestClient.get()
                    .uri("/api/v1/queue/validate")
                    .header("X-Queue-Token", queueToken)
                    .header("X-User-Id", String.valueOf(userId))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                        log.warn("Queue token validation failed: userId={}, status={}", userId, response.getStatusCode());
                        throw new BusinessException(ErrorCode.QUEUE_TOKEN_INVALID, "유효하지 않은 대기열 토큰입니다.");
                    })
                    // 5xx 에러는 핸들러 제거 - 기본 예외가 발생하여 Circuit Breaker가 감지
                    .toBodilessEntity();

            log.debug("Queue token validated successfully: userId={}", userId);

        } catch (BusinessException e) {
            // 4xx 에러는 비즈니스 예외로 처리 (Circuit 열지 않음)
            throw e;
        }
        // 5xx, Timeout 등 시스템 에러는 그대로 전파 → Circuit Breaker 감지
    }

    /**
     * Fallback 메서드
     * Circuit Breaker Open 또는 Bulkhead Full 시 호출
     *
     * SLO Decision: Fairness(100%) > Availability(99.9%)
     * - 503 반환은 "장애"가 아니라 "공정성을 지키기 위한 정책적 결정"
     * - 대기열 없이 통과시키면 수천 명이 공정성을 위반하게 됨
     */
    private void validateTokenFallback(Long userId, String queueToken, Exception e) {
        log.error("Queue service circuit breaker opened or bulkhead full: userId={}, error={}",
                userId, e.getClass().getSimpleName(), e);

        throw new BusinessException(
                ErrorCode.QUEUE_SERVICE_UNAVAILABLE,
                "대기열 확인 지연 중입니다. 5초 후 다시 시도해주세요."
        );
    }
}
