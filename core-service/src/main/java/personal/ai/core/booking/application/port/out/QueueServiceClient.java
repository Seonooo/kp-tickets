package personal.ai.core.booking.application.port.out;

/**
 * Queue Service Client (Output Port)
 * Queue Service와의 HTTP 통신 인터페이스
 */
public interface QueueServiceClient {

    /**
     * Queue 토큰 검증
     * Queue Service의 토큰 검증 API 호출
     *
     * @param userId 사용자 ID
     * @param queueToken 대기열 토큰
     * @throws personal.ai.common.exception.BusinessException 토큰이 유효하지 않거나 만료된 경우 (401)
     */
    void validateToken(Long userId, String queueToken);
}
