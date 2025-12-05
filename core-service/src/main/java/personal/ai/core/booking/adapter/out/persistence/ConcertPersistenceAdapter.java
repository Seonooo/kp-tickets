package personal.ai.core.booking.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.application.port.out.ConcertRepository;
import personal.ai.core.booking.domain.model.Concert;
import personal.ai.core.booking.domain.model.ConcertSchedule;

import java.util.Optional;

/**
 * Concert Persistence Adapter
 * JPA를 사용한 콘서트 저장소 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConcertPersistenceAdapter implements ConcertRepository {

    private final JpaConcertRepository jpaConcertRepository;
    private final JpaConcertScheduleRepository jpaConcertScheduleRepository;

    @Override
    public Optional<Concert> findById(Long concertId) {
        log.debug("Finding concert by id: {}", concertId);
        return jpaConcertRepository.findById(concertId)
                .map(ConcertEntity::toDomain);
    }

    @Override
    public Optional<ConcertSchedule> findScheduleById(Long scheduleId) {
        log.debug("Finding concert schedule by id: {}", scheduleId);
        return jpaConcertScheduleRepository.findById(scheduleId)
                .map(ConcertScheduleEntity::toDomain);
    }
}
