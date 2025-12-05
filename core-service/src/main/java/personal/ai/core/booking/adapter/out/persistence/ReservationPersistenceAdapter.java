package personal.ai.core.booking.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.application.port.out.ReservationRepository;
import personal.ai.core.booking.domain.model.Reservation;

import java.util.Optional;

/**
 * Reservation Persistence Adapter
 * JPA를 사용한 예약 저장소 구현체
 * Transactional Outbox Pattern: 예약 저장 시 자동으로 Outbox 이벤트 저장
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationPersistenceAdapter implements ReservationRepository {

    private final JpaReservationRepository jpaReservationRepository;
    private final JpaOutboxEventRepository jpaOutboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;

    @Override
    public Reservation save(Reservation reservation) {
        log.debug("Saving reservation: userId={}, seatId={}, status={}",
                reservation.userId(), reservation.seatId(), reservation.status());

        // 1. 예약 저장
        ReservationEntity entity = ReservationEntity.fromDomain(reservation);
        ReservationEntity saved = jpaReservationRepository.save(entity);

        // 2. Outbox 이벤트 저장 (같은 트랜잭션 내 - 원자성 보장)
        // Domain/Application Layer는 이 과정을 모름
        saveOutboxEvent(saved.toDomain());

        return saved.toDomain();
    }

    /**
     * Outbox 이벤트 저장 (Adapter 내부 구현)
     */
    private void saveOutboxEvent(Reservation reservation) {
        try {
            OutboxEventEntity outboxEvent = switch (reservation.status()) {
                case PENDING -> outboxEventFactory.createReservationCreatedEvent(reservation);
                case CONFIRMED -> outboxEventFactory.createReservationConfirmedEvent(reservation);
                case CANCELLED -> outboxEventFactory.createReservationCancelledEvent(reservation);
                case EXPIRED -> outboxEventFactory.createReservationExpiredEvent(reservation);
            };

            jpaOutboxEventRepository.save(outboxEvent);
            log.debug("Outbox event saved: reservationId={}", reservation.id());
        } catch (Exception e) {
            log.error("Failed to save outbox event: reservationId={}", reservation.id(), e);
            throw new RuntimeException("Failed to save outbox event", e);
        }
    }

    @Override
    public Optional<Reservation> findById(Long reservationId) {
        log.debug("Finding reservation by id: {}", reservationId);
        return jpaReservationRepository.findById(reservationId)
                .map(ReservationEntity::toDomain);
    }
}
