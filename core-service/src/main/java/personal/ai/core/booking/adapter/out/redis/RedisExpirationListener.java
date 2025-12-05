package personal.ai.core.booking.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.domain.service.BookingManager;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisExpirationListener implements MessageListener {

    private static final String RESERVATION_PREFIX = "reservation:";
    private final BookingManager bookingManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        log.info("Redis key expired: {}", expiredKey);

        if (expiredKey.startsWith(RESERVATION_PREFIX)) {
            handleReservationExpiration(expiredKey);
        }
    }

    /**
     * 예약 만료 처리
     * BookingManager를 통해 트랜잭션 단위로 처리
     */
    private void handleReservationExpiration(String expiredKey) {
        try {
            // reservation:123 -> 123
            String reservationIdStr = expiredKey.replace(RESERVATION_PREFIX, "");
            Long reservationId = Long.parseLong(reservationIdStr);

            log.info("Processing reservation expiration: reservationId={}", reservationId);

            // 1. 트랜잭션 처리 (DB 작업)
            bookingManager.expireReservation(reservationId);

            // 2. Redis 락 해제 (DB 트랜잭션과 별도, Best Effort)
            // 필요한 경우 여기서 수행하거나 BookingManager 내부에서 SeatLockService 호출 가능
            // 하지만 SeatLockRepository는 redis 어댑터 쪽이므로 여기서 호출하는 게 의존성 상 깔끔할 수 있음
            // (단, reservation 정보가 있어야 함. 여기선 ID만 아므로...)
            // 일단은 DB 정합성이 최우선이므로 BookingManager 호출로 충분. Redis 락은 TTL로 만료됨.

        } catch (Exception e) {
            log.error("Failed to handle reservation expiration: key={}", expiredKey, e);
        }
    }
}
