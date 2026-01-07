package personal.ai.core.booking.adapter.out.persistence;

import personal.ai.core.booking.domain.model.Seat;
import personal.ai.core.booking.domain.model.SeatGrade;
import personal.ai.core.booking.domain.model.SeatStatus;

import java.math.BigDecimal;

/**
 * Seat Query DTO (Read-Only Projection)
 * 좌석 조회 전용 DTO - 필요한 필드만 포함하여 데이터 전송량 최소화
 *
 * 용도: 예약 가능 좌석 목록 조회 시 사용
 * 효과: 데이터 크기 약 50% 감소 (id, scheduleId 제외)
 */
public record SeatQueryDTO(
        Long id,            // 좌석 예약 시 필요
        String seatNumber,  // UI 표시용
        SeatGrade grade,    // UI 표시용 (VIP, S, A, B)
        BigDecimal price,   // UI 표시용
        SeatStatus status   // UI 표시용 (AVAILABLE, RESERVED, OCCUPIED)
) {
    /**
     * Domain Model로 변환
     * 좌석 예약 시 scheduleId가 필요하므로 별도 매개변수로 전달
     */
    public Seat toDomain(Long scheduleId) {
        return new Seat(id, scheduleId, seatNumber, grade, price, status);
    }
}
