package personal.ai.core.booking.domain.model;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;
import personal.ai.core.booking.domain.exception.SeatNotAvailableException;

import java.math.BigDecimal;

/**
 * Seat Domain Model
 * 좌석 도메인 모델 (불변)
 */
public record Seat(
        Long id,
        Long scheduleId,
        String seatNumber,
        SeatGrade grade,
        BigDecimal price,
        SeatStatus status
) {
    public Seat {
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Seat ID cannot be null");
        }
        if (scheduleId == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Schedule ID cannot be null");
        }
        if (seatNumber == null || seatNumber.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Seat number cannot be null or blank");
        }
        if (grade == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Seat grade cannot be null");
        }
        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Seat price must be positive");
        }
        if (status == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Seat status cannot be null");
        }
    }

    /**
     * 좌석 예약 (AVAILABLE -> RESERVED)
     * 도메인 로직: 예약 가능 상태 검증
     */
    public Seat reserve() {
        if (status != SeatStatus.AVAILABLE) {
            throw new SeatNotAvailableException(id, status);
        }
        return new Seat(id, scheduleId, seatNumber, grade, price, SeatStatus.RESERVED);
    }

    /**
     * 좌석 점유 (RESERVED -> OCCUPIED)
     * 결제 완료 시 호출
     */
    public Seat occupy() {
        if (status != SeatStatus.RESERVED) {
            throw new IllegalStateException(
                    String.format("Cannot occupy seat in %s status. Seat ID: %d", status, id)
            );
        }
        return new Seat(id, scheduleId, seatNumber, grade, price, SeatStatus.OCCUPIED);
    }

    /**
     * 좌석 해제 (RESERVED -> AVAILABLE)
     * 예약 만료 또는 취소 시 호출
     */
    public Seat release() {
        if (status != SeatStatus.RESERVED) {
            throw new IllegalStateException(
                    String.format("Cannot release seat in %s status. Seat ID: %d", status, id)
            );
        }
        return new Seat(id, scheduleId, seatNumber, grade, price, SeatStatus.AVAILABLE);
    }

    /**
     * 예약 가능 여부 확인
     */
    public boolean isAvailable() {
        return status == SeatStatus.AVAILABLE;
    }

    /**
     * 예약 상태 여부 확인
     */
    public boolean isReserved() {
        return status == SeatStatus.RESERVED;
    }

    /**
     * 점유 상태 여부 확인
     */
    public boolean isOccupied() {
        return status == SeatStatus.OCCUPIED;
    }
}
