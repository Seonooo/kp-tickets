package personal.ai.core.booking.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Queue Token Invalid Exception
 * 대기열 토큰이 유효하지 않을 때 발생
 */
public class QueueTokenInvalidException extends BusinessException {
    public QueueTokenInvalidException() {
        super(ErrorCode.QUEUE_TOKEN_INVALID, "유효하지 않은 대기열 토큰입니다.");
    }

    public QueueTokenInvalidException(String message) {
        super(ErrorCode.QUEUE_TOKEN_INVALID, message);
    }
}
