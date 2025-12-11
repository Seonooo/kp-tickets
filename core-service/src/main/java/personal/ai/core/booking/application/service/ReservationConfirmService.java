package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import personal.ai.core.booking.application.port.in.ConfirmReservationUseCase;
import personal.ai.core.booking.application.port.out.ReservationRepository;
import personal.ai.core.booking.application.port.out.SeatRepository;
import personal.ai.core.booking.domain.exception.ReservationNotFoundException;
import personal.ai.core.booking.domain.exception.SeatNotFoundException;
import personal.ai.core.booking.domain.model.Reservation;

/**
 * Reservation Confirm Service (SRP)
 * 단일 책임: 예약 확정 (결제 완료 후)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationConfirmService implements ConfirmReservationUseCase {

    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;

    @Override
    @Transactional
    public Reservation confirmReservation(ConfirmReservationCommand command) {
        var reservation = reservationRepository.findById(command.reservationId())
                .orElseThrow(() -> {
                    log.warn("Reservation not found for confirmation: reservationId={}", command.reservationId());
                    return new ReservationNotFoundException(command.reservationId());
                });

        reservation.ensureOwnership(command.userId());

        var confirmedReservation = reservation.confirm();
        var savedReservation = reservationRepository.save(confirmedReservation);

        var seat = seatRepository.findById(reservation.seatId())
                .orElseThrow(() -> new SeatNotFoundException(reservation.seatId()));

        var occupiedSeat = seat.occupy();
        seatRepository.save(occupiedSeat);

        log.debug("Reservation confirmed: reservationId={}, seatId={}", savedReservation.id(), seat.id());

        return savedReservation;
    }
}
