package personal.ai.core.booking.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Queue Service Unavailable Exception
 * Queue Service에 연결할 수 없을 때 발생 (Circuit Breaker Open, Bulkhead Full 등)
 */
public class QueueServiceUnavailableException extends BusinessException {
    public QueueServiceUnavailableException() {
        super(ErrorCode.QUEUE_SERVICE_UNAVAILABLE, "대기열 확인 지연 중입니다. 5초 후 다시 시도해주세요.");
    }

    public QueueServiceUnavailableException(String message) {
        super(ErrorCode.QUEUE_SERVICE_UNAVAILABLE, message);
    }
}
