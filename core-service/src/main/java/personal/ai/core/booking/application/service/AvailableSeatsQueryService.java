package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.core.booking.application.port.in.GetAvailableSeatsUseCase;
import personal.ai.core.booking.application.port.out.QueueServiceClient;
import personal.ai.core.booking.domain.model.Seat;
import personal.ai.core.booking.domain.service.QueueTokenExtractor;

import java.util.List;

/**
 * Available Seats Query Service (SRP)
 * 단일 책임: 예약 가능 좌석 조회
 *
 * 성능 최적화:
 * - Redis 캐싱 적용 (TTL: 1초)
 * - @Transactional 제거하여 DB 커넥션 효율 개선
 *   (HTTP 호출 중 커넥션 홀딩 방지)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AvailableSeatsQueryService implements GetAvailableSeatsUseCase {

    private final SeatQueryCacheService seatQueryCacheService;
    private final QueueServiceClient queueServiceClient;

    @Override
    public List<Seat> getAvailableSeats(Long scheduleId, Long userId, String queueToken) {
        String concertId = QueueTokenExtractor.extractConcertId(queueToken);
        queueServiceClient.validateToken(concertId, userId, queueToken);
        return seatQueryCacheService.findAvailableSeats(scheduleId);
    }
}
