package personal.ai.core.payment.application.port.out;

import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.payment.domain.model.Payment;

/**
 * Payment Result Handler Port
 * 결제 결과 처리 책임
 */
public interface PaymentResultHandlerPort {

    /**
     * 결제 성공 처리
     * - 결제 상태 COMPLETED로 변경
     * - 이벤트 발행
     *
     * @param pendingPayment PENDING 상태 결제
     * @param concertId      콘서트 ID
     * @return 완료된 결제
     */
    Payment handleSuccess(Payment pendingPayment, String concertId);

    /**
     * 결제 실패 처리
     * - 결제 상태 FAILED로 변경
     * - 좌석 락 해제
     * - 예약 취소
     *
     * @param pendingPayment PENDING 상태 결제
     * @param reservation    예약
     * @param userId         사용자 ID
     */
    void handleFailure(Payment pendingPayment, Reservation reservation, Long userId);
}
