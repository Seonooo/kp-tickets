package personal.ai.core.booking.adapter.out.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.application.port.out.ReservationRepository;
import personal.ai.core.booking.application.port.out.SeatLockRepository;
import personal.ai.core.booking.application.port.out.SeatRepository;
import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.booking.domain.model.ReservationStatus;
import personal.ai.core.booking.domain.model.Seat;

/**
 * Redis Keyspace Notification Listener
 * Redis TTL 만료 이벤트를 감지하여 예약 만료 처리
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisExpirationListener implements MessageListener {

    private static final String RESERVATION_PREFIX = "reservation:";
    private final ReservationRepository reservationRepository;
    private final SeatRepository seatRepository;
    private final SeatLockRepository seatLockRepository;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String expiredKey = message.toString();
        log.info("Redis key expired: {}", expiredKey);

        // reservation: 프리픽스를 가진 키만 처리
        if (expiredKey.startsWith(RESERVATION_PREFIX)) {
            handleReservationExpiration(expiredKey);
        }
    }

    /**
     * 예약 만료 처리
     * 1. 예약 상태를 EXPIRED로 변경
     * 2. 좌석 상태를 AVAILABLE로 변경
     * 3. Redis 락 해제
     */
    private void handleReservationExpiration(String expiredKey) {
        try {
            // reservation:123 -> 123 추출
            String reservationIdStr = expiredKey.replace(RESERVATION_PREFIX, "");
            Long reservationId = Long.parseLong(reservationIdStr);

            log.info("Processing reservation expiration: reservationId={}", reservationId);

            // 1. 예약 조회
            Reservation reservation = reservationRepository.findById(reservationId)
                    .orElse(null);

            if (reservation == null) {
                log.warn("Reservation not found for expiration: reservationId={}", reservationId);
                return;
            }

            // 2. 이미 CONFIRMED 상태라면 처리 안함 (결제 완료된 예약)
            if (reservation.status() == ReservationStatus.CONFIRMED) {
                log.debug("Reservation already confirmed, skipping expiration: reservationId={}", reservationId);
                return;
            }

            // 3. 예약 상태를 EXPIRED로 변경
            if (reservation.status() == ReservationStatus.PENDING) {
                Reservation expiredReservation = reservation.expire();
                reservationRepository.save(expiredReservation);
                log.info("Reservation expired: reservationId={}", reservationId);
            }

            // 4. 좌석 상태를 AVAILABLE로 변경
            Seat seat = seatRepository.findById(reservation.seatId()).orElse(null);
            if (seat != null) {
                Seat releasedSeat = seat.release();
                seatRepository.save(releasedSeat);
                log.info("Seat released: seatId={}", reservation.seatId());
            }

            // 5. Redis 락 해제 (혹시 남아있을 수 있으므로)
            // 5. Redis 락 해제 (혹시 남아있을 수 있으므로)
            seatLockRepository.unlock(reservation.seatId(), reservation.userId());

        } catch (Exception e) {
            log.error("Failed to handle reservation expiration: key={}", expiredKey, e);
        }
    }
}
