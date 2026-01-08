package personal.ai.core.booking.application.port.in;

import personal.ai.core.booking.adapter.in.web.dto.SeatResponse;

import java.util.List;

/**
 * Get Available Seats UseCase (Input Port)
 * 예매 가능한 좌석 조회 유스케이스
 *
 * 성능 최적화: Response DTO를 직접 반환하여 직렬화 오버헤드 최소화
 */
public interface GetAvailableSeatsUseCase {

    /**
     * 특정 일정의 예매 가능한 좌석 목록 조회
     *
     * @param scheduleId 콘서트 일정 ID
     * @param userId 사용자 ID
     * @param queueToken 대기열 토큰
     * @return 예매 가능한 좌석 응답 DTO 목록 (캐시 최적화)
     */
    List<SeatResponse> getAvailableSeats(Long scheduleId, Long userId, String queueToken);
}
