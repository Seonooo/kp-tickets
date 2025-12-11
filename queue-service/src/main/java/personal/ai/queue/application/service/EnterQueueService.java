package personal.ai.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.queue.application.port.in.EnterQueueUseCase;
import personal.ai.queue.application.port.out.QueueRepository;
import personal.ai.queue.domain.model.QueueConfig;
import personal.ai.queue.domain.model.QueuePosition;

/**
 * Enter Queue Service (SRP)
 * 단일 책임: 대기열 진입
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnterQueueService implements EnterQueueUseCase {

    private static final int POSITION_DISPLAY_OFFSET = 1;

    private final QueueRepository queueRepository;
    private final QueueConfig queueConfig;

    @Override
    public QueuePosition enter(EnterQueueCommand command) {
        long position = queueRepository.addToWaitQueue(command.concertId(), command.userId());
        long totalWaiting = queueRepository.getWaitQueueSize(command.concertId());

        var result = QueuePosition.calculate(
                command.concertId(),
                command.userId(),
                position + POSITION_DISPLAY_OFFSET,
                totalWaiting,
                queueConfig.activeMaxSize(),
                queueConfig.activationIntervalSeconds());

        log.debug("Queue entry completed: concertId={}, userId={}, position={}",
                command.concertId(), command.userId(), position + POSITION_DISPLAY_OFFSET);

        return result;
    }
}
