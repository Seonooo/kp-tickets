package personal.ai.core.booking.application.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import personal.ai.core.booking.application.port.in.ConfirmReservationUseCase.ConfirmReservationCommand;
import personal.ai.core.booking.application.port.out.ReservationRepository;
import personal.ai.core.booking.application.port.out.SeatRepository;
import personal.ai.core.booking.domain.exception.InvalidReservationStateException;
import personal.ai.core.booking.domain.exception.ReservationExpiredException;
import personal.ai.core.booking.domain.exception.ReservationNotFoundException;
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
@DisplayName("ReservationConfirmService 단위 테스트")
class ReservationConfirmServiceTest {

    private static final Long RESERVATION_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Long SEAT_ID = 10L;
    private static final Long SCHEDULE_ID = 5L;
    private static final Long PAYMENT_ID = 999L;

    @Mock
    private ReservationRepository reservationRepository;

    @Mock
    private SeatRepository seatRepository;

    @InjectMocks
    private ReservationConfirmService reservationConfirmService;

    private ConfirmReservationCommand command;

    @BeforeEach
    void setUp() {
        command = new ConfirmReservationCommand(RESERVATION_ID, USER_ID, PAYMENT_ID);
    }

    private Reservation pendingReservation(LocalDateTime expiresAt) {
        return new Reservation(RESERVATION_ID, USER_ID, SEAT_ID, SCHEDULE_ID,
                ReservationStatus.PENDING, expiresAt, LocalDateTime.now());
    }

    private Seat reservedSeat() {
        return new Seat(SEAT_ID, SCHEDULE_ID, "A1", SeatGrade.VIP, BigDecimal.valueOf(100000), SeatStatus.RESERVED);
    }

    @Test
    @DisplayName("예약 확정 성공 - PENDING 상태 예약이 CONFIRMED로 전환된다")
    void confirmReservation_success() {
        // given
        Reservation pending = pendingReservation(LocalDateTime.now().plusMinutes(5));
        Reservation confirmed = new Reservation(RESERVATION_ID, USER_ID, SEAT_ID, SCHEDULE_ID,
                ReservationStatus.CONFIRMED, pending.expiresAt(), pending.createdAt());

        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(pending));
        given(reservationRepository.save(any())).willReturn(confirmed);
        given(seatRepository.findById(SEAT_ID)).willReturn(Optional.of(reservedSeat()));
        given(seatRepository.save(any())).willReturn(reservedSeat());

        // when
        Reservation result = reservationConfirmService.confirmReservation(command);

        // then
        assertThat(result.status()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository).save(any());
        verify(seatRepository).save(any());
    }

    @Test
    @DisplayName("예약 없음 - ReservationNotFoundException 발생")
    void confirmReservation_notFound_throwsReservationNotFound() {
        // given
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> reservationConfirmService.confirmReservation(command))
                .isInstanceOf(ReservationNotFoundException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("만료된 예약 확정 시 ReservationExpiredException 발생")
    void confirmReservation_expired_throwsReservationExpired() {
        // given
        Reservation expiredReservation = pendingReservation(LocalDateTime.now().minusMinutes(1));
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(expiredReservation));

        // when & then
        assertThatThrownBy(() -> reservationConfirmService.confirmReservation(command))
                .isInstanceOf(ReservationExpiredException.class);

        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 CONFIRMED 상태 예약은 멱등성 보장을 위해 조용히 반환한다 (중복 이벤트 처리)")
    void confirmReservation_alreadyConfirmed_idempotentSkip() {
        // given
        Reservation alreadyConfirmed = new Reservation(RESERVATION_ID, USER_ID, SEAT_ID, SCHEDULE_ID,
                ReservationStatus.CONFIRMED, LocalDateTime.now().plusMinutes(5), LocalDateTime.now());
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(alreadyConfirmed));

        // when
        Reservation result = reservationConfirmService.confirmReservation(command);

        // then - 예외 없이 기존 예약 그대로 반환, DB 쓰기 없음
        assertThat(result.status()).isEqualTo(ReservationStatus.CONFIRMED);
        verify(reservationRepository, never()).save(any());
        verify(seatRepository, never()).save(any());
    }

    @Test
    @DisplayName("CANCELLED 상태 예약 확정 시 InvalidReservationStateException 발생")
    void confirmReservation_cancelled_throwsInvalidState() {
        // given
        Reservation cancelled = new Reservation(RESERVATION_ID, USER_ID, SEAT_ID, SCHEDULE_ID,
                ReservationStatus.CANCELLED, LocalDateTime.now().plusMinutes(5), LocalDateTime.now());
        given(reservationRepository.findById(RESERVATION_ID)).willReturn(Optional.of(cancelled));

        // when & then
        assertThatThrownBy(() -> reservationConfirmService.confirmReservation(command))
                .isInstanceOf(InvalidReservationStateException.class);

        verify(reservationRepository, never()).save(any());
    }
}
