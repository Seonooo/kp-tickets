package personal.ai.core.booking.adapter.out.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.adapter.out.persistence.JpaOutboxEventRepository;
import personal.ai.core.booking.adapter.out.persistence.OutboxEventEntity;
import personal.ai.core.booking.adapter.out.persistence.OutboxEventFactory;
import personal.ai.core.booking.application.port.out.ReservationEventPort;
import personal.ai.core.booking.domain.model.Reservation;

/**
 * Reservation Event Adapter
 * Outbox 패턴을 사용한 예약 이벤트 발행 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventAdapter implements ReservationEventPort {

    private final JpaOutboxEventRepository jpaOutboxEventRepository;
    private final OutboxEventFactory outboxEventFactory;

    @Override
    public void publishReservationEvent(Reservation reservation) {
        try {
            OutboxEventEntity outboxEvent = switch (reservation.status()) {
                case PENDING -> outboxEventFactory.createReservationCreatedEvent(reservation);
                case CONFIRMED -> outboxEventFactory.createReservationConfirmedEvent(reservation);
                case CANCELLED -> outboxEventFactory.createReservationCancelledEvent(reservation);
                case EXPIRED -> outboxEventFactory.createReservationExpiredEvent(reservation);
            };

            jpaOutboxEventRepository.save(outboxEvent);
            log.debug("Reservation event published: reservationId={}, status={}",
                    reservation.id(), reservation.status());

        } catch (Exception e) {
            log.error("Failed to publish reservation event: reservationId={}", reservation.id(), e);
            throw new RuntimeException("Failed to publish reservation event", e);
        }
    }
}
