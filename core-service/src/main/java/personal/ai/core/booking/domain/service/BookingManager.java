package personal.ai.core.booking.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import personal.ai.core.booking.application.port.in.ReserveSeatCommand;
import personal.ai.core.booking.application.port.out.ReservationRepository;
import personal.ai.core.booking.application.port.out.SeatRepository;
import personal.ai.core.booking.domain.exception.SeatNotFoundException;
import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.booking.domain.model.ReservationStatus;
import personal.ai.core.booking.domain.model.Seat;

/**
 * Booking Domain Service (Transaction Manager)
 * 트랜잭션 범위 분리를 위한 실행 전용 서비스
 * Domain Layer에 속하며 Port에만 의존
 * Outbox 처리는 ReservationRepository Adapter 내부에서 수행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingManager {

    // 예약 TTL (분)
    private static final int RESERVATION_TTL_MINUTES = 5;
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;

    /**
     * 트랜잭션 내에서 좌석 예약 및 저장
     * Safe Transaction Pattern: DB 작업만 트랜잭션 내에서 수행
     * Transactional Outbox Pattern: Adapter 내부에서 Outbox 저장 (도메인은 알 필요 없음)
     */
    @Transactional
    public Reservation reserveSeatInTransaction(ReserveSeatCommand command) {
        // 1. 좌석 조회 (일반 조회, 비관적 락 없음)
        Seat seat = seatRepository.findById(command.seatId())
                .orElseThrow(() -> new SeatNotFoundException(command.seatId()));

        // 2. 도메인 로직: 좌석 예약 가능 검증 (AVAILABLE -> RESERVED)
        Seat reservedSeat = seat.reserve();
        seatRepository.save(reservedSeat);

        // 3. 예약 생성 (PENDING 상태, 5분 TTL)
        // DB Unique Index (schedule_id, seat_id)가 2차 방어선 역할
        // Outbox 저장은 ReservationPersistenceAdapter 내부에서 자동 처리
        Reservation reservation = Reservation.create(
                command.userId(),
                command.seatId(),
                command.scheduleId(),
                RESERVATION_TTL_MINUTES);

        return reservationRepository.save(reservation);
    }

    /**
     * 예약 만료 처리 (트랜잭션)
     * 1. 예약 상태 변경 (PENDING -> EXPIRED)
     * 2. 좌석 상태 변경 (RESERVED -> AVAILABLE)
     */
    @Transactional
    public void expireReservation(Long reservationId) {
        log.info("Expiring reservation: reservationId={}", reservationId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElse(null);

        if (reservation == null) {
            log.warn("Reservation not found for expiration: reservationId={}", reservationId);
            return;
        }

        // 이미 완료된 예약은 만료 처리하지 않음
        if (reservation.isConfirmed()) {
            log.warn("Reservation is already confirmed: reservationId={}", reservationId);
            return;
        }

        // 이미 만료된 경우 패스 (멱등성)
        if (reservation.status() == ReservationStatus.EXPIRED) {
            log.warn("Reservation is already expired: reservationId={}", reservationId);
            return;
        }

        // 1. 예약 만료 처리 (상태 변경 -> save -> Outbox Event 자동 발행)
        Reservation expiredReservation = reservation.expire();
        reservationRepository.save(expiredReservation);

        // 2. 좌석 해제
        Seat seat = seatRepository.findById(reservation.seatId())
                .orElseThrow(() -> new SeatNotFoundException(reservation.seatId()));

        if (seat.isReserved()) {
            Seat releasedSeat = seat.release();
            seatRepository.save(releasedSeat);
            log.info("Seat released: seatId={}", seat.id());
        }
    }
}
