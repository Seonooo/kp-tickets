package personal.ai.core.booking.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Concurrent Reservation Exception
 * DB Unique Index 위반 등 동시성 충돌 발생 시 예외
 * Redis는 통과했지만 DB에서 중복 감지된 매우 드문 케이스
 */
public class ConcurrentReservationException extends BusinessException {
    public ConcurrentReservationException(Long seatId) {
        super(ErrorCode.CONCURRENT_RESERVATION,
                String.format("Concurrent reservation detected for seat: seatId=%d", seatId));
    }
}
