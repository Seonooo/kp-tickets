package personal.ai.core.payment.application.port.out;

import personal.ai.core.booking.domain.model.Reservation;

/**
 * Reservation Validation Port
 * 결제를 위한 예약 검증 책임
 */
public interface ReservationValidationPort {

    /**
     * 결제를 위한 예약 검증
     * - 예약 존재 여부
     * - 소유권 검증
     * - 상태 검증 (PENDING)
     * - 만료 여부
     *
     * @param reservationId 예약 ID
     * @param userId        사용자 ID
     * @return 검증된 예약
     */
    Reservation validateForPayment(Long reservationId, Long userId);
}
