package personal.ai.core.booking.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;
import personal.ai.core.booking.domain.model.SeatStatus;

/**
 * Seat Not Available Exception
 * 좌석이 예약 불가능한 상태일 때 발생하는 예외
 */
public class SeatNotAvailableException extends BusinessException {
    public SeatNotAvailableException(Long seatId, SeatStatus currentStatus) {
        super(ErrorCode.SEAT_NOT_AVAILABLE,
                String.format("Seat is not available: seatId=%d, currentStatus=%s", seatId, currentStatus));
    }
}
