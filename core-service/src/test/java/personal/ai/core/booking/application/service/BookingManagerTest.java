package personal.ai.core.booking.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personal.ai.core.booking.application.port.out.ReservationRepository;
import personal.ai.core.booking.application.port.out.SeatRepository;
import personal.ai.core.booking.domain.exception.SeatNotFoundException;
import personal.ai.core.booking.domain.model.Reservation;
import personal.ai.core.booking.domain.model.ReservationStatus;
import personal.ai.core.booking.domain.model.Seat;
import personal.ai.core.booking.domain.model.SeatGrade;
import personal.ai.core.booking.domain.model.SeatStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingManager 단위 테스트")
class BookingManagerTest {

    private static final Long USER_ID = 100L;
    private static final Long SEAT_ID = 10L;
    private static final Long SCHEDULE_ID = 5L;
    private static final Long RESERVATION_ID = 1L;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private ReservationRepository reservationRepository;

    @InjectMocks
    private BookingManager bookingManager;

    private Seat availableSeat() {
        return new Seat(SEAT_ID, SCHEDULE_ID, "A1", SeatGrade.VIP, BigDecimal.valueOf(100000), SeatStatus.AVAILABLE);
    }

    private Seat reservedSeat() {
        return new Seat(SEAT_ID, SCHEDULE_ID, "A1", SeatGrade.VIP, BigDecimal.valueOf(100000), SeatStatus.RESERVED);
    }

    private Reservation pendingReservation() {
        return new Reservation(RESERVATION_ID, USER_ID, SEAT_ID, SCHEDULE_ID,
                ReservationStatus.PENDING, LocalDateTime.now().plusMinutes(5), LocalDateTime.now());
    }

    @Test
    @DisplayName("좌석 예약 성공 - PENDING 상태 예약이 생성된다")
    void reserveSeatInTransaction_success() {
        // given
        Reservation saved = pendingReservation();
        given(seatRepository.findById(SEAT_ID)).willReturn(Optional.of(availableSeat()));
        given(seatRepository.save(any())).willReturn(reservedSeat());
        given(reservationRepository.save(any())).willReturn(saved);

        // when
        Reservation result = bookingManager.reserveSeatInTransaction(USER_ID, SEAT_ID, SCHEDULE_ID);

        // then
        assertThat(result.status()).isEqualTo(ReservationStatus.PENDING);
        assertThat(result.userId()).isEqualTo(USER_ID);
        assertThat(result.seatId()).isEqualTo(SEAT_ID);
        verify(seatRepository).save(any());
        verify(reservationRepository).save(any());
    }

    @Test
    @DisplayName("좌석 미존재 시 SeatNotFoundException 발생")
    void reserveSeatInTransaction_seatNotFound_throwsSeatNotFoundException() {
        // given
        given(seatRepository.findById(SEAT_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> bookingManager.reserveSeatInTransaction(USER_ID, SEAT_ID, SCHEDULE_ID))
                .isInstanceOf(SeatNotFoundException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("PENDING 예약 만료 처리 - EXPIRED 상태로 전환되고 좌석이 해제된다")
    void expireReservation_pending_expiresAndReleasesSeat() {
        // given
        Reservation pending = pendingReservation();
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(pending));
        given(reservationRepository.save(any())).willReturn(pending);
        given(seatRepository.findById(SEAT_ID)).willReturn(Optional.of(reservedSeat()));
        given(seatRepository.save(any())).willReturn(availableSeat());

        // when
        bookingManager.expireReservation(RESERVATION_ID);

        // then
        verify(reservationRepository).save(any());
        verify(seatRepository).save(any());
    }

    @Test
    @DisplayName("예약 미존재 시 경고만 남기고 종료된다")
    void expireReservation_notFound_skips() {
        // given
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.empty());

        // when
        bookingManager.expireReservation(RESERVATION_ID);

        // then
        verify(reservationRepository, never()).save(any());
        verify(seatRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 CONFIRMED 예약은 만료 처리하지 않는다")
    void expireReservation_alreadyConfirmed_skips() {
        // given
        Reservation confirmed = new Reservation(RESERVATION_ID, USER_ID, SEAT_ID, SCHEDULE_ID,
                ReservationStatus.CONFIRMED, LocalDateTime.now().plusMinutes(5), LocalDateTime.now());
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(confirmed));

        // when
        bookingManager.expireReservation(RESERVATION_ID);

        // then
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 EXPIRED 예약은 재처리하지 않는다")
    void expireReservation_alreadyExpired_skips() {
        // given
        Reservation alreadyExpired = new Reservation(RESERVATION_ID, USER_ID, SEAT_ID, SCHEDULE_ID,
                ReservationStatus.EXPIRED, LocalDateTime.now().minusMinutes(5), LocalDateTime.now());
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(alreadyExpired));

        // when
        bookingManager.expireReservation(RESERVATION_ID);

        // then
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("CANCELLED 예약은 만료 처리하지 않고 조용히 skip한다 (기존 코드 버그 수정)")
    void expireReservation_cancelled_skips() {
        // given
        Reservation cancelled = new Reservation(RESERVATION_ID, USER_ID, SEAT_ID, SCHEDULE_ID,
                ReservationStatus.CANCELLED, LocalDateTime.now().plusMinutes(5), LocalDateTime.now());
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(cancelled));

        // when - 기존 코드에서는 InvalidReservationStateException 발생했던 케이스
        bookingManager.expireReservation(RESERVATION_ID);

        // then - 예외 없이 skip, 상태 변경 없음
        verify(reservationRepository, never()).save(any());
        verify(seatRepository, never()).save(any());
    }
}
