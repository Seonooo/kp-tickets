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
import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.booking.domain.model.Seat;
import personal.ai.core.booking.domain.service.BookingManager;

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

    // Redis 락 TTL (초)
    private static final int SEAT_LOCK_TTL_SECONDS = 300; // 5분
    private final SeatRepository seatRepository;
    private final ReservationRepository reservationRepository;
    private final SeatLockRepository seatLockRepository;
    private final ReservationCacheRepository reservationCacheRepository;
    private final QueueServiceClient queueServiceClient;
    private final ReservationEventPublisher eventPublisher;
    private final BookingManager bookingManager;

    @Override
    public Reservation reserveSeat(ReserveSeatCommand command) {
        log.info("Reserving seat: userId={}, seatId={}, scheduleId={}",
                command.userId(), command.seatId(), command.scheduleId());

        // 1. Queue 토큰 검증 (트랜잭션 밖에서 수행 - Safe Transaction Pattern)
        queueServiceClient.validateToken(command.userId(), command.queueToken());

        // 2. Redis SETNX로 좌석 선점 (1차 방어 - Fail-Fast, 5분 TTL)
        // 성공: true 반환, 실패: false 반환 (대기하지 않고 즉시 실패)
        boolean locked = seatLockRepository.tryLock(
                command.seatId(),
                command.userId(),
                SEAT_LOCK_TTL_SECONDS);

        if (!locked) {
            // DB 접근 없이 즉시 409 Conflict 반환
            log.warn("Seat already reserved: seatId={}", command.seatId());
            throw new SeatAlreadyReservedException(command.seatId());
        }

        try {
            // 3. DB 트랜잭션 내에서 좌석 예약 및 저장 (BookingManager 위임)
            Reservation saved = bookingManager.reserveSeatInTransaction(command);

            // 4. 트랜잭션 외부에서 Redis TTL 설정 (만료 처리용)
            reservationCacheRepository.setReservationTTL(
                    saved.id(),
                    saved.expiresAt());

            // 5. Kafka 이벤트 발행 (트랜잭션 커밋 후)
            eventPublisher.publishReservationCreated(saved);

            log.info("Seat reserved successfully: reservationId={}, userId={}, seatId={}",
                    saved.id(), command.userId(), command.seatId());

            return saved;

        } catch (DataIntegrityViolationException e) {
            // 2차 방어: DB Unique Index 위반 (매우 드문 케이스)
            log.error("Concurrent reservation detected: seatId={}", command.seatId(), e);
            throw new ConcurrentReservationException(command.seatId());

        } catch (Exception e) {
            // 기타 실패 시 예외 전파
            log.error("Failed to reserve seat: seatId={}", command.seatId(), e);
            throw e;

        } finally {
            // 모든 경우에 Redis 락 해제 (성공/실패 무관)
            seatLockRepository.unlock(command.seatId(), command.userId());
        }
    }

    @Override
    public List<Seat> getAvailableSeats(Long scheduleId, Long userId, String queueToken) {
        log.debug("Getting available seats: scheduleId={}, userId={}", scheduleId, userId);

        // Queue 토큰 검증 (대기열을 통과한 사용자만 조회 가능)
        queueServiceClient.validateToken(userId, queueToken);

        // 일정 존재 여부 확인은 생략 (좌석 목록이 비어있으면 빈 리스트 반환)
        List<Seat> availableSeats = seatRepository.findAvailableByScheduleId(scheduleId);

        log.debug("Found {} available seats for schedule: {}", availableSeats.size(), scheduleId);

        return availableSeats;
    }

    @Override
    public Reservation getReservation(Long reservationId, Long userId) {
        log.debug("Getting reservation: reservationId={}, userId={}", reservationId, userId);

        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        // 소유권 검증: 예약한 사용자만 조회 가능
        if (!reservation.userId().equals(userId)) {
            log.warn("Unauthorized reservation access: reservationId={}, requestUserId={}, ownerUserId={}",
                    reservationId, userId, reservation.userId());
            throw new personal.ai.common.exception.BusinessException(
                    personal.ai.common.exception.ErrorCode.FORBIDDEN,
                    "해당 예약에 접근할 권한이 없습니다.");
        }

        return reservation;
    }

}
