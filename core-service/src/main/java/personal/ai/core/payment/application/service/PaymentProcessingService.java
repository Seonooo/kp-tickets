package personal.ai.core.payment.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;
import personal.ai.core.payment.application.port.in.ProcessPaymentUseCase;
import personal.ai.core.payment.application.port.out.PaymentRepository;
import personal.ai.core.payment.application.port.out.PaymentResultHandlerPort;
import personal.ai.core.payment.application.port.out.ReservationValidationPort;
import personal.ai.core.payment.domain.exception.PaymentAlreadyCompletedException;
import personal.ai.core.payment.domain.exception.PaymentAlreadyInProgressException;
import personal.ai.core.payment.domain.exception.PaymentFailedException;
import personal.ai.core.payment.domain.model.Payment;
import personal.ai.core.payment.domain.service.PaymentMockService;

/**
 * Payment Processing Service (SRP)
 * 단일 책임: 결제 처리 오케스트레이션
 * 
 * 협력 객체:
 * - ReservationValidationPort: 예약 검증
 * - PaymentResultHandlerPort: 결과 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentProcessingService implements ProcessPaymentUseCase {

    private final PaymentRepository paymentRepository;
    private final PaymentMockService paymentMockService;
    private final TransactionTemplate transactionTemplate;

    // Port 의존성 (DIP)
    private final ReservationValidationPort reservationValidationPort;
    private final PaymentResultHandlerPort paymentResultHandlerPort;

    @Override
    public Payment processPayment(ProcessPaymentCommand command) {
        // 1. 예약 검증 (Port 위임)
        var reservation = reservationValidationPort.validateForPayment(
                command.reservationId(), command.userId());

        // 2. PENDING 결제 생성
        var pendingPayment = transactionTemplate.execute(status -> createPendingPayment(command));

        // 3. 외부 결제 처리 (No Tx)
        boolean paymentSuccess = paymentMockService.processPayment(
                command.userId(), command.reservationId(), command.amount().longValue());

        // 4. 결과 처리 (Port 위임)
        if (paymentSuccess) {
            var completedPayment = transactionTemplate
                    .execute(status -> paymentResultHandlerPort.handleSuccess(pendingPayment, command.concertId()));

            if (completedPayment == null) {
                log.error("Payment result handler returned null: paymentId={}", pendingPayment.id());
                throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Failed to handle payment success");
            }

            return completedPayment;
        } else {
            transactionTemplate.execute(status -> {
                paymentResultHandlerPort.handleFailure(pendingPayment, reservation, command.userId());
                return null;
            });
            throw new PaymentFailedException("결제가 거절되었습니다.");
        }
    }

    private Payment createPendingPayment(ProcessPaymentCommand command) {
        paymentRepository.findByReservationId(command.reservationId())
                .ifPresent(existing -> {
                    // 1. 이미 완료된 결제: 중복 결제 방지
                    if (existing.isCompleted()) {
                        throw new PaymentAlreadyCompletedException(existing.id());
                    }

                    // 2. 진행 중인 결제: 동시 결제 방지
                    if (existing.isPending()) {
                        log.warn("Payment already in progress: paymentId={}, reservationId={}",
                            existing.id(), command.reservationId());
                        throw new PaymentAlreadyInProgressException(existing.id());
                    }

                    // 3. FAILED, CANCELLED: 재시도 허용 (로그만 남김)
                    log.info("Retrying payment for reservation: reservationId={}, previousStatus={}, previousPaymentId={}",
                        command.reservationId(), existing.status(), existing.id());
                });

        var payment = Payment.create(
                command.reservationId(),
                command.userId(),
                command.amount(),
                command.paymentMethod());

        var pendingPayment = paymentRepository.save(payment);

        if (pendingPayment == null) {
            log.error("Payment repository returned null: reservationId={}", command.reservationId());
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
                "Failed to create pending payment");
        }

        log.debug("Payment created: paymentId={}", pendingPayment.id());

        return pendingPayment;
    }
}
