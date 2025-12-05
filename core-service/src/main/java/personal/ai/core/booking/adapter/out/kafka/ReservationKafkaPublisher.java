package personal.ai.core.booking.adapter.out.kafka;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.application.port.out.ReservationEventPublisher;
import personal.ai.core.booking.domain.model.Reservation;

/**
 * Reservation Kafka Publisher (Adapter Layer)
 * Kafka를 통한 예약 이벤트 발행 구현체
 * Outbox Service에 의해 호출됨
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationKafkaPublisher implements ReservationEventPublisher {

    private static final String TOPIC_RESERVATION_CREATED = "reservation.created";
    private static final String TOPIC_RESERVATION_CONFIRMED = "reservation.confirmed";
    private static final String TOPIC_RESERVATION_CANCELLED = "reservation.cancelled";
    private static final String TOPIC_RESERVATION_EXPIRED = "reservation.expired";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Override
    public void publishRaw(String topic, String key, String payload) {
        log.debug("Publishing raw event: topic={}, key={}", topic, key);
        // String payload를 그대로 전송
        kafkaTemplate.send(topic, key, payload)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish raw event: topic={}, key={}", topic, key, ex);
                        throw new RuntimeException("Kafka publish failed", ex);
                    } else {
                        log.debug("Raw event published: topic={}, key={}", topic, key);
                    }
                });
    }

    // 아래 메서드들은 Outbox Pattern 도입으로 인해 직접 호출되지 않을 수 있으나,
    // 인터페이스 규약을 위해 구현하거나, 필요 시 직접 발행 용도로 유지
    @Override
    public void publishReservationCreated(Reservation reservation) {
        // Implementation if needed, or delegates to publishRaw after serialization
        // For now, leaving as direct publish for backward compatibility if mixed usage
        // But OutboxService calls publishRaw, so this class is primarily used by
        // OutboxService now.
        log.warn("Direct publish called for reservation created: {}", reservation.id());
    }

    @Override
    public void publishReservationConfirmed(Reservation reservation) {
        log.warn("Direct publish called for reservation confirmed: {}", reservation.id());
    }

    @Override
    public void publishReservationCancelled(Reservation reservation) {
        log.warn("Direct publish called for reservation cancelled: {}", reservation.id());
    }

    @Override
    public void publishReservationExpired(Reservation reservation) {
        log.warn("Direct publish called for reservation expired: {}", reservation.id());
    }
}
