package personal.ai.core.booking.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA Repository for OutboxEvent
 */
public interface JpaOutboxEventRepository extends JpaRepository<OutboxEventEntity, Long> {

    /**
     * Aggregate ID로 이벤트 조회
     */
    List<OutboxEventEntity> findByAggregateTypeAndAggregateId(String aggregateType, Long aggregateId);

    /**
     * 발행 대기 중인 이벤트 조회 (재시도 횟수 제한)
     */
    List<OutboxEventEntity> findByStatusAndRetryCountLessThanOrderByCreatedAtAsc(
            OutboxEventEntity.OutboxEventStatus status,
            int maxRetryCount
    );

    /**
     * 오래된 발행 완료 이벤트 삭제용 조회
     */
    List<OutboxEventEntity> findByStatusAndPublishedAtBefore(
            OutboxEventEntity.OutboxEventStatus status,
            LocalDateTime publishedBefore
    );
}
