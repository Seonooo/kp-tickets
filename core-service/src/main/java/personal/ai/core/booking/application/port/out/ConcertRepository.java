package personal.ai.core.booking.application.port.out;

import personal.ai.core.booking.domain.model.Concert;
import personal.ai.core.booking.domain.model.ConcertSchedule;

import java.util.Optional;

/**
 * Concert Repository (Output Port)
 * 콘서트 저장소 인터페이스
 */
public interface ConcertRepository {

    /**
     * 콘서트 ID로 조회
     *
     * @param concertId 콘서트 ID
     * @return 콘서트 정보
     */
    Optional<Concert> findById(Long concertId);

    /**
     * 콘서트 일정 ID로 조회
     *
     * @param scheduleId 일정 ID
     * @return 콘서트 일정 정보
     */
    Optional<ConcertSchedule> findScheduleById(Long scheduleId);
}
