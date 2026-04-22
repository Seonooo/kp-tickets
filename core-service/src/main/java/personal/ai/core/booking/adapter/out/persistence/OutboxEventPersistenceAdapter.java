package personal.ai.core.booking.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.application.port.out.OutboxEventRepository;
import personal.ai.core.booking.domain.model.OutboxEvent;
import personal.ai.core.booking.domain.model.OutboxPolicy;

import java.util.List;

/**
 * Outbox Event Persistence Adapter
 * OutboxEventRepository 구현체
 */
@Component
@RequiredArgsConstructor
public class OutboxEventPersistenceAdapter implements OutboxEventRepository {

    private final JpaOutboxEventRepository jpaOutboxEventRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        OutboxEventEntity entity = OutboxEventEntity.fromDomain(outboxEvent);
        OutboxEventEntity saved = jpaOutboxEventRepository.save(entity);
        return saved.toDomain();
    }

    @Override
    public List<OutboxEvent> findPendingEvents() {
        return jpaOutboxEventRepository.findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                OutboxEventEntity.OutboxEventStatus.PENDING,
                OutboxPolicy.MAX_RETRY_COUNT)
                .stream()
                .map(OutboxEventEntity::toDomain)
                .collect(java.util.stream.Collectors.toList());
    }
}
