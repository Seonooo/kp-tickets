package personal.ai.core.booking.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.application.port.out.SeatRepository;
import personal.ai.core.booking.domain.model.Seat;
import personal.ai.core.booking.domain.model.SeatStatus;

import java.util.List;
import java.util.Optional;

/**
 * Seat Persistence Adapter
 * JPA를 사용한 좌석 저장소 구현체
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SeatPersistenceAdapter implements SeatRepository {

    private final JpaSeatRepository jpaSeatRepository;

    @Override
    public Optional<Seat> findById(Long seatId) {
        log.debug("Finding seat by id: {}", seatId);
        return jpaSeatRepository.findById(seatId)
                .map(SeatEntity::toDomain);
    }

    @Override
    public List<Seat> findAvailableByScheduleId(Long scheduleId) {
        log.debug("Finding available seats for schedule: {}", scheduleId);
        return jpaSeatRepository.findByScheduleIdAndStatus(scheduleId, SeatStatus.AVAILABLE)
                .stream()
                .map(SeatEntity::toDomain)
                .toList();
    }

    @Override
    public Seat save(Seat seat) {
        log.debug("Saving seat: seatId={}, status={}", seat.id(), seat.status());
        SeatEntity entity = SeatEntity.fromDomain(seat);
        SeatEntity saved = jpaSeatRepository.save(entity);
        return saved.toDomain();
    }
}
