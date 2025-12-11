package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import personal.ai.core.booking.application.port.in.GetReservationUseCase;
import personal.ai.core.booking.application.port.out.ReservationRepository;
import personal.ai.core.booking.domain.exception.ReservationNotFoundException;
import personal.ai.core.booking.domain.model.Reservation;

/**
 * Reservation Query Service (SRP)
 * 단일 책임: 예약 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReservationQueryService implements GetReservationUseCase {

    private final ReservationRepository reservationRepository;

    @Override
    public Reservation getReservation(Long reservationId, Long userId) {
        var reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> {
                    log.warn("Reservation not found: reservationId={}", reservationId);
                    return new ReservationNotFoundException(reservationId);
                });

        reservation.ensureOwnership(userId);

        log.debug("Reservation retrieved: reservationId={}", reservationId);

        return reservation;
    }
}
