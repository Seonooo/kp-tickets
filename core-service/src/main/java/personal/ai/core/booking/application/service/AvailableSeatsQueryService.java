package personal.ai.core.booking.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import personal.ai.core.booking.application.port.in.GetAvailableSeatsUseCase;
import personal.ai.core.booking.application.port.out.QueueServiceClient;
import personal.ai.core.booking.application.port.out.SeatRepository;
import personal.ai.core.booking.domain.model.Seat;
import personal.ai.core.booking.domain.service.QueueTokenExtractor;

import java.util.List;

/**
 * Available Seats Query Service (SRP)
 * 단일 책임: 예약 가능 좌석 조회
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AvailableSeatsQueryService implements GetAvailableSeatsUseCase {

    private final SeatRepository seatRepository;
    private final QueueServiceClient queueServiceClient;

    @Override
    public List<Seat> getAvailableSeats(Long scheduleId, Long userId, String queueToken) {
        log.debug("Getting available seats: scheduleId={}, userId={}", scheduleId, userId);

        // 토큰에서 concertId 추출 (형식: concertId:userId:counter)
        String concertId = QueueTokenExtractor.extractConcertId(queueToken);

        // Queue Service에 토큰 검증 요청
        queueServiceClient.validateToken(concertId, userId, queueToken);

        var availableSeats = seatRepository.findAvailableByScheduleId(scheduleId);

        log.debug("Found available seats: scheduleId={}, count={}", scheduleId, availableSeats.size());

        return availableSeats;
    }
}
