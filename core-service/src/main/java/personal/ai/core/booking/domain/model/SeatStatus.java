package personal.ai.core.booking.domain.model;

/**
 * Seat Status Enum
 * 좌석 상태
 */
public enum SeatStatus {
    /**
     * 예매 가능
     */
    AVAILABLE,

    /**
     * 임시 예약 (5분 TTL)
     */
    RESERVED,

    /**
     * 결제 완료 (점유됨)
     */
    OCCUPIED
}
