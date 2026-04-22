package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import personal.ai.core.booking.application.port.in.ExpireReservationUseCase;
import personal.ai.core.booking.application.port.out.ReservationRepository;
import personal.ai.core.booking.application.port.out.SeatRepository;
import personal.ai.core.booking.domain.exception.SeatNotFoundException;
import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.booking.domain.model.Seat;

import java.util.Optional;

/**
 * Booking Manager (Application Service - Transaction Manager)
 * 트랜잭션 범위 분리를 위한 실행 전용 서비스
 * Outbox 처리는 ReservationRepository Adapter 내부에서 수행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingManager implements ExpireReservationUseCase {

    private static final int RESERVATION_TTL_MINUTES = 5;

    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;

    /**
     * 트랜잭션 내에서 좌석 예약 및 저장
     */
    @Transactional
    public Reservation reserveSeatInTransaction(Long userId, Long seatId, Long scheduleId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new SeatNotFoundException(seatId));

        Seat reservedSeat = seat.reserve();
        seatRepository.save(reservedSeat);

        Reservation reservation = Reservation.create(userId, seatId, scheduleId, RESERVATION_TTL_MINUTES);
        return reservationRepository.save(reservation);
    }

    /**
     * 예약 만료 처리 (PENDING -> EXPIRED)
     *
     * <p>멱등성 보장: Redis TTL 만료 이벤트가 중복 도착하거나, 이미 다른 경로로
     * 상태가 전이된 예약(CONFIRMED/CANCELLED/EXPIRED)에 대해서는 조용히 skip한다.
     */
    @Override
    @Transactional
    public void expireReservation(Long reservationId) {
        log.info("Expiring reservation: reservationId={}", reservationId);

        Optional<Reservation> reservationOpt = reservationRepository.findById(reservationId);
        if (reservationOpt.isEmpty()) {
            log.warn("Reservation not found for expiration: reservationId={}", reservationId);
            return;
        }

        Reservation reservation = reservationOpt.get();
        if (!reservation.isPending()) {
            log.warn("Reservation not in PENDING state, skipping expiration: reservationId={}, status={}",
                    reservationId, reservation.status());
            return;
        }

        Reservation expiredReservation = reservation.expire();
        reservationRepository.save(expiredReservation);

        releaseSeatIfReserved(reservation.seatId());
    }

    /**
     * 좌석이 RESERVED 상태일 때만 해제한다.
     * (OCCUPIED/AVAILABLE 상태는 방어적으로 무시)
     */
    private void releaseSeatIfReserved(Long seatId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new SeatNotFoundException(seatId));

        if (seat.isReserved()) {
            Seat releasedSeat = seat.release();
            seatRepository.save(releasedSeat);
            log.info("Seat released: seatId={}", seatId);
        }
    }
}
