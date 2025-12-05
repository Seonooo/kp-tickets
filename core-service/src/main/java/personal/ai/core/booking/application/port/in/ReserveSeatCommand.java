package personal.ai.core.booking.application.port.in;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Reserve Seat Command
 * 좌석 예약 커맨드
 */
public record ReserveSeatCommand(
        Long userId,
        Long seatId,
        Long scheduleId,
        String queueToken
) {
    public ReserveSeatCommand {
        if (userId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "User ID cannot be null");
        }
        if (seatId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Seat ID cannot be null");
        }
        if (scheduleId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Schedule ID cannot be null");
        }
        if (queueToken == null || queueToken.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Queue token cannot be null or blank");
        }
    }
}
