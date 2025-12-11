package personal.ai.core.payment.adapter.out.validation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.application.port.out.ReservationRepository;
import personal.ai.core.booking.domain.exception.ReservationExpiredException;
import personal.ai.core.booking.domain.exception.ReservationNotFoundException;
import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.payment.application.port.out.ReservationValidationPort;

/**
 * Reservation Validation Adapter
 * 결제를 위한 예약 검증 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationValidationAdapter implements ReservationValidationPort {

    private final ReservationRepository reservationRepository;

    @Override
    public Reservation validateForPayment(Long reservationId, Long userId) {
        var reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> {
                    log.warn("Reservation not found for payment: reservationId={}", reservationId);
                    return new ReservationNotFoundException(reservationId);
                });

        reservation.ensureOwnership(userId);
        reservation.ensurePending();

        if (reservation.isExpired()) {
            log.warn("Reservation expired for payment: reservationId={}", reservationId);
            throw new ReservationExpiredException(reservationId);
        }

        log.debug("Reservation validated for payment: reservationId={}", reservationId);
        return reservation;
    }
}
