package personal.ai.core.booking.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Reservation Not Found Exception
 * 예약을 찾을 수 없을 때 발생하는 예외
 */
public class ReservationNotFoundException extends BusinessException {
    public ReservationNotFoundException(Long reservationId) {
        super(ErrorCode.RESERVATION_NOT_FOUND,
                String.format("Reservation not found: reservationId=%d", reservationId));
    }
}
