package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.core.booking.adapter.in.web.dto.SeatResponse;
import personal.ai.core.booking.application.port.in.GetAvailableSeatsUseCase;
import personal.ai.core.booking.application.port.out.QueueServiceClient;
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
    public List<SeatResponse> getAvailableSeats(Long scheduleId, Long userId, String queueToken) {
        long serviceStartTime = System.currentTimeMillis();

        // 토큰에서 concertId 추출 (형식: concertId:userId:counter)
        String concertId = QueueTokenExtractor.extractConcertId(queueToken);

        // Queue Service에 토큰 검증 요청 (캐시하면 안됨 - 보안)
        long queueValidationStart = System.currentTimeMillis();
        queueServiceClient.validateToken(concertId, userId, queueToken);
        long queueValidationTime = System.currentTimeMillis() - queueValidationStart;

        // 좌석 조회 (Redis 캐시 적용 - Response DTO 직접 반환)
        long cacheQueryStart = System.currentTimeMillis();
        var availableSeats = seatQueryCacheService.findAvailableSeats(scheduleId);
        long cacheQueryTime = System.currentTimeMillis() - cacheQueryStart;

        long totalServiceTime = System.currentTimeMillis() - serviceStartTime;

        log.info("Service timing - scheduleId: {}, total: {}ms, queueValidation: {}ms, cache: {}ms",
                scheduleId, totalServiceTime, queueValidationTime, cacheQueryTime);

        return availableSeats;
    }
}
