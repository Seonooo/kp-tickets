package personal.ai.core.booking.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import personal.ai.core.booking.domain.exception.InvalidReservationStateException;
import personal.ai.core.booking.domain.exception.ReservationAccessDeniedException;
import personal.ai.core.booking.domain.exception.ReservationAlreadyConfirmedException;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Reservation 도메인 모델 단위 테스트")
class ReservationTest {

    private static final Long ID = 1L;
    private static final Long USER_ID = 100L;
    private static final Long SEAT_ID = 10L;
    private static final Long SCHEDULE_ID = 5L;

    private Reservation pendingReservation(LocalDateTime expiresAt) {
        return new Reservation(ID, USER_ID, SEAT_ID, SCHEDULE_ID,
                ReservationStatus.PENDING, expiresAt, LocalDateTime.now());
    }

    private Reservation reservationWithStatus(ReservationStatus status) {
        return new Reservation(ID, USER_ID, SEAT_ID, SCHEDULE_ID,
                status, LocalDateTime.now().plusMinutes(5), LocalDateTime.now());
    }

    @Nested
    @DisplayName("confirm()")
    class Confirm {

        @Test
        @DisplayName("PENDING 상태 예약 확정 시 CONFIRMED 상태로 전환된다")
        void confirm_pending_success() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);

            // when
            Reservation confirmed = reservation.confirm();

            // then
            assertThat(confirmed.status()).isEqualTo(ReservationStatus.CONFIRMED);
            assertThat(confirmed.id()).isEqualTo(ID);
            assertThat(confirmed.userId()).isEqualTo(USER_ID);
        }

        @Test
        @DisplayName("CONFIRMED 상태 예약 확정 시 InvalidReservationStateException 발생")
        void confirm_confirmed_throwsInvalidState() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.CONFIRMED);

            // when & then
            assertThatThrownBy(reservation::confirm)
                    .isInstanceOf(InvalidReservationStateException.class);
        }

        @Test
        @DisplayName("CANCELLED 상태 예약 확정 시 InvalidReservationStateException 발생")
        void confirm_cancelled_throwsInvalidState() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.CANCELLED);

            // when & then
            assertThatThrownBy(reservation::confirm)
                    .isInstanceOf(InvalidReservationStateException.class);
        }

        @Test
        @DisplayName("EXPIRED 상태 예약 확정 시 InvalidReservationStateException 발생")
        void confirm_expired_throwsInvalidState() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.EXPIRED);

            // when & then
            assertThatThrownBy(reservation::confirm)
                    .isInstanceOf(InvalidReservationStateException.class);
        }
    }

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("PENDING 상태 예약 취소 시 CANCELLED 상태로 전환된다")
        void cancel_pending_success() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);

            // when
            Reservation cancelled = reservation.cancel();

            // then
            assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
        }

        @Test
        @DisplayName("PENDING 이외 상태 예약 취소 시 InvalidReservationStateException 발생")
        void cancel_nonPending_throwsInvalidState() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.CONFIRMED);

            // when & then
            assertThatThrownBy(reservation::cancel)
                    .isInstanceOf(InvalidReservationStateException.class);
        }
    }

    @Nested
    @DisplayName("expire()")
    class Expire {

        @Test
        @DisplayName("PENDING 상태 예약 만료 시 EXPIRED 상태로 전환된다")
        void expire_pending_success() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);

            // when
            Reservation expired = reservation.expire();

            // then
            assertThat(expired.status()).isEqualTo(ReservationStatus.EXPIRED);
        }

        @Test
        @DisplayName("PENDING 이외 상태 예약 만료 시 InvalidReservationStateException 발생")
        void expire_nonPending_throwsInvalidState() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.CANCELLED);

            // when & then
            assertThatThrownBy(reservation::expire)
                    .isInstanceOf(InvalidReservationStateException.class);
        }
    }

    @Nested
    @DisplayName("isExpired()")
    class IsExpired {

        @Test
        @DisplayName("만료 시각이 미래이면 false 반환")
        void isExpired_futureExpiry_false() {
            // given
            Reservation reservation = pendingReservation(LocalDateTime.now().plusMinutes(5));

            // when & then
            assertThat(reservation.isExpired()).isFalse();
        }

        @Test
        @DisplayName("만료 시각이 과거이면 true 반환")
        void isExpired_pastExpiry_true() {
            // given
            Reservation reservation = pendingReservation(LocalDateTime.now().minusMinutes(1));

            // when & then
            assertThat(reservation.isExpired()).isTrue();
        }
    }

    @Nested
    @DisplayName("ensureOwnership()")
    class EnsureOwnership {

        @Test
        @DisplayName("소유자 ID 일치 시 예외가 발생하지 않는다")
        void ensureOwnership_sameUser_success() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);

            // when & then - no exception
            reservation.ensureOwnership(USER_ID);
        }

        @Test
        @DisplayName("소유자 ID 불일치 시 ReservationAccessDeniedException 발생")
        void ensureOwnership_differentUser_throwsAccessDenied() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);

            // when & then
            assertThatThrownBy(() -> reservation.ensureOwnership(999L))
                    .isInstanceOf(ReservationAccessDeniedException.class);
        }
    }

    @Nested
    @DisplayName("ensurePending()")
    class EnsurePending {

        @Test
        @DisplayName("PENDING 상태이면 예외가 발생하지 않는다")
        void ensurePending_pending_success() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.PENDING);

            // when & then - no exception
            reservation.ensurePending();
        }

        @Test
        @DisplayName("CONFIRMED 상태이면 ReservationAlreadyConfirmedException 발생")
        void ensurePending_confirmed_throwsAlreadyConfirmed() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.CONFIRMED);

            // when & then
            assertThatThrownBy(reservation::ensurePending)
                    .isInstanceOf(ReservationAlreadyConfirmedException.class);
        }

        @Test
        @DisplayName("CANCELLED 상태이면 InvalidReservationStateException 발생")
        void ensurePending_cancelled_throwsInvalidState() {
            // given
            Reservation reservation = reservationWithStatus(ReservationStatus.CANCELLED);

            // when & then
            assertThatThrownBy(reservation::ensurePending)
                    .isInstanceOf(InvalidReservationStateException.class);
        }
    }
}
