package personal.ai.core.booking.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Concert Not Found Exception
 * 콘서트를 찾을 수 없을 때 발생하는 예외
 */
public class ConcertNotFoundException extends BusinessException {
    public ConcertNotFoundException(Long concertId) {
        super(ErrorCode.CONCERT_NOT_FOUND,
                String.format("Concert not found: concertId=%d", concertId));
    }
}
