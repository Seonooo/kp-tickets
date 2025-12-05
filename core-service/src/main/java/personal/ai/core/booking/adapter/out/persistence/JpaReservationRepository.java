package personal.ai.core.booking.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA Repository for Reservation
 */
public interface JpaReservationRepository extends JpaRepository<ReservationEntity, Long> {
}
