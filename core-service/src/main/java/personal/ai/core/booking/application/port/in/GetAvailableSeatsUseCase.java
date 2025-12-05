package personal.ai.core.booking.application.port.in;

import personal.ai.core.booking.domain.model.Seat;

import java.util.List;

/**
 * Get Available Seats UseCase (Input Port)
 * 예매 가능한 좌석 조회 유스케이스
 */
public interface GetAvailableSeatsUseCase {

    /**
     * 특정 일정의 예매 가능한 좌석 목록 조회
     *
     * @param scheduleId 콘서트 일정 ID
     * @param userId 사용자 ID
     * @param queueToken 대기열 토큰
     * @return 예매 가능한 좌석 목록 (AVAILABLE 상태)
     * @throws personal.ai.core.booking.domain.exception.ScheduleNotFoundException 일정을 찾을 수 없을 때
     */
    List<Seat> getAvailableSeats(Long scheduleId, Long userId, String queueToken);
}
