package personal.ai.core.booking.adapter.in.web.dto;

import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.booking.domain.model.ReservationStatus;

import java.time.LocalDateTime;

/**
 * 예약 조회/생성 응답 DTO
 */
public record ReservationResponse(
        Long reservationId,
        Long userId,
        Long seatId,
        Long scheduleId,
        ReservationStatus status,
        LocalDateTime expiresAt,
        LocalDateTime createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.id(),
                reservation.userId(),
                reservation.seatId(),
                reservation.scheduleId(),
                reservation.status(),
                reservation.expiresAt(),
                reservation.createdAt()
        );
    }
}
