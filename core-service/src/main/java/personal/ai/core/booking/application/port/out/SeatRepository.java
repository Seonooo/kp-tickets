package personal.ai.core.booking.application.port.out;

import personal.ai.core.booking.domain.model.Seat;

import java.util.List;
import java.util.Optional;

/**
 * Seat Repository (Output Port)
 * 좌석 저장소 인터페이스
 */
public interface SeatRepository {

    /**
     * 좌석 ID로 조회
     *
     * @param seatId 좌석 ID
     * @return 좌석 정보
     */
    Optional<Seat> findById(Long seatId);

    /**
     * 특정 일정의 예매 가능한 좌석 목록 조회
     *
     * @param scheduleId 일정 ID
     * @return 예매 가능한 좌석 목록 (AVAILABLE 상태)
     */
    List<Seat> findAvailableByScheduleId(Long scheduleId);

    /**
     * 좌석 저장 (상태 변경)
     *
     * @param seat 좌석 정보
     * @return 저장된 좌석 정보
     */
    Seat save(Seat seat);
}
