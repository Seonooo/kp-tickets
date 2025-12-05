package personal.ai.core.booking.adapter.in.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.application.port.in.PublishPendingEventsUseCase;

/**
 * Outbox Event Scheduler (Driving Adapter)
 * 주기적으로 OutboxEventService를 호출하여 PENDING 상태의 이벤트를 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxEventScheduler {

    private final PublishPendingEventsUseCase publishPendingEventsUseCase;

    /**
     * 500ms마다 실행 (이전 작업 완료 후)
     * 짧은 주기로 실행하여 실시간성 확보
     */
    @Scheduled(fixedDelay = 500)
    public void schedulePublishing() {
        int publishedCount = publishPendingEventsUseCase.publishPendingEvents();
        if (publishedCount > 0) {
            log.debug("Scheduled publishing completed. Count: {}", publishedCount);
        }
    }
}
