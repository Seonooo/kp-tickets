package personal.ai.core.booking.domain.model;

/**
 * Outbox 도메인 정책 상수
 * 재시도 횟수 등 비즈니스 정책으로 결정된 값을 정의한다.
 */
public final class OutboxPolicy {

    public static final int MAX_RETRY_COUNT = 3;

    private OutboxPolicy() {}
}
