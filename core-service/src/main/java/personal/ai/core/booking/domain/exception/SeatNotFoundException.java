package personal.ai.core.booking.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Seat Not Found Exception
 * 좌석을 찾을 수 없을 때 발생하는 예외
 */
public class SeatNotFoundException extends BusinessException {
    public SeatNotFoundException(Long seatId) {
        super(ErrorCode.SEAT_NOT_FOUND, String.format("Seat not found: seatId=%d", seatId));
    }
}
