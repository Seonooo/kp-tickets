package personal.ai.core.booking.domain.model;

/**
 * Reservation Status Enum
 * 예약 상태
 */
public enum ReservationStatus {
    /**
     * 임시 예약 (결제 대기 중)
     */
    PENDING,

    /**
     * 결제 완료 (확정)
     */
    CONFIRMED,

    /**
     * 사용자 취소
     */
    CANCELLED,

    /**
     * 시간 만료
     */
    EXPIRED
}
