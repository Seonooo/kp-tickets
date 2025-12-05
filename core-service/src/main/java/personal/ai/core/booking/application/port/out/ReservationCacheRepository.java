package personal.ai.core.booking.application.port.out;

import java.time.LocalDateTime;

/**
 * Reservation Cache Repository (Output Port)
 * Redis 기반 예약 TTL 관리 인터페이스
 */
public interface ReservationCacheRepository {

    /**
     * 예약 TTL 설정 (만료 처리용)
     * Redis Keyspace Notification을 위한 TTL 설정
     *
     * @param reservationId 예약 ID
     * @param expiresAt 만료 시간
     */
    void setReservationTTL(Long reservationId, LocalDateTime expiresAt);

    /**
     * 예약 캐시 제거
     *
     * @param reservationId 예약 ID
     */
    void removeReservation(Long reservationId);
}
