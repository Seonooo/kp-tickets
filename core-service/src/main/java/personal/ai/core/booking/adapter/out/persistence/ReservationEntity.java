package personal.ai.core.booking.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.booking.domain.model.ReservationStatus;

import java.time.LocalDateTime;

/**
 * Reservation JPA Entity
 * 예약 테이블 매핑
 */
@Entity
@Table(name = "reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_schedule_seat",
                columnNames = {"schedule_id", "seat_id"}
        ))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "seat_id", nullable = false)
    private Long seatId;

    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 도메인 모델로부터 엔티티 생성
     */
    public static ReservationEntity fromDomain(Reservation reservation) {
        ReservationEntity entity = new ReservationEntity();
        entity.id = reservation.id();
        entity.userId = reservation.userId();
        entity.seatId = reservation.seatId();
        entity.scheduleId = reservation.scheduleId();
        entity.status = reservation.status();
        entity.expiresAt = reservation.expiresAt();
        entity.createdAt = reservation.createdAt();
        return entity;
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /**
     * 도메인 모델로 변환
     */
    public Reservation toDomain() {
        return new Reservation(id, userId, seatId, scheduleId, status, expiresAt, createdAt);
    }

    /**
     * 상태 업데이트 (영속성 컨텍스트 내에서 사용)
     */
    public void updateStatus(ReservationStatus newStatus) {
        this.status = newStatus;
    }
}
