package personal.ai.core.booking.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import personal.ai.core.booking.domain.model.SeatStatus;

import java.util.List;

/**
 * Spring Data JPA Repository for Seat
 */
public interface JpaSeatRepository extends JpaRepository<SeatEntity, Long> {

    /**
     * 특정 일정의 예매 가능한 좌석 목록 조회
     */
    @Query("SELECT s FROM SeatEntity s WHERE s.scheduleId = :scheduleId AND s.status = :status")
    List<SeatEntity> findByScheduleIdAndStatus(@Param("scheduleId") Long scheduleId,
                                                @Param("status") SeatStatus status);
}
