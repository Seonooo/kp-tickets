package personal.ai.core.booking.application.port.out;

import personal.ai.core.booking.domain.model.Reservation;

/**
 * Reservation Event Publisher (Output Port)
 * Kafka 이벤트 발행 인터페이스
 */
public interface ReservationEventPublisher {

    /**
     * 예약 생성 이벤트 발행
     * Topic: booking.reservation.created
     *
     * @param reservation 생성된 예약 정보
     */
    void publishReservationCreated(Reservation reservation);

    /**
     * 예약 확정 이벤트 발행 (결제 완료)
     * Topic: booking.reservation.confirmed
     *
     * @param reservation 확정된 예약 정보
     */
    void publishReservationConfirmed(Reservation reservation);

    /**
     * 예약 취소 이벤트 발행
     * Topic: booking.reservation.cancelled
     *
     * @param reservation 취소된 예약 정보
     */
    void publishReservationCancelled(Reservation reservation);

    /**
     * 예약 만료 이벤트 발행
     * Topic: booking.reservation.expired
     *
     * @param reservation 만료된 예약 정보
     */
    void publishReservationExpired(Reservation reservation);

    /**
     * Raw Event 발행 (Outbox Pattern용)
     * 이미 직렬화된 JSON Payload를 그대로 발행
     *
     * @param topic   발행할 Kafka 토픽
     * @param key     메시지 키 (순서 보장용, e.g. reservationId)
     * @param payload 메시지 본문 (JSON String)
     */
    void publishRaw(String topic, String key, String payload);
}
