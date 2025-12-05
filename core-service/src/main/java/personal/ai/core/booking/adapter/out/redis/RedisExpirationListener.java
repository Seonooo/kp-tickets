package personal.ai.core.booking.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.domain.service.BookingManager;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisExpirationListener implements MessageListener {

    private static final String RESERVATION_PREFIX = "reservation:";
    private final BookingManager bookingManager;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = new String(message.getBody(), StandardCharsets.UTF_8);
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
            String reservationIdStr = expiredKey.substring(RESERVATION_PREFIX.length());
            Long reservationId = Long.parseLong(reservationIdStr);

            log.info("Processing reservation expiration: reservationId={}", reservationId);

            // 1. 트랜잭션 처리 (DB 작업)
            bookingManager.expireReservation(reservationId);

        } catch (Exception e) {
            log.error("Failed to handle reservation expiration: key={}", expiredKey, e);
        }
    }
}
