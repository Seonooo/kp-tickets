package personal.ai.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.queue.application.port.in.ValidateTokenUseCase;
import personal.ai.queue.application.port.out.QueueRepository;
import personal.ai.queue.domain.exception.QueueTokenNotFoundException;

/**
 * Token Validation Service (SRP)
 * 단일 책임: 토큰 유효성 검증
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TokenValidationService implements ValidateTokenUseCase {

    private final QueueRepository queueRepository;

    @Override
    public void validate(ValidateTokenQuery query) {
        var token = queueRepository.getActiveToken(query.concertId(), query.userId())
                .orElseThrow(() -> {
                    log.warn("Token not found: concertId={}", query.concertId());
                    return new QueueTokenNotFoundException(query.concertId(), query.userId());
                });

        token.ensureValidFor(query.token());

        log.debug("Token validated: concertId={}, userId={}", query.concertId(), query.userId());
    }
}
