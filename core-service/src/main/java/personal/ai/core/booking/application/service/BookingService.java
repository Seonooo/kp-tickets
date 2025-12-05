package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import personal.ai.core.booking.application.port.in.GetAvailableSeatsUseCase;
import personal.ai.core.booking.application.port.in.GetReservationUseCase;
import personal.ai.core.booking.application.port.in.ReserveSeatCommand;
import personal.ai.core.booking.application.port.in.ReserveSeatUseCase;
import personal.ai.core.booking.application.port.out.*;
import personal.ai.core.booking.domain.exception.ConcurrentReservationException;
import personal.ai.core.booking.domain.exception.ReservationNotFoundException;
import personal.ai.core.booking.domain.exception.SeatAlreadyReservedException;
import personal.ai.core.booking.domain.exception.SeatNotFoundException;
import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.booking.domain.model.Seat;

import java.util.List;

/**
 * Booking Application Service
 * MVP: 좌석 조회, 예약 생성, 예약 조회만 구현
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BookingService implements
        ReserveSeatUseCase,
        GetAvailableSeatsUseCase,
        GetReservationUseCase {

    // 예약 TTL (분)
    private static final int RESERVATION_TTL_MINUTES = 5;
    // Redis 락 TTL (초)
    private static final int SEAT_LOCK_TTL_SECONDS = 300; // 5분
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final SeatLockRepository seatLockRepository;
    private final ReservationCacheRepository reservationCacheRepository;
    private final QueueServiceClient queueServiceClient;
    private final ReservationEventPublisher eventPublisher;

    @Override
    @Transactional
    public Reservation reserveSeat(ReserveSeatCommand command) {
        log.info("Reserving seat: userId={}, seatId={}, scheduleId={}",
                command.userId(), command.seatId(), command.scheduleId());

        // 1. Queue 토큰 검증 (Queue Service HTTP 호출)
        queueServiceClient.validateToken(command.userId(), command.queueToken());

        // 2. Redis SETNX로 좌석 선점 (1차 방어 - Fail-Fast, 5분 TTL)
        //    성공: true 반환, 실패: false 반환 (대기하지 않고 즉시 실패)
        boolean locked = seatLockRepository.tryLock(
                command.seatId(),
                command.userId(),
                SEAT_LOCK_TTL_SECONDS
        );

        if (!locked) {
            // DB 접근 없이 즉시 409 Conflict 반환
            log.warn("Seat already reserved: seatId={}", command.seatId());
            throw new SeatAlreadyReservedException(command.seatId());
        }

        try {
            // 3. 좌석 조회 (일반 조회, 비관적 락 없음)
            Seat seat = seatRepository.findById(command.seatId())
                    .orElseThrow(() -> new SeatNotFoundException(command.seatId()));

            // 4. 도메인 로직: 좌석 예약 가능 검증 (AVAILABLE -> RESERVED)
            Seat reservedSeat = seat.reserve();
            seatRepository.save(reservedSeat);

            // 5. 예약 생성 (PENDING 상태, 5분 TTL)
            //    DB Unique Index (concert_id, seat_id)가 2차 방어선 역할
            Reservation reservation = Reservation.create(
                    command.userId(),
                    command.seatId(),
                    command.scheduleId(),
                    RESERVATION_TTL_MINUTES
            );
            Reservation saved = reservationRepository.save(reservation);

            // 6. Redis TTL 설정 (만료 처리용)
            reservationCacheRepository.setReservationTTL(
                    saved.id(),
                    saved.expiresAt()
            );

            // 7. Kafka 이벤트 발행 (트랜잭션 커밋 후)
            eventPublisher.publishReservationCreated(saved);

            log.info("Seat reserved successfully: reservationId={}, userId={}, seatId={}",
                    saved.id(), command.userId(), command.seatId());

            return saved;

        } catch (DataIntegrityViolationException e) {
            // 2차 방어: DB Unique Index 위반 (매우 드문 케이스)
            // Redis는 통과했지만 DB에서 중복 감지된 경우
            log.error("Concurrent reservation detected: seatId={}", command.seatId(), e);
            seatLockRepository.unlock(command.seatId());
            throw new ConcurrentReservationException(command.seatId());

        } catch (Exception e) {
            // 기타 실패 시 Redis 락 해제
            log.error("Failed to reserve seat: seatId={}", command.seatId(), e);
            seatLockRepository.unlock(command.seatId());
            throw e;
        }
    }

    @Override
    public List<Seat> getAvailableSeats(Long scheduleId) {
        log.debug("Getting available seats: scheduleId={}", scheduleId);

        // 일정 존재 여부 확인은 생략 (좌석 목록이 비어있으면 빈 리스트 반환)
        List<Seat> availableSeats = seatRepository.findAvailableByScheduleId(scheduleId);

        log.debug("Found {} available seats for schedule: {}", availableSeats.size(), scheduleId);

        return availableSeats;
    }

    @Override
    public Reservation getReservation(Long reservationId) {
        log.debug("Getting reservation: reservationId={}", reservationId);

        return reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));
    }

}
