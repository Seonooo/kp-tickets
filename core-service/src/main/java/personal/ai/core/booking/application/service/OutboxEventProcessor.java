package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import personal.ai.core.booking.application.port.out.OutboxEventRepository;
import personal.ai.core.booking.application.port.out.ReservationEventPublisher;
import personal.ai.core.booking.domain.model.OutboxEvent;
import personal.ai.core.booking.domain.model.OutboxPolicy;

/**
 * Outbox Event Processor
 * 이벤트 1건을 독립 트랜잭션으로 처리한다.
 *
 * <p>Self-invocation 문제를 피하기 위해 {@link OutboxEventService}와 별도 클래스로 분리됨.
 * {@code REQUIRES_NEW}를 통해 상위 호출자의 트랜잭션과 격리하여,
 * 한 이벤트의 DB 저장 실패가 다른 이벤트 처리에 영향을 주지 않도록 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventProcessor {

    private final OutboxEventRepository outboxEventRepository;
    private final ReservationEventPublisher eventPublisher;

    /**
     * 단일 이벤트 발행 및 상태 업데이트.
     *
     * @return 발행 성공 여부
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean processEvent(OutboxEvent event) {
        try {
            String topic = mapEventTypeToTopic(event.eventType());
            String key = String.valueOf(event.aggregateId());

            log.debug("Publishing event: id={}, type={}, topic={}", event.id(), event.eventType(), topic);

            eventPublisher.publishRaw(topic, key, event.payload());

            outboxEventRepository.save(event.markAsPublished());
            return true;

        } catch (Exception e) {
            log.error("Failed to publish event: id={}", event.id(), e);

            OutboxEvent retriedEvent = event.incrementRetryCount();
            if (retriedEvent.retryCount() >= OutboxPolicy.MAX_RETRY_COUNT) {
                retriedEvent = retriedEvent.markAsFailed();
            }
            outboxEventRepository.save(retriedEvent);
            return false;
        }
    }

    private String mapEventTypeToTopic(String eventType) {
        return switch (eventType) {
            case "RESERVATION_CREATED" -> "reservation.created";
            case "RESERVATION_CONFIRMED" -> "reservation.confirmed";
            case "RESERVATION_CANCELLED" -> "reservation.cancelled";
            case "RESERVATION_EXPIRED" -> "reservation.expired";
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
