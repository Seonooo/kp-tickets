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
        //    DB Unique Index (schedule_id, seat_id)가 2차 방어선 역할
        //    Outbox 저장은 ReservationPersistenceAdapter 내부에서 자동 처리
        Reservation reservation = Reservation.create(
                command.userId(),
                command.seatId(),
                command.scheduleId(),
                RESERVATION_TTL_MINUTES
        );

        return reservationRepository.save(reservation);
    }
}
