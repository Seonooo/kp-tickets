package personal.ai.core.booking.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Reservation Expired Exception
 * 예약이 만료된 경우 발생하는 예외
 */
public class ReservationExpiredException extends BusinessException {
    public ReservationExpiredException(Long reservationId) {
        super(ErrorCode.RESERVATION_EXPIRED,
                String.format("Reservation has expired: reservationId=%d", reservationId));
    }
}
