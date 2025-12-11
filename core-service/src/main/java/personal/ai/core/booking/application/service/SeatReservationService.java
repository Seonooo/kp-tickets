package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import personal.ai.core.booking.application.port.in.ReserveSeatCommand;
import personal.ai.core.booking.application.port.in.ReserveSeatUseCase;
import personal.ai.core.booking.application.port.out.QueueServiceClient;
import personal.ai.core.booking.application.port.out.ReservationCacheRepository;
import personal.ai.core.booking.application.port.out.SeatLockRepository;
import personal.ai.core.booking.domain.exception.ConcurrentReservationException;
import personal.ai.core.booking.domain.exception.SeatAlreadyReservedException;
import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.booking.domain.service.BookingManager;

/**
 * Seat Reservation Service (SRP)
 * 단일 책임: 좌석 예약 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatReservationService implements ReserveSeatUseCase {

    private static final int SEAT_LOCK_TTL_SECONDS = 300;

    private final SeatLockRepository seatLockRepository;
    private final ReservationCacheRepository reservationCacheRepository;
    private final QueueServiceClient queueServiceClient;
    private final BookingManager bookingManager;

    @Override
    public Reservation reserveSeat(ReserveSeatCommand command) {
        queueServiceClient.validateToken(command.userId(), command.queueToken());

        boolean locked = seatLockRepository.tryLock(command.seatId(), command.userId(), SEAT_LOCK_TTL_SECONDS);
        if (!locked) {
            log.warn("Seat already reserved: seatId={}", command.seatId());
            throw new SeatAlreadyReservedException(command.seatId());
        }

        try {
            var saved = bookingManager.reserveSeatInTransaction(command);
            reservationCacheRepository.setReservationTTL(saved.id(), saved.expiresAt());

            log.debug("Seat reserved: reservationId={}, seatId={}", saved.id(), command.seatId());
            return saved;

        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent reservation detected: seatId={}", command.seatId());
            throw new ConcurrentReservationException(command.seatId());

        } finally {
            seatLockRepository.unlock(command.seatId(), command.userId());
        }
    }
}
