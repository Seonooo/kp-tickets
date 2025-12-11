package personal.ai.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.queue.application.port.in.GetActiveConcertsUseCase;
import personal.ai.queue.application.port.in.GetQueueStatusUseCase;
import personal.ai.queue.application.port.out.QueueRepository;
import personal.ai.queue.domain.model.QueueToken;

import java.util.List;

/**
 * Queue Status Query Service (SRP)
 * 단일 책임: 대기열 상태 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueStatusQueryService implements GetQueueStatusUseCase, GetActiveConcertsUseCase {

    private static final int POSITION_DISPLAY_OFFSET = 1;

    private final QueueRepository queueRepository;

    @Override
    public QueueToken getStatus(GetQueueStatusQuery query) {
        var activeToken = queueRepository.getActiveToken(query.concertId(), query.userId());

        if (activeToken.isPresent()) {
            var token = activeToken.get();
            if (token.isExpired()) {
                log.debug("Token expired: concertId={}, userId={}", query.concertId(), query.userId());
                return QueueToken.expired(query.concertId(), query.userId());
            }
            return token;
        }

        Long position = queueRepository.getWaitQueuePosition(query.concertId(), query.userId());
        if (position != null) {
            return QueueToken.waiting(query.concertId(), query.userId(), position + POSITION_DISPLAY_OFFSET);
        }

        log.debug("Token not found in queue: concertId={}, userId={}", query.concertId(), query.userId());
        return QueueToken.notFound(query.concertId(), query.userId());
    }

    @Override
    public List<String> getActiveConcerts() {
        return queueRepository.getActiveConcertIds();
    }
}
