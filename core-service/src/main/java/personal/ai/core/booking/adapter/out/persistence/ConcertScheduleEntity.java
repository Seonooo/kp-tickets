package personal.ai.core.booking.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import personal.ai.core.booking.domain.model.ConcertSchedule;

import java.time.LocalDateTime;

/**
 * Concert Schedule JPA Entity
 * 콘서트 일정 테이블 매핑
 */
@Entity
@Table(name = "concert_schedules")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ConcertScheduleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "concert_id", nullable = false)
    private Long concertId;

    @Column(name = "performance_date", nullable = false)
    private LocalDateTime performanceDate;

    @Column(nullable = false, length = 200)
    private String venue;

    /**
     * 도메인 모델로 변환
     */
    public ConcertSchedule toDomain() {
        return new ConcertSchedule(id, concertId, performanceDate, venue);
    }
}
