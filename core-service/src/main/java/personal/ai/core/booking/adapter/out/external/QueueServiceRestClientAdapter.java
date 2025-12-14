package personal.ai.core.booking.adapter.out.external;

import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import personal.ai.core.booking.application.port.out.QueueServiceClient;
import personal.ai.core.booking.domain.exception.QueueServiceUnavailableException;
import personal.ai.core.booking.domain.exception.QueueTokenInvalidException;

import java.util.Map;

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
     * - 4xx 에러: QueueTokenInvalidException → ignoreExceptions → Circuit 열지 않음
     * - 5xx 에러, Timeout: 기본 예외 전파 → Circuit 실패로 카운트
     * - Bulkhead: 내부 리소스 보호 (최대 100개 동시 호출)
     * - Retry: 안전한 재시도만 허용 (Connection 실패, 502)
     */
    @Override
    @CircuitBreaker(name = "queueService", fallbackMethod = "validateTokenFallback")
    @Bulkhead(name = "queueService", fallbackMethod = "validateTokenFallback", type = Bulkhead.Type.SEMAPHORE)
    @Retry(name = "queueService")
    public void validateToken(String concertId, Long userId, String queueToken) {
        log.debug("Validating queue token: concertId={}, userId={}, token={}", concertId, userId, queueToken);

        // Queue Service expects POST with JSON body
        Map<String, Object> requestBody = Map.of(
                "concertId", concertId,
                "userId", String.valueOf(userId),
                "token", queueToken);

        queueServiceRestClient.post()
                .uri("/api/v1/queue/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    log.warn("Queue token validation failed: status={}", response.getStatusCode());
                    throw new QueueTokenInvalidException();
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    log.error("Queue service unavailable: status={}", response.getStatusCode());
                    throw new QueueServiceUnavailableException();
                })
                .toBodilessEntity();

        log.debug("Queue token validated successfully: userId={}", userId);
    }

    /**
     * Fallback 메서드
     * Circuit Breaker Open 또는 Bulkhead Full 시 호출
     *
     * SLO Decision: Fairness(100%) > Availability(99.9%)
     * - 503 반환은 "장애"가 아니라 "공정성을 지키기 위한 정책적 결정"
     * - 대기열 없이 통과시키면 수천 명이 공정성을 위반하게 됨
     */
    private void validateTokenFallback(String concertId, Long userId, String queueToken, Exception e) {
        log.error("Queue service circuit breaker opened or bulkhead full: concertId={}, userId={}, error={}",
                concertId, userId, e.getClass().getSimpleName(), e);

        throw new QueueServiceUnavailableException();
    }
}
