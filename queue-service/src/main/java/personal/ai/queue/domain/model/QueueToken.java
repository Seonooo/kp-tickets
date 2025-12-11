package personal.ai.queue.domain.model;

import personal.ai.queue.domain.exception.QueueExtensionLimitExceededException;
import personal.ai.queue.domain.exception.QueueTokenExpiredException;
import personal.ai.queue.domain.exception.QueueTokenInvalidException;

import java.time.Instant;

/**
 * 대기열 토큰 (Value Object)
 * 불변 객체로 대기열 토큰 정보를 담음
 */
public record QueueToken(
        String concertId,
        String userId,
        String token,
        QueueStatus status,
        Long position,
        Instant expiredAt,
        Integer extendCount) {
    // 상수
    private static final int INITIAL_EXTEND_COUNT = 0;
    private static final int MAX_EXTENSION_COUNT = 2;

    /**
     * 대기 중 토큰 생성 (Wait Queue)
     */
    public static QueueToken waiting(String concertId, String userId, Long position) {
        return new QueueToken(
                concertId,
                userId,
                null,
                QueueStatus.WAITING,
                position,
                null,
                INITIAL_EXTEND_COUNT);
    }

    /**
     * 활성화 대기 토큰 생성 (Active Queue - Ready 상태)
     */
    public static QueueToken ready(String concertId, String userId, String token, Instant expiredAt) {
        return new QueueToken(
                concertId,
                userId,
                token,
                QueueStatus.READY,
                null,
                expiredAt,
                INITIAL_EXTEND_COUNT);
    }

    /**
     * 활동 중 토큰 생성 (Active Queue - Active 상태)
     */
    public static QueueToken active(String concertId, String userId, String token, Instant expiredAt,
            Integer extendCount) {
        return new QueueToken(
                concertId,
                userId,
                token,
                QueueStatus.ACTIVE,
                null,
                expiredAt,
                extendCount);
    }

    /**
     * 만료된 토큰
     */
    public static QueueToken expired(String concertId, String userId) {
        return new QueueToken(
                concertId,
                userId,
                null,
                QueueStatus.EXPIRED,
                null,
                null,
                null);
    }

    /**
     * 존재하지 않는 토큰
     */
    public static QueueToken notFound(String concertId, String userId) {
        return new QueueToken(
                concertId,
                userId,
                null,
                QueueStatus.NOT_FOUND,
                null,
                null,
                null);
    }

    /**
     * 연장 가능 여부 확인 (최대 2회)
     */
    public boolean canExtend() {
        return extendCount != null && extendCount < MAX_EXTENSION_COUNT;
    }

    /**
     * 활성 상태인지 확인
     */
    public boolean isActive() {
        return status == QueueStatus.ACTIVE || status == QueueStatus.READY;
    }

    /**
     * 만료되었는지 확인
     */
    public boolean isExpired() {
        if (expiredAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiredAt);
    }

    // ========== Domain Validation Methods (Tell, Don't Ask) ==========

    /**
     * 토큰 유효성 검증 (토큰값 + 만료 + 활성상태)
     * 서비스 레이어의 조건 분기를 도메인으로 위임
     *
     * @param requestedToken 요청된 토큰 값
     * @throws QueueTokenInvalidException 토큰 불일치 또는 비활성 상태
     * @throws QueueTokenExpiredException 만료된 토큰
     */
    public void ensureValidFor(String requestedToken) {
        if (!this.token.equals(requestedToken)) {
            throw new QueueTokenInvalidException(concertId, userId);
        }

        if (isExpired()) {
            throw new QueueTokenExpiredException(concertId, userId);
        }

        if (!isActive()) {
            throw new QueueTokenInvalidException(concertId, userId);
        }
    }

    /**
     * 연장 가능 여부 검증
     *
     * @throws QueueExtensionLimitExceededException 연장 한도 초과
     * @throws QueueTokenInvalidException           활성 상태가 아님
     */
    public void ensureCanExtend() {
        if (!canExtend()) {
            throw new QueueExtensionLimitExceededException(concertId, userId);
        }

        if (!isActive()) {
            throw new QueueTokenInvalidException(concertId, userId);
        }
    }

    /**
     * 연장된 토큰 생성 (불변 객체 유지)
     *
     * @param newExpiredAt   새로운 만료 시간
     * @param newExtendCount 새로운 연장 횟수
     * @return 연장 정보가 적용된 새 토큰
     */
    public QueueToken withExtension(Instant newExpiredAt, Integer newExtendCount) {
        return new QueueToken(
                this.concertId,
                this.userId,
                this.token,
                this.status,
                this.position,
                newExpiredAt,
                newExtendCount);
    }
}
