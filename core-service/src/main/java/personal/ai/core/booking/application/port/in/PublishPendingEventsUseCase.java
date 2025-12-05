package personal.ai.core.booking.application.port.in;

/**
 * Publish Pending Events UseCase (Input Port)
 * Outbox 테이블에 쌓인 대기 중(PENDING)인 이벤트를 발행하는 유스케이스
 */
public interface PublishPendingEventsUseCase {

    /**
     * 대기 중인 이벤트를 조회하여 발행
     * 스케줄러에 의해 주기적으로 호출됨
     * 
     * @return 발행된 이벤트 수
     */
    int publishPendingEvents();
}
