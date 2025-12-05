package personal.ai.core.booking.application.port.out;

import personal.ai.core.booking.domain.model.OutboxEvent;

import java.util.List;

/**
 * Outbox Event Repository (Output Port)
 * Transactional Outbox Pattern을 위한 이벤트 저장소 인터페이스
 */
public interface OutboxEventRepository {

    /**
     * Outbox 이벤트 저장
     */
    OutboxEvent save(OutboxEvent outboxEvent);

    /**
     * 발행 대기 중인 이벤트 조회 (Locking 등 고려 가능)
     * 
     * @return PENDING 상태의 이벤트 목록
     */
    List<OutboxEvent> findPendingEvents();
}
