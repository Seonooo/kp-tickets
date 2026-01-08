package personal.ai.queue.application.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.queue.application.port.in.RemoveFromQueueUseCase;
import personal.ai.queue.application.port.out.QueueRepository;

/**
 * Queue Removal Service (SRP)
 * 단일 책임: 대기열에서 사용자 제거
 * Phase 4: Exit Rate 메트릭 추가
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueRemovalService implements RemoveFromQueueUseCase {

    private final QueueRepository queueRepository;
    private final MeterRegistry meterRegistry;

    @Override
    public void removeFromQueue(RemoveFromQueueCommand command) {
        // Active Queue에서 제거
        queueRepository.removeFromActiveQueue(command.concertId(), command.userId());

        // Exit Rate 메트릭 기록 (Phase 4: Queue 순환 테스트용)
        Counter.builder("queue.exit.count")
                .tag("concert_id", command.concertId())
                .tag("service", "queue-service")
                .description("Number of users exited from Active Queue")
                .register(meterRegistry)
                .increment();

        log.info("User removed from Active Queue: concertId={}, userId={}",
                command.concertId(), command.userId());
    }
}
