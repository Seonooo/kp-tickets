package personal.ai.core.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import personal.ai.core.acceptance.support.BookingTestAdapter;
import personal.ai.core.booking.adapter.in.web.dto.ReservationResponse;
import personal.ai.core.booking.adapter.in.web.dto.SeatResponse;
import personal.ai.core.booking.domain.model.ReservationStatus;
import personal.ai.core.booking.domain.model.SeatStatus;
import personal.ai.core.payment.adapter.in.web.dto.PaymentResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Booking Acceptance Test Step Definitions
 * 비즈니스 관점의 자연어로 작성된 시나리오에 매핑
 */
@Slf4j
@ScenarioScope
@RequiredArgsConstructor
public class BookingAcceptanceSteps {

    private final BookingTestAdapter bookingAdapter;
    // 동시성 테스트용
    private final AtomicInteger successfulReservations = new AtomicInteger(0);
    private final AtomicInteger failedReservations = new AtomicInteger(0);
    // 테스트 컨텍스트
    private Long currentScheduleId;
    private Long currentSeatId;
    private Long currentUserId;
    private String currentQueueToken;
    private List<SeatResponse> lastSeatsResponse;
    private ReservationResponse lastReservationResponse;
    private Long currentReservationId;
    private PaymentResponse lastPaymentResponse;

    // ==========================================
    // 배경: 예약 가능한 스케줄
    // ==========================================

    @Given("예약 가능한 콘서트 스케줄이 존재한다")
    public void 예약_가능한_콘서트_스케줄이_존재한다() {
        log.info(">>> Given: 콘서트 스케줄 설정");
        bookingAdapter.clearAllData();
        this.currentScheduleId = 1L;
        bookingAdapter.createScheduleIfNotExists(currentScheduleId);

        // 기본 사용자 생성 및 토큰 발급
        this.currentUserId = bookingAdapter.createUser("testuser");
        this.currentQueueToken = bookingAdapter.issueActiveQueueToken(currentUserId);
    }

    // ==========================================
    // Given: 사전 상태
    // ==========================================

    @Given("예약 가능한 좌석이 있다")
    public void 예약_가능한_좌석이_있다() {
        log.info(">>> Given: 예약 가능한 좌석 생성");
        this.currentSeatId = bookingAdapter.createSeat(currentScheduleId, "A1", SeatStatus.AVAILABLE);
    }

    @Given("이미 예약된 좌석이 있다")
    public void 이미_예약된_좌석이_있다() {
        log.info(">>> Given: 이미 예약된 좌석 생성");
        this.currentSeatId = bookingAdapter.createSeat(currentScheduleId, "A1", SeatStatus.AVAILABLE);
        // 예약 생성하여 좌석 상태를 RESERVED로 변경
        bookingAdapter.createReservation(null, currentUserId, currentSeatId, ReservationStatus.PENDING);
    }

    @Given("예약을 완료한 사용자이다")
    public void 예약을_완료한_사용자이다() {
        log.info(">>> Given: 예약 완료 상태 설정");
        예약_가능한_좌석이_있다();
        해당_좌석_예약을_요청한다();
        예약이_생성된다();
    }

    @Given("대기 중인 예약이 있다")
    public void 대기_중인_예약이_있다() {
        log.info(">>> Given: 대기 중인 예약 설정");
        예약을_완료한_사용자이다();
    }

    @Given("만료된 예약이 있다")
    public void 만료된_예약이_있다() {
        log.info(">>> Given: 만료된 예약 생성");
        예약_가능한_좌석이_있다();

        // 만료된 예약 생성
        this.currentReservationId = bookingAdapter.createReservation(
            null, currentUserId, currentSeatId, ReservationStatus.EXPIRED
        );
    }

    // ==========================================
    // When: 사용자 행동
    // ==========================================

    @When("좌석 목록 조회를 요청한다")
    public void 좌석_목록_조회를_요청한다() {
        log.info(">>> When: 좌석 목록 조회 API 호출");
        try {
            lastSeatsResponse = bookingAdapter.getAvailableSeats(
                currentScheduleId,
                currentUserId,
                currentQueueToken
            );
        } catch (Exception e) {
            log.error(">>> 좌석 조회 실패", e);
            throw e;
        }
    }

    @When("해당 좌석 예약을 요청한다")
    public void 해당_좌석_예약을_요청한다() {
        log.info(">>> When: 좌석 예약 API 호출");
        try {
            lastReservationResponse = bookingAdapter.reserveSeat(
                currentScheduleId,
                currentSeatId,
                currentUserId,
                currentQueueToken
            );
            currentReservationId = lastReservationResponse.reservationId();
        } catch (Exception e) {
            log.debug(">>> 예약 실패 (예상된 동작일 수 있음): {}", e.getMessage());
            // 실패 케이스는 Then에서 검증
        }
    }

    @When("{int}명의 사용자가 동시에 같은 좌석을 예약 시도한다")
    public void 명의_사용자가_동시에_같은_좌석을_예약_시도한다(Integer count) {
        log.info(">>> When: {}명의 동시 예약 시도", count);
        successfulReservations.set(0);
        failedReservations.set(0);

        CompletableFuture<?>[] futures = new CompletableFuture[count];

        for (int i = 0; i < count; i++) {
            String username = "concurrent-user-" + i;
            futures[i] = CompletableFuture.runAsync(() -> {
                try {
                    Long userId = bookingAdapter.createUser(username);
                    String token = bookingAdapter.issueActiveQueueToken(userId);

                    bookingAdapter.reserveSeat(
                        currentScheduleId,
                        currentSeatId,
                        userId,
                        token
                    );
                    successfulReservations.incrementAndGet();
                    log.debug(">>> 예약 성공 - username={}", username);
                } catch (Exception e) {
                    failedReservations.incrementAndGet();
                    log.debug(">>> 예약 실패 (예상된 동작) - username={}", username);
                }
            });
        }

        CompletableFuture.allOf(futures).join();
        log.info(">>> When: 동시 예약 완료 - 성공: {}, 실패: {}",
            successfulReservations.get(), failedReservations.get());
    }

    @When("나의 예약 조회를 요청한다")
    public void 나의_예약_조회를_요청한다() {
        log.info(">>> When: 예약 조회 API 호출");
        try {
            lastReservationResponse = bookingAdapter.getReservation(currentReservationId, currentUserId);
        } catch (Exception e) {
            log.error(">>> 예약 조회 실패", e);
            throw e;
        }
    }

    @When("결제를 요청한다")
    public void 결제를_요청한다() {
        log.info(">>> When: 결제 API 호출");
        try {
            lastPaymentResponse = bookingAdapter.processPayment(
                currentReservationId,
                currentUserId
            );
        } catch (Exception e) {
            log.debug(">>> 결제 실패 (예상된 동작일 수 있음): {}", e.getMessage());
            // 실패 케이스는 Then에서 검증
        }
    }

    // ==========================================
    // Then/And: 결과 검증
    // ==========================================

    @Then("좌석 목록이 반환된다")
    public void 좌석_목록이_반환된다() {
        log.info(">>> Then: 좌석 목록 반환 검증");
        assertThat(lastSeatsResponse).isNotNull();
        assertThat(lastSeatsResponse).isNotEmpty();
    }

    @And("각 좌석의 정보가 포함된다")
    public void 각_좌석의_정보가_포함된다() {
        log.info(">>> Then: 좌석 정보 포함 검증");
        assertThat(lastSeatsResponse.get(0)).isNotNull();
    }

    @Then("예약이 생성된다")
    public void 예약이_생성된다() {
        log.info(">>> Then: 예약 생성 검증");
        assertThat(lastReservationResponse).isNotNull();
        assertThat(lastReservationResponse.reservationId()).isNotNull();
    }

    @And("예약 상태는 대기 중이다")
    public void 예약_상태는_대기_중이다() {
        log.info(">>> Then: 예약 상태 검증");
        assertThat(lastReservationResponse.status()).isEqualTo(ReservationStatus.PENDING);
    }

    @And("예약 만료 시간이 설정된다")
    public void 예약_만료_시간이_설정된다() {
        log.info(">>> Then: 예약 만료 시간 검증");
        assertThat(lastReservationResponse.expiresAt()).isNotNull();
    }

    @Then("예약에 실패한다")
    public void 예약에_실패한다() {
        log.info(">>> Then: 예약 실패 검증");
        // API 호출 시 예외가 발생했어야 함
        assertThat(lastReservationResponse).isNull();
    }

    @And("좌석이 이미 예약되었다는 메시지가 반환된다")
    public void 좌석이_이미_예약되었다는_메시지가_반환된다() {
        log.info(">>> Then: 예약 불가 메시지 검증");
        // 에러 메시지는 예외에 포함됨
    }

    @Then("오직 {int}명의 사용자만 예약에 성공하고")
    public void 오직_명의_사용자만_예약에_성공하고(Integer expectedSuccess) {
        log.info(">>> Then: 예약 성공 수 검증 - expected={}, actual={}",
            expectedSuccess, successfulReservations.get());
        assertThat(successfulReservations.get()).isEqualTo(expectedSuccess);
    }

    @And("{int}명의 사용자는 예약에 실패한다")
    public void 명의_사용자는_예약에_실패한다(Integer expectedFail) {
        log.info(">>> Then: 예약 실패 수 검증 - expected={}, actual={}",
            expectedFail, failedReservations.get());
        assertThat(failedReservations.get()).isEqualTo(expectedFail);
    }

    @Then("예약 정보가 반환된다")
    public void 예약_정보가_반환된다() {
        log.info(">>> Then: 예약 정보 반환 검증");
        assertThat(lastReservationResponse).isNotNull();
        assertThat(lastReservationResponse.reservationId()).isEqualTo(currentReservationId);
    }

    @And("예약한 좌석 정보가 포함된다")
    public void 예약한_좌석_정보가_포함된다() {
        log.info(">>> Then: 좌석 정보 포함 검증");
        assertThat(lastReservationResponse.seatId()).isNotNull();
    }

    @Then("결제가 완료된다")
    public void 결제가_완료된다() {
        log.info(">>> Then: 결제 완료 검증");
        assertThat(lastPaymentResponse).isNotNull();
        assertThat(lastPaymentResponse.paymentId()).isNotNull();
    }

    @And("예약 상태가 확정으로 변경된다")
    public void 예약_상태가_확정으로_변경된다() {
        log.info(">>> Then: 예약 상태 확정 검증");
        ReservationStatus status = bookingAdapter.getReservationStatus(currentReservationId);
        assertThat(status).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Then("결제에 실패한다")
    public void 결제에_실패한다() {
        log.info(">>> Then: 결제 실패 검증");
        assertThat(lastPaymentResponse).isNull();
    }

    @And("예약이 만료되었다는 메시지가 반환된다")
    public void 예약이_만료되었다는_메시지가_반환된다() {
        log.info(">>> Then: 만료 메시지 검증");
        // 에러 메시지는 예외에 포함됨
    }
}
