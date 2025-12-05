package personal.ai.core.booking.adapter.in.web.dto;

import personal.ai.core.booking.domain.model.Seat;
import personal.ai.core.booking.domain.model.SeatGrade;
import personal.ai.core.booking.domain.model.SeatStatus;

import java.math.BigDecimal;

/**
 * 좌석 조회 응답 DTO
 */
public record SeatResponse(
        Long seatId,
        Long scheduleId,
        String seatNumber,
        SeatGrade grade,
        BigDecimal price,
        SeatStatus status
) {
    public static SeatResponse from(Seat seat) {
        return new SeatResponse(
                seat.id(),
                seat.scheduleId(),
                seat.seatNumber(),
                seat.grade(),
                seat.price(),
                seat.status()
        );
    }
}
