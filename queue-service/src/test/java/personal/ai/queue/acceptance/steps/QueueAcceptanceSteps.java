package personal.ai.queue.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import personal.ai.queue.acceptance.support.QueueHttpAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Queue Acceptance Test Step Definitions
 * 비즈니스 관점의 자연어로 작성된 시나리오에 매핑
 */
@Slf4j
@ScenarioScope
@RequiredArgsConstructor
public class QueueAcceptanceSteps {

    private final QueueHttpAdapter httpAdapter;

    // 테스트 컨텍스트
    private String concertId;
    private String userId;
    private String currentToken;
    private Response lastResponse;

    // 동시성 테스트용
    private List<Response> concurrentResponses = new ArrayList<>();

    // ==========================================
    // 배경: 새로운 사용자
    // ==========================================

    @Given("새로운 사용자가 콘서트 예매를 시도한다")
    public void newUserTriesToBook() {
        this.concertId = "CONCERT-" + UUID.randomUUID().toString().substring(0, 8);
        this.userId = "USER-" + UUID.randomUUID().toString().substring(0, 8);
        log.info(">>> 새로운 사용자 생성 - concertId={}, userId={}", concertId, userId);
    }

    // ==========================================
    // Given: 사전 상태 (API 호출로 도달)
    // ==========================================

    @Given("대기열에 등록한 상태이다")
    public void alreadyInQueue() {
        log.info(">>> 대기열 등록 API 호출");
        Response response = httpAdapter.enterQueue(concertId, userId);
        assertThat(response.statusCode()).isEqualTo(201);
    }

    @And("나의 입장 차례가 되었다")
    public void waitForMyTurn() {
        log.info(">>> 입장 차례 대기 중...");

        await().atMost(10, TimeUnit.SECONDS)
                .pollInterval(500, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    Response statusResponse = httpAdapter.getQueueStatus(concertId, userId);
                    String status = statusResponse.jsonPath().getString("data.status");
                    assertThat(status).isEqualTo("READY");
                });

        Response statusResponse = httpAdapter.getQueueStatus(concertId, userId);
        currentToken = statusResponse.jsonPath().getString("data.token");
        log.info(">>> 입장 차례 도착! token={}", currentToken);
    }

    @And("예매 페이지에 입장한 상태이다")
    public void alreadyEnteredBookingPage() {
        log.info(">>> 예매 페이지 입장 API 호출");
        Response response = httpAdapter.activateToken(concertId, userId);
        assertThat(response.statusCode()).isEqualTo(200);
        currentToken = response.jsonPath().getString("data.token");
    }

    @And("이미 1회 시간을 연장한 상태이다")
    public void alreadyExtendedOnce() {
        log.info(">>> 시간 연장 API 호출 (1회)");
        Response response = httpAdapter.extendToken(concertId, userId);
        assertThat(response.statusCode()).isEqualTo(200);
    }

    // ==========================================
    // When: 사용자 행동
    // ==========================================

    @When("대기열에 등록을 요청한다")
    public void requestQueueRegistration() {
        log.info(">>> 대기열 등록 요청");
        lastResponse = httpAdapter.enterQueue(concertId, userId);
    }

    @When("{int}명의 사용자가 동시에 대기열 등록을 요청한다")
    public void multipleUsersRequestRegistration(int count) {
        log.info(">>> {}명 동시 대기열 등록 요청 (Virtual Thread)", count);

        concurrentResponses.clear();

        // Java 21 Virtual Thread로 진정한 동시 요청
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Response>> futures = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                String uniqueUserId = "USER-" + i + "-" + UUID.randomUUID().toString().substring(0, 4);

                Future<Response> future = executor.submit(() -> httpAdapter.enterQueue(concertId, uniqueUserId));
                futures.add(future);
            }

            // 모든 Virtual Thread 완료 대기
            for (Future<Response> future : futures) {
                try {
                    concurrentResponses.add(future.get());
                } catch (Exception e) {
                    log.error("동시 요청 실패", e);
                }
            }
        }

        log.info(">>> 동시 등록 완료: {} 응답 수신", concurrentResponses.size());
    }

    @When("나의 대기 상태를 확인한다")
    public void checkMyQueueStatus() {
        log.info(">>> 대기 상태 확인");
        lastResponse = httpAdapter.getQueueStatus(concertId, userId);
    }

    @When("예매 페이지에 입장한다")
    public void enterBookingPage() {
        log.info(">>> 예매 페이지 입장");
        lastResponse = httpAdapter.activateToken(concertId, userId);
        if (lastResponse.statusCode() == 200) {
            currentToken = lastResponse.jsonPath().getString("data.token");
        }
    }

    @When("시간 연장을 요청한다")
    public void requestTimeExtension() {
        log.info(">>> 시간 연장 요청");
        lastResponse = httpAdapter.extendToken(concertId, userId);
    }

    @When("토큰 유효성을 확인한다")
    public void checkTokenValidity() {
        log.info(">>> 토큰 유효성 확인");
        lastResponse = httpAdapter.validateToken(concertId, userId, currentToken);
    }

    // ==========================================
    // Then: 결과 검증
    // ==========================================

    @Then("대기열 등록이 완료된다")
    public void queueRegistrationCompleted() {
        log.info(">>> 대기열 등록 완료 검증");
        assertThat(lastResponse.statusCode()).isEqualTo(201);
    }

    @Then("모든 사용자의 등록이 완료된다")
    public void allUsersRegistrationCompleted() {
        log.info(">>> 모든 사용자 등록 완료 검증");
        for (Response response : concurrentResponses) {
            assertThat(response.statusCode()).isEqualTo(201);
        }
    }

    @And("나의 대기 순번을 확인할 수 있다")
    public void canCheckMyPosition() {
        Long position = lastResponse.jsonPath().getLong("data.position");
        log.info(">>> 대기 순번 확인 - position={}", position);
        assertThat(position).isNotNull();
        assertThat(position).isGreaterThan(0);
    }

    @And("각 사용자는 서로 다른 순번을 받는다")
    public void eachUserGetsUniquePosition() {
        List<Long> positions = new ArrayList<>();
        for (Response response : concurrentResponses) {
            Long position = response.jsonPath().getLong("data.position");
            positions.add(position);
        }

        long uniqueCount = positions.stream().distinct().count();
        log.info(">>> 고유 순번 검증 - total={}, unique={}", positions.size(), uniqueCount);
        assertThat(uniqueCount).isEqualTo(positions.size());
    }

    @Then("현재 대기 중임을 알 수 있다")
    public void canSeeWaitingStatus() {
        String status = lastResponse.jsonPath().getString("data.status");
        log.info(">>> 대기 상태 검증 - status={}", status);
        assertThat(status).isEqualTo("WAITING");
    }

    @Then("입장 가능 상태임을 확인할 수 있다")
    public void canSeeReadyStatus() {
        String status = lastResponse.jsonPath().getString("data.status");
        log.info(">>> 입장 가능 상태 검증 - status={}", status);
        assertThat(status).isEqualTo("READY");
    }

    @And("입장 토큰을 받는다")
    public void receiveEntryToken() {
        String token = lastResponse.jsonPath().getString("data.token");
        log.info(">>> 토큰 수신 - token={}", token);
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        currentToken = token;
    }

    @And("토큰 만료 시간을 확인할 수 있다")
    public void canCheckTokenExpiry() {
        String expiredAt = lastResponse.jsonPath().getString("data.expiredAt");
        log.info(">>> 만료 시간 확인 - expiredAt={}", expiredAt);
        assertThat(expiredAt).isNotNull();
    }

    @Then("예매 페이지 입장이 완료된다")
    public void bookingPageEntryCompleted() {
        log.info(">>> 예매 페이지 입장 완료 검증");
        assertThat(lastResponse.statusCode()).isEqualTo(200);
        String status = lastResponse.jsonPath().getString("data.status");
        assertThat(status).isEqualTo("ACTIVE");
    }

    @And("예매를 진행할 수 있는 토큰을 받는다")
    public void receiveBookingToken() {
        String token = lastResponse.jsonPath().getString("data.token");
        log.info(">>> 예매 토큰 수신 - token={}", token);
        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
    }

    @Then("시간 연장이 완료된다")
    public void timeExtensionCompleted() {
        log.info(">>> 시간 연장 완료 검증");
        assertThat(lastResponse.statusCode()).isEqualTo(200);
    }

    @And("연장 횟수가 {int}회로 기록된다")
    public void extensionCountRecorded(int expectedCount) {
        Integer extendCount = lastResponse.jsonPath().getInt("data.extendCount");
        log.info(">>> 연장 횟수 검증 - expected={}, actual={}", expectedCount, extendCount);
        assertThat(extendCount).isEqualTo(expectedCount);
    }

    @Then("유효한 토큰임이 확인된다")
    public void tokenIsValid() {
        log.info(">>> 토큰 유효성 검증 완료");
        assertThat(lastResponse.statusCode()).isEqualTo(200);
    }
}
