package personal.ai.core.payment.adapter.out.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import personal.ai.core.booking.application.port.out.ReservationRepository;
import personal.ai.core.booking.application.port.out.SeatLockRepository;
import personal.ai.core.booking.domain.exception.ReservationExpiredException;
import personal.ai.core.booking.domain.exception.ReservationNotFoundException;
import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.payment.application.port.out.PaymentEventPort;
import personal.ai.core.payment.application.port.out.PaymentRepository;
import personal.ai.core.payment.application.port.out.PaymentResultHandlerPort;
import personal.ai.core.payment.domain.model.Payment;

/**
 * Payment Result Handler Adapter
 * 결제 결과 처리 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentResultHandlerAdapter implements PaymentResultHandlerPort {

    private final PaymentRepository paymentRepository;
    private final ReservationRepository reservationRepository;
    private final SeatLockRepository seatLockRepository;
    private final PaymentEventPort paymentEventPort;

    @Override
    @Transactional
    public Payment handleSuccess(Payment pendingPayment, String concertId) {
        var reservation = reservationRepository.findById(pendingPayment.reservationId())
                .orElseThrow(() -> new ReservationNotFoundException(pendingPayment.reservationId()));

        if (reservation.isExpired()) {
            log.warn("Reservation expired during payment: reservationId={}", reservation.id());
            throw new ReservationExpiredException(reservation.id());
        }

        var completedPayment = pendingPayment.complete();
        var savedPayment = paymentRepository.save(completedPayment);

        paymentEventPort.publishPaymentCompleted(savedPayment, concertId);

        log.debug("Payment success handled: paymentId={}", savedPayment.id());
        return savedPayment;
    }

    @Override
    @Transactional
    public void handleFailure(Payment pendingPayment, Reservation reservation, Long userId) {
        var failedPayment = pendingPayment.fail();
        paymentRepository.save(failedPayment);

        seatLockRepository.unlock(reservation.seatId(), userId);

        var cancelledReservation = reservation.cancel();
        reservationRepository.save(cancelledReservation);

        log.warn("Payment failure handled: paymentId={}, reservationId={}",
                failedPayment.id(), failedPayment.reservationId());
    }
}
