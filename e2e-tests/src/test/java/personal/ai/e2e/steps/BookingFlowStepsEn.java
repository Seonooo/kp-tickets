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
 * Booking Flow Step Definitions (English)
 */
@Slf4j
public class BookingFlowStepsEn {

    private final TestContext context;
    private final QueueServiceClient queueClient;
    private final CoreServiceClient coreClient;

    public BookingFlowStepsEn(TestContext context) {
        this.context = context;
        this.queueClient = new QueueServiceClient(context.getQueueServiceUrl());
        this.coreClient = new CoreServiceClient(context.getCoreServiceUrl());
    }

    @Before
    public void setup() {
        context.reset();
    }

    // Background
    @Given("Core service is healthy")
    public void core_service_is_healthy() {
        Response response = coreClient.healthCheck();
        assertThat(response.statusCode()).isEqualTo(200);
        log.info("✓ Core Service is healthy");
    }

    @And("Queue service is healthy")
    public void queue_service_is_healthy() {
        Response response = queueClient.healthCheck();
        assertThat(response.statusCode()).isEqualTo(200);
        log.info("✓ Queue Service is healthy");
    }

    @And("Test data is prepared")
    public void test_data_is_prepared() {
        context.setConcertId("1");
        context.setScheduleId("1");
        log.info("✓ Test data initialized");
    }

    // Queue Entry
    @Given("User {string} requests to enter queue for concert {string}")
    public void user_requests_to_enter_queue(String userId, String concertId) {
        context.setUserId(userId);
        context.setConcertId(concertId);
        log.info("User {} requests queue entry for concert {}", userId, concertId);
    }

    @When("User sends queue entry request")
    public void user_sends_queue_entry_request() {
        context.startStep("queue_entry");
        Response response = queueClient.enterQueue(context.getConcertId(), context.getUserId());
        context.endStep("queue_entry");
        context.saveResponse("queue_entry", response);
    }

    @Then("Queue entry is successful")
    public void queue_entry_is_successful() {
        Response response = context.getLastResponse();
        assertThat(response.statusCode()).isEqualTo(201);
        log.info("✓ Queue entry successful");
    }

    @And("User receives queue position")
    public void user_receives_queue_position() {
        Response response = context.getLastResponse();
        Integer position = response.jsonPath().getInt("data.position");
        assertThat(position).isGreaterThanOrEqualTo(0);
        log.info("✓ Queue position: {}", position);
    }

    // Queue Polling
    @When("User polls queue status")
    public void user_polls_queue_status() {
        context.startStep("activation_wait");
    }

    @Then("User becomes {string} within 2 minutes")
    public void user_becomes_ready_within_minutes(String expectedStatus) {
        await()
                .atMost(2, TimeUnit.MINUTES)
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

    @And("User receives queue token")
    public void user_receives_queue_token() {
        // Queue status API에서 실제 토큰 추출
        Response response = queueClient.getQueueStatus(
                context.getConcertId(),
                context.getUserId()
        );
        String token = response.jsonPath().getString("data.token");
        context.setQueueToken(token);
        log.info("✓ Queue token received: {}", token);
    }

    // Seats Query
    @When("User queries seats for schedule {string}")
    public void user_queries_seats(String scheduleId) {
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

    @Then("Seats query is successful")
    public void seats_query_is_successful() {
        Response response = context.getLastResponse();
        assertThat(response.statusCode()).isEqualTo(200);
        log.info("✓ Seats query successful");
    }

    @And("Available seats exist")
    public void available_seats_exist() {
        Response response = context.getLastResponse();
        List<Object> seats = response.jsonPath().getList("$");
        assertThat(seats).isNotEmpty();
        log.info("✓ Available seats found: {} seats", seats.size());
    }

    // Reservation
    @When("User selects first available seat")
    public void user_selects_first_available_seat() {
        Response response = context.getResponses().get("seats_query");
        List<Long> availableSeats = response.jsonPath().getList(
                "findAll { it.status == 'AVAILABLE' }.seatId",
                Long.class
        );
        assertThat(availableSeats).isNotEmpty();
        context.setSelectedSeatId(availableSeats.get(0));
        log.info("✓ Selected seat: {}", context.getSelectedSeatId());
    }

    @And("User requests seat reservation")
    public void user_requests_seat_reservation() {
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

    @Then("Seat reservation is successful")
    public void seat_reservation_is_successful() {
        Response response = context.getLastResponse();
        assertThat(response.statusCode()).isEqualTo(201);
        log.info("✓ Reservation successful");
    }

    @And("User receives reservation ID")
    public void user_receives_reservation_id() {
        Response response = context.getLastResponse();
        Long reservationId = response.jsonPath().getLong("data.reservationId");
        context.setReservationId(reservationId);
        log.info("✓ Reservation ID: {}", reservationId);
    }

    // Payment
    @When("User requests payment for reservation")
    public void user_requests_payment() {
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

    @Then("Payment is successful")
    public void payment_is_successful() {
        Response response = context.getLastResponse();
        assertThat(response.statusCode()).isEqualTo(201);
        log.info("✓ Payment successful");
    }

    @And("User receives payment ID")
    public void user_receives_payment_id() {
        Response response = context.getLastResponse();
        Long paymentId = response.jsonPath().getLong("data.paymentId");
        context.setPaymentId(paymentId);
        log.info("✓ Payment ID: {}", paymentId);
    }

    // Kafka Event
    @When("System waits for Kafka event processing")
    public void system_waits_for_kafka_event() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Then("User is automatically removed from queue within 10 seconds")
    public void user_is_removed_from_queue() {
        await()
                .atMost(10, TimeUnit.SECONDS)
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
    }
}
