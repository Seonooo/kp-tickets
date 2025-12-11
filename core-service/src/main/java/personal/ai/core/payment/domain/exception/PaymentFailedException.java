package personal.ai.core.payment.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Payment Failed Exception
 * 결제가 거부되었을 때 발생
 */
public class PaymentFailedException extends BusinessException {
    public PaymentFailedException(Long reservationId) {
        super(ErrorCode.PAYMENT_FAILED,
                String.format("Payment failed for reservation: %d", reservationId));
    }

    public PaymentFailedException(String message) {
        super(ErrorCode.PAYMENT_FAILED, message);
    }
}
