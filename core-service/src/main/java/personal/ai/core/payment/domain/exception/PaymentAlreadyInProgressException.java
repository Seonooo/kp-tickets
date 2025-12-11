package personal.ai.core.payment.domain.exception;

import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

/**
 * Payment Already In Progress Exception
 * 이미 진행 중인 결제가 있을 때 발생
 */
public class PaymentAlreadyInProgressException extends BusinessException {
    public PaymentAlreadyInProgressException(Long paymentId) {
        super(ErrorCode.PAYMENT_IN_PROGRESS, String.format("Payment already in progress: %d", paymentId));
    }
}