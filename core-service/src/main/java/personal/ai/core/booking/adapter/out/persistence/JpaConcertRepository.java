package personal.ai.core.booking.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA Repository for Concert
 */
public interface JpaConcertRepository extends JpaRepository<ConcertEntity, Long> {
}
