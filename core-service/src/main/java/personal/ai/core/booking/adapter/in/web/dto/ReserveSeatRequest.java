package personal.ai.core.booking.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import personal.ai.core.booking.application.port.in.ReserveSeatCommand;

/**
 * 좌석 예약 요청 DTO
 */
public record ReserveSeatRequest(
        @NotNull(message = "좌석 ID는 필수입니다.")
        Long seatId,

        @NotNull(message = "일정 ID는 필수입니다.")
        Long scheduleId
) {
    public ReserveSeatCommand toCommand(Long userId, String queueToken) {
        return new ReserveSeatCommand(userId, seatId, scheduleId, queueToken);
    }
}
