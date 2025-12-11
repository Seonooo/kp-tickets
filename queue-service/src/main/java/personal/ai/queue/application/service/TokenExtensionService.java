package personal.ai.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.queue.application.port.in.ExtendTokenUseCase;
import personal.ai.queue.application.port.out.QueueRepository;
import personal.ai.queue.domain.exception.QueueTokenNotFoundException;
import personal.ai.queue.domain.model.QueueToken;
import personal.ai.queue.domain.service.QueueDomainService;

import java.time.Instant;

/**
 * Token Extension Service (SRP)
 * 단일 책임: 토큰 연장
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenExtensionService implements ExtendTokenUseCase {

    private final QueueRepository queueRepository;
    private final QueueDomainService domainService;

    @Override
    public QueueToken extend(ExtendTokenCommand command) {
        var token = queueRepository.getActiveToken(command.concertId(), command.userId())
                .orElseThrow(() -> {
                    log.warn("Token not found for extension: concertId={}", command.concertId());
                    return new QueueTokenNotFoundException(command.concertId(), command.userId());
                });

        token.ensureCanExtend();

        int newExtendCount = queueRepository.incrementExtendCount(command.concertId(), command.userId());
        Instant newExpiration = domainService.calculateActiveExpiration();
        queueRepository.updateTokenExpiration(command.concertId(), command.userId(), newExpiration);

        log.debug("Token extended: concertId={}, userId={}, extendCount={}",
                command.concertId(), command.userId(), newExtendCount);

        return token.withExtension(newExpiration, newExtendCount);
    }
}
