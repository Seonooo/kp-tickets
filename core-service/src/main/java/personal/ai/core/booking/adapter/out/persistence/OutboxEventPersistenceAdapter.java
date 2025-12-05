package personal.ai.core.booking.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.application.port.out.OutboxEventRepository;

import java.util.List;

/**
 * Outbox Event Persistence Adapter
 * OutboxEventRepository 구현체
 */
@Component
@RequiredArgsConstructor
public class OutboxEventPersistenceAdapter implements OutboxEventRepository {

    private static final int MAX_RETRY_COUNT = 3;

    private final JpaOutboxEventRepository jpaOutboxEventRepository;

    @Override
    public OutboxEventEntity save(OutboxEventEntity outboxEvent) {
        return jpaOutboxEventRepository.save(outboxEvent);
    }

    @Override
    public List<OutboxEventEntity> findPendingEvents() {
        return jpaOutboxEventRepository.findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
                OutboxEventEntity.OutboxEventStatus.PENDING,
                MAX_RETRY_COUNT);
    }
}
