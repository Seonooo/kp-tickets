package personal.ai.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.queue.application.port.in.ActivateTokenUseCase;
import personal.ai.queue.application.port.out.QueueRepository;
import personal.ai.queue.domain.exception.QueueTokenNotFoundException;
import personal.ai.queue.domain.model.QueueStatus;
import personal.ai.queue.domain.model.QueueToken;
import personal.ai.queue.domain.service.QueueDomainService;

import java.time.Instant;

/**
 * Token Activation Service (SRP)
 * 단일 책임: 토큰 활성화 (READY -> ACTIVE)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenActivationService implements ActivateTokenUseCase {

    private static final int INITIAL_EXTEND_COUNT = 0;

    private final QueueRepository queueRepository;
    private final QueueDomainService domainService;

    @Override
    public QueueToken activate(ActivateTokenCommand command) {
        var token = queueRepository.getActiveToken(command.concertId(), command.userId())
                .orElseThrow(() -> {
                    log.warn("Token not found for activation: concertId={}", command.concertId());
                    return new QueueTokenNotFoundException(command.concertId(), command.userId());
                });

        if (token.status() == QueueStatus.ACTIVE) {
            log.debug("Token already active: concertId={}, userId={}", command.concertId(), command.userId());
            return token;
        }

        Instant newExpiration = domainService.calculateActiveExpiration();
        boolean success = queueRepository.activateTokenAtomic(command.concertId(), command.userId(), newExpiration);

        if (!success) {
            log.warn("Token activation failed: concertId={}", command.concertId());
            throw new QueueTokenNotFoundException(command.concertId(), command.userId());
        }

        log.debug("Token activated: concertId={}, userId={}", command.concertId(), command.userId());

        return QueueToken.active(command.concertId(), command.userId(), token.token(), newExpiration,
                INITIAL_EXTEND_COUNT);
    }
}
