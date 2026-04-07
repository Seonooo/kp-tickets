package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import personal.ai.core.booking.application.port.in.PublishPendingEventsUseCase;
import personal.ai.core.booking.application.port.out.OutboxEventRepository;
import personal.ai.core.booking.application.port.out.ReservationEventPublisher;
import personal.ai.core.booking.domain.model.OutboxEvent;

import java.util.List;

/**
 * Outbox Event Service (Application Service)
 * 대기 중인 이벤트를 발행 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService implements PublishPendingEventsUseCase {

    public static final int MAX_RETRY_COUNT = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final ReservationEventPublisher eventPublisher;

    @Override
    @Transactional
    public int publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents();
        int publishedCount = 0;

        for (OutboxEvent event : pendingEvents) {
            try {
                String topic = mapEventTypeToTopic(event.eventType());
                String key = String.valueOf(event.aggregateId());

                log.debug("Publishing event: id={}, type={}, topic={}", event.id(), event.eventType(), topic);

                eventPublisher.publishRaw(topic, key, event.payload());

                OutboxEvent publishedEvent = event.markAsPublished();
                outboxEventRepository.save(publishedEvent);
                publishedCount++;

            } catch (Exception e) {
                log.error("Failed to publish event: id={}", event.id(), e);

                OutboxEvent retriedEvent = event.incrementRetryCount();

                if (retriedEvent.retryCount() >= MAX_RETRY_COUNT) {
                    retriedEvent = retriedEvent.markAsFailed();
                }

                outboxEventRepository.save(retriedEvent);
            }
        }
        return publishedCount;
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
