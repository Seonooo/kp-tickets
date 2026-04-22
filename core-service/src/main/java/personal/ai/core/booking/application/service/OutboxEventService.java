package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.core.booking.application.port.in.PublishPendingEventsUseCase;
import personal.ai.core.booking.application.port.out.OutboxEventRepository;
import personal.ai.core.booking.domain.model.OutboxEvent;

import java.util.List;

/**
 * Outbox Event Service (Application Service)
 * 대기 중인 이벤트들을 조회하여 {@link OutboxEventProcessor}에 이벤트별 처리를 위임한다.
 *
 * <p>트랜잭션은 {@link OutboxEventProcessor}가 이벤트 1건 단위로 관리하므로
 * 본 서비스 메서드에는 {@code @Transactional}을 적용하지 않는다.
 * 이로써 한 이벤트의 DB 저장 실패가 이미 Kafka로 발행된 다른 이벤트의
 * 상태 업데이트까지 롤백시키는 문제를 방지한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxEventService implements PublishPendingEventsUseCase {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProcessor outboxEventProcessor;

    @Override
    public int publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents();
        int publishedCount = 0;

        for (OutboxEvent event : pendingEvents) {
            if (outboxEventProcessor.processEvent(event)) {
                publishedCount++;
            }
        }
        return publishedCount;
    }
}
