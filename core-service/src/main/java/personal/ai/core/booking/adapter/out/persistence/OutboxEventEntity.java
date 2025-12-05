package personal.ai.core.booking.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import personal.ai.core.booking.domain.model.OutboxEvent;

import java.time.LocalDateTime;

/**
 * Outbox Event Entity
 * Transactional Outbox Pattern을 위한 이벤트 저장소
 */
@Entity
@Table(name = "outbox_events", indexes = {
        @Index(name = "idx_status_created", columnList = "status, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType; // "RESERVATION"

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId; // reservationId

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType; // "RESERVATION_CREATED"

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    public static OutboxEventEntity create(String aggregateType, Long aggregateId,
            String eventType, String payload) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.aggregateType = aggregateType;
        entity.aggregateId = aggregateId;
        entity.eventType = eventType;
        entity.payload = payload;
        entity.status = OutboxEventStatus.PENDING;
        entity.retryCount = 0;
        return entity;
    }

    /**
     * Domain 모델로부터 Entity 생성
     */
    public static OutboxEventEntity fromDomain(OutboxEvent domain) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.id = domain.id();
        entity.aggregateType = domain.aggregateType();
        entity.aggregateId = domain.aggregateId();
        entity.eventType = domain.eventType();
        entity.payload = domain.payload();
        entity.status = OutboxEventStatus.valueOf(domain.status().name());
        entity.createdAt = domain.createdAt();
        entity.publishedAt = domain.publishedAt();
        entity.retryCount = domain.retryCount();
        return entity;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = OutboxEventStatus.PENDING;
        }
    }

    // Entity 내부 메서드 제거 (Domain 로직으로 이동)
    // markAsPublished, markAsFailed, incrementRetryCount 제거

    /**
     * Domain 모델로 변환
     */
    public OutboxEvent toDomain() {
        return new OutboxEvent(
                id,
                aggregateType,
                aggregateId,
                eventType,
                payload,
                OutboxEvent.OutboxStatus.valueOf(status.name()),
                retryCount,
                createdAt,
                publishedAt);
    }

    public enum OutboxEventStatus {
        PENDING,
        PUBLISHED,
        FAILED
    }
}
