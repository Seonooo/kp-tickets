package personal.ai.queue.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import personal.ai.queue.application.port.in.*;
import personal.ai.queue.application.port.out.QueueRepository;
import personal.ai.queue.domain.exception.QueueTokenExpiredException;
import personal.ai.queue.domain.exception.QueueTokenInvalidException;
import personal.ai.queue.domain.exception.QueueTokenNotFoundException;
import personal.ai.queue.domain.model.QueueConfig;
import personal.ai.queue.domain.model.QueuePosition;
import personal.ai.queue.domain.model.QueueStatus;
import personal.ai.queue.domain.model.QueueToken;
import personal.ai.queue.domain.service.QueueDomainService;

import java.time.Instant;
import java.util.List;

/**
 * Queue Application Service
 * 모든 Input Port(UseCase)를 구현하는 Application Service
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QueueService implements
        EnterQueueUseCase,
        GetQueueStatusUseCase,
        ActivateTokenUseCase,
        ExtendTokenUseCase,
        ValidateTokenUseCase,
        GetActiveConcertsUseCase,
        RemoveFromQueueUseCase {

    private final QueueRepository queueRepository;
    private final QueueDomainService domainService;
    private final QueueConfig queueConfig;

    // 상수
    private static final int POSITION_DISPLAY_OFFSET = 1; // 사용자에게 표시할 때 1-based로 변환
    private static final int INITIAL_EXTEND_COUNT = 0; // 최초 활성화 시 연장 횟수

    @Override
    public QueuePosition enter(EnterQueueCommand command) {
        log.info("User entering queue: concertId={}, userId={}",
                command.concertId(), command.userId());

        // Wait Queue에 추가
        Long position = queueRepository.addToWaitQueue(
                command.concertId(),
                command.userId()
        );

        // 전체 대기 인원 조회
        Long totalWaiting = queueRepository.getWaitQueueSize(command.concertId());

        // 예상 대기 시간 계산
        QueuePosition queuePosition = QueuePosition.calculate(
                command.concertId(),
                command.userId(),
                position + POSITION_DISPLAY_OFFSET,
                totalWaiting,
                queueConfig.activeMaxSize(),
                queueConfig.activationIntervalSeconds()
        );

        log.info("User entered queue: position={}, totalWaiting={}",
                position + POSITION_DISPLAY_OFFSET, totalWaiting);

        return queuePosition;
    }

    @Override
    public QueueToken getStatus(GetQueueStatusQuery query) {
        log.debug("Getting queue status: concertId={}, userId={}",
                query.concertId(), query.userId());

        // Active Queue 확인
        var activeToken = queueRepository.getActiveToken(
                query.concertId(),
                query.userId()
        );

        if (activeToken.isPresent()) {
            QueueToken token = activeToken.get();

            // 만료 확인
            if (token.isExpired()) {
                log.warn("Token expired: concertId={}, userId={}",
                        query.concertId(), query.userId());
                return QueueToken.expired(query.concertId(), query.userId());
            }

            return token;
        }

        // Wait Queue 확인
        Long position = queueRepository.getWaitQueuePosition(
                query.concertId(),
                query.userId()
        );

        if (position != null) {
            return QueueToken.waiting(
                    query.concertId(),
                    query.userId(),
                    position + POSITION_DISPLAY_OFFSET
            );
        }

        // 둘 다 없으면 NOT_FOUND
        return QueueToken.notFound(query.concertId(), query.userId());
    }

    @Override
    public QueueToken activate(ActivateTokenCommand command) {
        log.info("Activating token: concertId={}, userId={}",
                command.concertId(), command.userId());

        // Active Token 조회 (토큰 값 획득용)
        QueueToken token = queueRepository.getActiveToken(
                        command.concertId(),
                        command.userId())
                .orElseThrow(() -> new QueueTokenNotFoundException(
                        command.concertId(),
                        command.userId()
                ));

        // 이미 ACTIVE 상태면 그대로 반환
        if (token.status() == QueueStatus.ACTIVE) {
            log.debug("Token already active: concertId={}, userId={}",
                    command.concertId(), command.userId());
            return token;
        }

        // READY -> ACTIVE 전환 및 만료 시간 갱신 (원자적)
        Instant newExpiration = domainService.calculateActiveExpiration();
        boolean success = queueRepository.activateTokenAtomic(
                command.concertId(),
                command.userId(),
                newExpiration
        );

        if (!success) {
            log.error("Failed to activate token atomically: concertId={}, userId={}",
                    command.concertId(), command.userId());
            throw new QueueTokenNotFoundException(command.concertId(), command.userId());
        }

        log.info("Token activated atomically: concertId={}, userId={}, expiredAt={}",
                command.concertId(), command.userId(), newExpiration);

        return QueueToken.active(
                command.concertId(),
                command.userId(),
                token.token(),
                newExpiration,
                INITIAL_EXTEND_COUNT
        );
    }

    @Override
    public QueueToken extend(ExtendTokenCommand command) {
        log.info("Extending token: concertId={}, userId={}",
                command.concertId(), command.userId());

        // Active Token 조회
        QueueToken token = queueRepository.getActiveToken(
                        command.concertId(),
                        command.userId())
                .orElseThrow(() -> new QueueTokenNotFoundException(
                        command.concertId(),
                        command.userId()
                ));

        // 도메인 검증 (연장 가능 여부, 활성 상태 확인)
        domainService.validateExtension(token);

        // 연장 횟수 증가
        Integer newExtendCount = queueRepository.incrementExtendCount(
                command.concertId(),
                command.userId()
        );

        // 만료 시간 갱신 (10분 연장)
        Instant newExpiration = domainService.calculateActiveExpiration();
        queueRepository.updateTokenExpiration(
                command.concertId(),
                command.userId(),
                newExpiration
        );

        log.info("Token extended: concertId={}, userId={}, extendCount={}, expiredAt={}",
                command.concertId(), command.userId(), newExtendCount, newExpiration);

        return QueueToken.active(
                command.concertId(),
                command.userId(),
                token.token(),
                newExpiration,
                newExtendCount
        );
    }

    @Override
    public void validate(ValidateTokenQuery query) {
        log.debug("Validating token: concertId={}, userId={}",
                query.concertId(), query.userId());

        // Active Token 조회
        QueueToken token = queueRepository.getActiveToken(
                        query.concertId(),
                        query.userId())
                .orElseThrow(() -> new QueueTokenNotFoundException(
                        query.concertId(),
                        query.userId()
                ));

        // 토큰 값 검증
        if (!token.token().equals(query.token())) {
            throw new QueueTokenInvalidException(query.concertId(), query.userId());
        }

        // 만료 여부 확인
        if (token.isExpired()) {
            throw new QueueTokenExpiredException(query.concertId(), query.userId());
        }

        // 활성 상태 확인
        if (!token.isActive()) {
            throw new QueueTokenInvalidException(query.concertId(), query.userId());
        }

        log.debug("Token validated successfully: concertId={}, userId={}",
                query.concertId(), query.userId());
    }

    @Override
    public List<String> getActiveConcerts() {
        return queueRepository.getActiveConcertIds();
    }

    @Override
    public void removeFromQueue(RemoveFromQueueCommand command) {
        log.info("Removing user from queue: concertId={}, userId={}",
                command.concertId(), command.userId());

        queueRepository.removeFromActiveQueue(command.concertId(), command.userId());

        log.info("User removed from queue: concertId={}, userId={}",
                command.concertId(), command.userId());
    }
}
