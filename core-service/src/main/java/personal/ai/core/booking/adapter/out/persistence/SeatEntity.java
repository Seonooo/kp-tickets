package personal.ai.core.booking.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import personal.ai.core.booking.domain.model.Seat;
import personal.ai.core.booking.domain.model.SeatGrade;
import personal.ai.core.booking.domain.model.SeatStatus;

import java.math.BigDecimal;

/**
 * Seat JPA Entity
 * 좌석 테이블 매핑
 */
@Entity
@Table(name = "seats",
        indexes = {
                @Index(name = "idx_schedule_status", columnList = "schedule_id, status"),
                @Index(name = "idx_schedule_id", columnList = "schedule_id")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "seat_number", nullable = false, length = 10)
    private String seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SeatGrade grade;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    /**
     * 도메인 모델로부터 엔티티 생성 (업데이트용)
     */
    public static SeatEntity fromDomain(Seat seat) {
        SeatEntity entity = new SeatEntity();
        entity.id = seat.id();
        entity.scheduleId = seat.scheduleId();
        entity.seatNumber = seat.seatNumber();
        entity.grade = seat.grade();
        entity.price = seat.price();
        entity.status = seat.status();
        return entity;
    }

    /**
     * 도메인 모델로 변환
     */
    public Seat toDomain() {
        return new Seat(id, scheduleId, seatNumber, grade, price, status);
    }

    /**
     * 상태 업데이트 (영속성 컨텍스트 내에서 사용)
     */
    public void updateStatus(SeatStatus newStatus) {
        this.status = newStatus;
    }
}
