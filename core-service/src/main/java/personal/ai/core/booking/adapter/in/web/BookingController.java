package personal.ai.core.booking.adapter.in.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import personal.ai.core.booking.adapter.in.web.dto.ReservationResponse;
import personal.ai.core.booking.adapter.in.web.dto.ReserveSeatRequest;
import personal.ai.core.booking.adapter.in.web.dto.SeatResponse;
import personal.ai.core.booking.application.port.in.GetAvailableSeatsUseCase;
import personal.ai.core.booking.application.port.in.GetReservationUseCase;
import personal.ai.core.booking.application.port.in.ReserveSeatCommand;
import personal.ai.core.booking.application.port.in.ReserveSeatUseCase;
import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.booking.domain.model.Seat;

import java.util.List;

/**
 * Booking API Controller
 * 좌석 조회 및 예약 관리 REST API
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingController {

    private final ReserveSeatUseCase reserveSeatUseCase;
    private final GetAvailableSeatsUseCase getAvailableSeatsUseCase;
    private final GetReservationUseCase getReservationUseCase;

    /**
     * 예약 가능한 좌석 목록 조회
     * GET /api/v1/schedules/{scheduleId}/seats
     */
    @GetMapping("/schedules/{scheduleId}/seats")
    public ResponseEntity<List<SeatResponse>> getAvailableSeats(
            @PathVariable Long scheduleId,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Queue-Token") String queueToken
    ) {
        log.info("Get available seats: scheduleId={}, userId={}", scheduleId, userId);

        List<Seat> seats = getAvailableSeatsUseCase.getAvailableSeats(scheduleId, userId, queueToken);

        List<SeatResponse> response = seats.stream()
                .map(SeatResponse::from)
                .toList();

        return ResponseEntity.ok(response);
    }

    /**
     * 좌석 예약 생성
     * POST /api/v1/reservations
     */
    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> reserveSeat(
            @Valid @RequestBody ReserveSeatRequest request,
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-Queue-Token") String queueToken
    ) {
        log.info("Reserve seat: userId={}, seatId={}, scheduleId={}", userId, request.seatId(), request.scheduleId());

        ReserveSeatCommand command = request.toCommand(userId, queueToken);
        Reservation reservation = reserveSeatUseCase.reserveSeat(command);

        ReservationResponse response = ReservationResponse.from(reservation);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 예약 조회
     * GET /api/v1/reservations/{reservationId}
     */
    @GetMapping("/reservations/{reservationId}")
    public ResponseEntity<ReservationResponse> getReservation(
            @PathVariable Long reservationId,
            @RequestHeader("X-User-Id") Long userId
    ) {
        log.info("Get reservation: reservationId={}, userId={}", reservationId, userId);

        Reservation reservation = getReservationUseCase.getReservation(reservationId, userId);

        ReservationResponse response = ReservationResponse.from(reservation);

        return ResponseEntity.ok(response);
    }
}
