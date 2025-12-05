package personal.ai.core.booking.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Seat Already Reserved Exception
 * 좌석이 이미 다른 사용자에 의해 선점된 경우 발생하는 예외
 * HTTP 409 Conflict 반환용
 */
public class SeatAlreadyReservedException extends BusinessException {
    public SeatAlreadyReservedException(Long seatId) {
        super(ErrorCode.SEAT_ALREADY_RESERVED,
                String.format("Seat already reserved by another user: seatId=%d", seatId));
    }
}
