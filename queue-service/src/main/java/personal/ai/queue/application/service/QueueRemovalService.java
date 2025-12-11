package personal.ai.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.queue.application.port.in.RemoveFromQueueUseCase;
import personal.ai.queue.application.port.out.QueueRepository;

/**
 * Queue Removal Service (SRP)
 * 단일 책임: 대기열에서 사용자 제거
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueRemovalService implements RemoveFromQueueUseCase {

    private final QueueRepository queueRepository;

    @Override
    public void removeFromQueue(RemoveFromQueueCommand command) {
        queueRepository.removeFromActiveQueue(command.concertId(), command.userId());

        log.debug("User removed from queue: concertId={}, userId={}", command.concertId(), command.userId());
    }
}
