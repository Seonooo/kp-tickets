package personal.ai.core.booking.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.application.port.out.ReservationEventPort;
import personal.ai.core.booking.application.port.out.ReservationRepository;
import personal.ai.core.booking.domain.model.Reservation;

import java.util.Optional;

/**
 * Reservation Persistence Adapter
 * JPA를 사용한 예약 저장소 구현체
 * Transactional Outbox Pattern: ReservationEventPort에 이벤트 발행 위임
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationPersistenceAdapter implements ReservationRepository {

    private final JpaReservationRepository jpaReservationRepository;
    private final ReservationEventPort reservationEventPort;

    @Override
    public Reservation save(Reservation reservation) {
        log.debug("Saving reservation: seatId={}, status={}", reservation.seatId(), reservation.status());

        var entity = ReservationEntity.fromDomain(reservation);
        var saved = jpaReservationRepository.save(entity);
        var savedReservation = saved.toDomain();

        // Outbox 이벤트 발행 (Port 위임)
        reservationEventPort.publishReservationEvent(savedReservation);

        return savedReservation;
    }

    @Override
    public Optional<Reservation> findById(Long reservationId) {
        log.debug("Finding reservation: reservationId={}", reservationId);
        return jpaReservationRepository.findById(reservationId)
                .map(ReservationEntity::toDomain);
    }
}
