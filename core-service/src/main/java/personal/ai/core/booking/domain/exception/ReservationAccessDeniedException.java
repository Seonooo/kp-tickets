package personal.ai.core.booking.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Reservation Access Denied Exception
 * 예약에 대한 접근 권한이 없을 때 발생
 */
public class ReservationAccessDeniedException extends BusinessException {
    public ReservationAccessDeniedException(Long reservationId, Long userId) {
        super(ErrorCode.FORBIDDEN,
                String.format("User %d does not have access to reservation %d", userId, reservationId));
    }

    public ReservationAccessDeniedException() {
        super(ErrorCode.FORBIDDEN, "해당 예약에 접근할 권한이 없습니다.");
    }
}
