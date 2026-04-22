package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import personal.ai.core.booking.application.port.out.SeatRepository;
import personal.ai.core.booking.domain.model.Seat;

import java.util.List;

/**
 * Seat Query Cache Service
 *
 * Spring AOP 프록시를 위해 별도 컴포넌트로 분리
 * (Self-invocation 문제 해결)
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
     * 예약 가능 좌석 조회 (Redis 캐싱)
     * TTL: 1초 (티켓팅 실시간성 보장)
     */
    @Cacheable(value = "availableSeats", key = "#scheduleId")
    public List<Seat> findAvailableSeats(Long scheduleId) {
        log.info("Cache MISS - Loading available seats from DB: scheduleId={}", scheduleId);
        List<Seat> seats = seatRepository.findAvailableByScheduleId(scheduleId);
        log.info("DB query completed - scheduleId={}, seatCount={}", scheduleId, seats.size());
        return seats;
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
