package personal.ai.core.booking.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA Repository for Concert Schedule
 */
public interface JpaConcertScheduleRepository extends JpaRepository<ConcertScheduleEntity, Long> {
}
