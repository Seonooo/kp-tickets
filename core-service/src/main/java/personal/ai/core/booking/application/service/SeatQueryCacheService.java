package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import personal.ai.core.booking.adapter.in.web.dto.SeatResponse;
import personal.ai.core.booking.application.port.out.SeatRepository;
import personal.ai.core.booking.domain.model.Seat;

import java.util.List;

/**
 * Seat Query Cache Service
 *
 * Spring AOP 프록시를 위해 별도 컴포넌트로 분리
 * (Self-invocation 문제 해결)
 *
 * 최적화: Response DTO를 직접 캐싱하여 직렬화/역직렬화 오버헤드 최소화
 * 
 * 캐시 전략:
 * - 조회: @Cacheable로 1초 TTL 캐싱
 * - 무효화: 좌석 예약 시 @CacheEvict로 즉시 무효화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatQueryCacheService {

    private final SeatRepository seatRepository;

    /**
     * 예약 가능 좌석 조회 (Redis 캐싱 - Response DTO 직접 캐싱)
     *
     * 성능 최적화:
     * - SeatResponse (DTO)를 직접 캐싱하여 Entity → DTO 변환 제거
     * - 역직렬화 후 바로 HTTP 응답 가능
     * - TTL: 1초 (티켓팅 실시간성 보장)
     */
    @Cacheable(value = "availableSeats", key = "#scheduleId")
    public List<SeatResponse> findAvailableSeats(Long scheduleId) {
        long startTime = System.currentTimeMillis();
        log.info("Cache MISS - Loading available seats from DB: scheduleId={}", scheduleId);

        List<Seat> seats = seatRepository.findAvailableByScheduleId(scheduleId);

        long dbQueryTime = System.currentTimeMillis() - startTime;

        // DTO 변환 (캐시 저장 전 1회만 수행)
        long mappingStart = System.currentTimeMillis();
        List<SeatResponse> response = seats.stream()
                .map(SeatResponse::from)
                .toList();
        long mappingTime = System.currentTimeMillis() - mappingStart;

        log.info("DB query completed - scheduleId: {}, seatCount: {}, dbQueryTime: {}ms, mappingTime: {}ms",
                scheduleId, seats.size(), dbQueryTime, mappingTime);

        return response;
    }

    /**
     * 좌석 예약 시 캐시 무효화
     * 
     * 좌석 예약이 완료되면 해당 스케줄의 availableSeats 캐시를 즉시 삭제
     * → 다음 조회 시 DB에서 최신 데이터 로드
     * 
     * @param scheduleId 예약된 좌석의 스케줄 ID
     */
    @CacheEvict(value = "availableSeats", key = "#scheduleId")
    public void evictAvailableSeatsCache(Long scheduleId) {
        log.debug("Evicting availableSeats cache for scheduleId={}", scheduleId);
    }
}
