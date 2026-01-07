package personal.ai.e2e.steps;

import io.cucumber.java.Before;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.And;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import personal.ai.e2e.client.CoreServiceClient;
import personal.ai.e2e.client.QueueServiceClient;
import personal.ai.e2e.context.TestContext;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * 예매 플로우 Step Definitions (한글)
 * 영어 애너테이션 + 한글 Gherkin 텍스트 매칭
 */
@Slf4j
public class BookingFlowStepsKo {

    private final TestContext context;
    private final QueueServiceClient queueClient;
    private final CoreServiceClient coreClient;

    public BookingFlowStepsKo(TestContext context) {
        this.context = context;
        this.queueClient = new QueueServiceClient(context.getQueueServiceUrl());
        this.coreClient = new CoreServiceClient(context.getCoreServiceUrl());
    }

    @Before
    public void setup() {
        context.reset();
    }

    // 배경
    @Given("콘서트 서비스가 정상 작동중이다")
    public void 콘서트_서비스가_정상_작동중이다() {
        Response response = coreClient.healthCheck();
        assertThat(response.statusCode()).isEqualTo(200);
        log.info("✓ Core Service is healthy");
    }

    @And("큐 서비스가 정상 작동중이다")
    public void 큐_서비스가_정상_작동중이다() {
        Response response = queueClient.healthCheck();
        assertThat(response.statusCode()).isEqualTo(200);
        log.info("✓ Queue Service is healthy");
    }

    @And("테스트 데이터가 준비되어있다")
    public void 테스트_데이터가_준비되어있다() {
        context.setConcertId("1");
        context.setScheduleId("1");
        log.info("✓ Test data initialized");
    }

    // 대기열 진입
    @Given("사용자 {string}가 콘서트 {string}에 대기열 진입을 요청한다")
    public void 사용자가_콘서트에_대기열_진입을_요청한다(String userId, String concertId) {
        context.setUserId(userId);
        context.setConcertId(concertId);
        log.info("User {} requests queue entry for concert {}", userId, concertId);
    }

    @When("대기열 진입 요청을 보낸다")
    public void 대기열_진입_요청을_보낸다() {
        context.startStep("queue_entry");
        Response response = queueClient.enterQueue(context.getConcertId(), context.getUserId());
        context.endStep("queue_entry");
        context.saveResponse("queue_entry", response);
    }

    @Then("대기열 진입이 성공한다")
    public void 대기열_진입이_성공한다() {
        Response response = context.getLastResponse();
        assertThat(response.statusCode()).isEqualTo(201);
        log.info("✓ Queue entry successful");
    }

    @And("대기 순번을 받는다")
    public void 대기_순번을_받는다() {
        Response response = context.getLastResponse();
        Integer position = response.jsonPath().getInt("data.position");
        assertThat(position).isGreaterThanOrEqualTo(0);
        log.info("✓ Queue position: {}", position);
    }

    // 대기열 폴링
    @When("대기열 상태를 폴링한다")
    public void 대기열_상태를_폴링한다() {
        context.startStep("activation_wait");
    }

    @Then("{int}분 이내에 {string} 상태가 된다")
    public void 분_이내에_상태가_된다(int minutes, String expectedStatus) {
        await()
                .atMost(minutes, TimeUnit.MINUTES)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    Response response = queueClient.getQueueStatus(
                            context.getConcertId(),
                            context.getUserId()
                    );

                    if (response.statusCode() == 200) {
                        String status = response.jsonPath().getString("data.status");
                        return status.equals(expectedStatus) || status.equals("ACTIVE");
                    }
                    return false;
                });

        context.endStep("activation_wait");
        log.info("✓ Queue status is READY/ACTIVE");
    }

    @And("큐 토큰을 받는다")
    public void 큐_토큰을_받는다() {
        // Queue status API에서 실제 토큰 추출
        Response response = queueClient.getQueueStatus(
                context.getConcertId(),
                context.getUserId()
        );
        String token = response.jsonPath().getString("data.token");
        context.setQueueToken(token);
        log.info("✓ Queue token received: {}", token);
    }

    // 좌석 조회
    @When("스케줄 {string}의 좌석을 조회한다")
    public void 스케줄의_좌석을_조회한다(String scheduleId) {
        context.setScheduleId(scheduleId);
        context.startStep("seats_query");
        Response response = coreClient.getSeats(
                scheduleId,
                context.getUserId(),
                context.getQueueToken()
        );
        context.endStep("seats_query");
        context.saveResponse("seats_query", response);
    }

    @Then("좌석 조회가 성공한다")
    public void 좌석_조회가_성공한다() {
        Response response = context.getLastResponse();
        assertThat(response.statusCode()).isEqualTo(200);
        log.info("✓ Seats query successful");
    }

    @And("예약 가능한 좌석이 존재한다")
    public void 예약_가능한_좌석이_존재한다() {
        Response response = context.getLastResponse();
        List<Object> seats = response.jsonPath().getList("$");
        assertThat(seats).isNotEmpty();
        log.info("✓ Available seats found: {} seats", seats.size());
    }

    // 예약
    @When("첫번째 예약 가능 좌석을 선택한다")
    public void 첫번째_예약_가능_좌석을_선택한다() {
        Response response = context.getResponses().get("seats_query");
        List<Long> availableSeats = response.jsonPath().getList(
                "findAll { it.status == 'AVAILABLE' }.seatId",
                Long.class
        );
        assertThat(availableSeats).isNotEmpty();
        context.setSelectedSeatId(availableSeats.get(0));
        log.info("✓ Selected seat: {}", context.getSelectedSeatId());
    }

    @And("좌석 예약을 요청한다")
    public void 좌석_예약을_요청한다() {
        context.startStep("reservation");
        Response response = coreClient.reserveSeat(
                context.getScheduleId(),
                context.getSelectedSeatId(),
                context.getUserId(),
                context.getQueueToken()
        );
        context.endStep("reservation");
        context.saveResponse("reservation", response);
    }

    @Then("좌석 예약이 성공한다")
    public void 좌석_예약이_성공한다() {
        Response response = context.getLastResponse();
        assertThat(response.statusCode()).isEqualTo(201);
        log.info("✓ Reservation successful");
    }

    @And("예약 ID를 받는다")
    public void 예약_ID를_받는다() {
        Response response = context.getLastResponse();
        Long reservationId = response.jsonPath().getLong("data.reservationId");
        context.setReservationId(reservationId);
        log.info("✓ Reservation ID: {}", reservationId);
    }

    // 결제
    @When("예약에 대해 결제를 요청한다")
    public void 예약에_대해_결제를_요청한다() {
        context.startStep("payment");
        Response response = coreClient.processPayment(
                context.getReservationId(),
                context.getUserId(),
                50000,
                context.getConcertId()
        );
        context.endStep("payment");
        context.saveResponse("payment", response);
    }

    @Then("결제가 성공한다")
    public void 결제가_성공한다() {
        Response response = context.getLastResponse();
        assertThat(response.statusCode()).isEqualTo(201);
        log.info("✓ Payment successful");
    }

    @And("결제 ID를 받는다")
    public void 결제_ID를_받는다() {
        Response response = context.getLastResponse();
        Long paymentId = response.jsonPath().getLong("data.paymentId");
        context.setPaymentId(paymentId);
        log.info("✓ Payment ID: {}", paymentId);
    }

    // Kafka 이벤트
    @When("Kafka 이벤트 처리를 대기한다")
    public void Kafka_이벤트_처리를_대기한다() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Then("{int}초 이내에 대기열에서 자동 제거된다")
    public void 초_이내에_대기열에서_자동_제거된다(int seconds) {
        await()
                .atMost(seconds, TimeUnit.SECONDS)
                .pollInterval(1, TimeUnit.SECONDS)
                .until(() -> {
                    Response response = queueClient.getQueueStatus(
                            context.getConcertId(),
                            context.getUserId()
                    );
                    if (response.statusCode() == 404) {
                        return true;
                    }
                    if (response.statusCode() == 200) {
                        String status = response.jsonPath().getString("data.status");
                        return "EXPIRED".equals(status) || "NOT_FOUND".equals(status);
                    }
                    return false;
                });

        log.info("✓ User removed from queue automatically");

        // 단계별 응답 시간 출력
        log.info("=== Step Durations ===");
        context.getStepDurations().forEach((step, duration) -> {
            log.info("{}: {}ms", step, duration);
        });
    }
}
