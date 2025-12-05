package personal.ai.core.booking.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.application.port.out.ReservationCacheRepository;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Redis Reservation Cache Adapter
 * Redis TTL 기반 예약 만료 처리 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisReservationCacheAdapter implements ReservationCacheRepository {

    private static final String RESERVATION_PREFIX = "reservation:";
    private final StringRedisTemplate redisTemplate;

    @Override
    public void setReservationTTL(Long reservationId, LocalDateTime expiresAt) {
        String key = RESERVATION_PREFIX + reservationId;

        // 현재 시간과의 차이를 초 단위로 계산
        Duration ttl = Duration.between(LocalDateTime.now(), expiresAt);

        if (ttl.isNegative() || ttl.isZero()) {
            log.warn("Invalid TTL for reservation: reservationId={}, expiresAt={}", reservationId, expiresAt);
            return;
        }

        // 더미 값으로 설정 (만료 이벤트를 위한 키 생성)
        redisTemplate.opsForValue().set(key, String.valueOf(reservationId), ttl);

        log.debug("Reservation TTL set: reservationId={}, ttl={}s", reservationId, ttl.getSeconds());
    }

    @Override
    public void removeReservation(Long reservationId) {
        String key = RESERVATION_PREFIX + reservationId;
        Boolean deleted = redisTemplate.delete(key);

        log.debug("Reservation cache removed: reservationId={}, deleted={}", reservationId, deleted);
    }
}
