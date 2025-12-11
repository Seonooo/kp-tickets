package personal.ai.core.booking.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;
import personal.ai.core.booking.domain.model.ReservationStatus;

/**
 * Invalid Reservation State Exception
 * 예약 상태가 올바르지 않을 때 발생
 */
public class InvalidReservationStateException extends BusinessException {
    public InvalidReservationStateException(ReservationStatus currentStatus, ReservationStatus expectedStatus) {
        super(ErrorCode.INVALID_INPUT,
                String.format("예약 상태가 %s이어야 하지만 현재 %s입니다.", expectedStatus, currentStatus));
    }

    public InvalidReservationStateException(ReservationStatus currentStatus) {
        super(ErrorCode.INVALID_INPUT,
                String.format("예약이 PENDING 상태가 아닙니다. 현재 상태: %s", currentStatus));
    }
}
