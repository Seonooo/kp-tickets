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
import personal.ai.core.booking.domain.service.QueueTokenExtractor;

/**
 * Seat Reservation Service (SRP)
 * 단일 책임: 좌석 예약 처리
 * 
 * 캐시 정합성:
 * - 좌석 예약 성공 시 availableSeats 캐시를 즉시 무효화
 * - 다음 조회 시 DB에서 최신 데이터 로드
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
    private final SeatQueryCacheService seatQueryCacheService;

    @Override
    public Reservation reserveSeat(ReserveSeatCommand command) {
        // 토큰에서 concertId 추출 후 검증
        String concertId = QueueTokenExtractor.extractConcertId(command.queueToken());
        queueServiceClient.validateToken(concertId, command.userId(), command.queueToken());

        boolean locked = seatLockRepository.tryLock(command.seatId(), command.userId(), SEAT_LOCK_TTL_SECONDS);
        if (!locked) {
            log.warn("Seat already reserved: seatId={}", command.seatId());
            throw new SeatAlreadyReservedException(command.seatId());
        }

        try {
            var saved = bookingManager.reserveSeatInTransaction(command);
            reservationCacheRepository.setReservationTTL(saved.id(), saved.expiresAt());

            // 캐시 무효화: 예약 성공 후 해당 스케줄의 좌석 캐시 삭제
            seatQueryCacheService.evictAvailableSeatsCache(command.scheduleId());

            log.debug("Seat reserved: reservationId={}, seatId={}, scheduleId={}",
                    saved.id(), command.seatId(), command.scheduleId());
            return saved;

        } catch (DataIntegrityViolationException e) {
            log.warn("Concurrent reservation detected: seatId={}", command.seatId());
            throw new ConcurrentReservationException(command.seatId());

        } finally {
            seatLockRepository.unlock(command.seatId(), command.userId());
        }
    }
}
