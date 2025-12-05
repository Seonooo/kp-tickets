package personal.ai.core.booking.adapter.out.external;

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

    @Override
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
                    .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                        log.error("Queue service unavailable: status={}", response.getStatusCode());
                        throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "대기열 서비스 오류가 발생했습니다.");
                    })
                    .toBodilessEntity();

            log.debug("Queue token validated successfully: userId={}", userId);

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Queue service call failed: userId={}", userId, e);
            throw new BusinessException(ErrorCode.EXTERNAL_SERVICE_ERROR, "대기열 서비스 호출 중 오류가 발생했습니다.");
        }
    }
}
