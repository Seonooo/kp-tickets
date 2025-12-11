package personal.ai.core.payment.application.port.out;

import personal.ai.core.payment.domain.model.Payment;

/**
 * Payment Event Port
 * 결제 이벤트 발행 책임
 */
public interface PaymentEventPort {

    /**
     * 결제 완료 이벤트 발행 (Outbox 패턴)
     *
     * @param payment   완료된 결제
     * @param concertId 콘서트 ID
     */
    void publishPaymentCompleted(Payment payment, String concertId);
}
