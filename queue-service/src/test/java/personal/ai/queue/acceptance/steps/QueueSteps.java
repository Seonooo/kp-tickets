package personal.ai.queue.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.cucumber.spring.ScenarioScope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import personal.ai.common.dto.ApiResponse;
import personal.ai.queue.acceptance.support.QueueTestAdapter;
import personal.ai.queue.adapter.in.web.dto.QueuePositionResponse;
import personal.ai.queue.adapter.in.web.dto.QueueTokenResponse;
import personal.ai.queue.application.port.in.MoveToActiveQueueUseCase;
import personal.ai.queue.domain.model.QueueStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Queue Service API 인수 테스트 Step Definitions
 *
 * 이 클래스는 Cucumber Feature 파일의 각 시나리오 단계(Given/When/Then)를
 * 실제 테스트 코드로 매핑합니다.
 *
 * @ScenarioScope: 각 시나리오마다 새로운 인스턴스를 생성하여 테스트 격리를 보장합니다.
 *                 이를 통해 시나리오 간 상태 공유 문제를 방지하고, 병렬 실행 시에도 안전합니다.
 *                 Cucumber-Spring이 step definition 클래스의 생명주기를 관리하므로
 * @Component 어노테이션은 사용하지 않습니다.
 *
 * @author AI Queue Team
 */
@Slf4j
@ScenarioScope
@RequiredArgsConstructor
public class QueueSteps {

    // ==========================================
    // 의존성 주입
    // ==========================================

    /** API 호출을 위한 테스트 어댑터 */
    private final QueueTestAdapter queueAdapter;

    /** 대기열 전환 UseCase (스케줄러 시뮬레이션용) */
    private final MoveToActiveQueueUseCase moveToActiveQueueUseCase;

    // ==========================================
    // 테스트 컨텍스트 (시나리오 간 상태 공유)
    // ==========================================

    /** 현재 테스트 중인 콘서트 ID */
    private String currentConcertId;

    /** 현재 테스트 중인 사용자 ID */
    private String currentUserId;

    /** 현재 발급된 토큰 */
    private String currentToken;

    /** 마지막 API 응답 */
    private ResponseEntity<?> lastResponse;

    /** 마지막 대기열 진입 응답 */
    private QueuePositionResponse lastPosition;

    /** 마지막 토큰 응답 */
    private QueueTokenResponse lastTokenResponse;

    /** 동시성 테스트용 사용자 ID 목록 */
    private List<String> multipleUserIds = new ArrayList<>();

    /** SSE 연결 객체 */
    private SseEmitter sseEmitter;

    /** SSE 이벤트 데이터 */
    private String lastSseEventData;

    // ==========================================
    // Given 단계: 테스트 사전 조건 설정
    // ==========================================

    /**
     * 배경: 대기열 시스템 초기화
     * Redis의 모든 대기열 데이터를 초기화하여 깨끗한 상태로 테스트를 시작합니다.
     */
    @Given("대기열 시스템이 준비되어 있다")
    public void 대기열_시스템이_준비되어_있다() {
        log.info(">>> Given: 대기열 시스템 초기화");
        queueAdapter.clearAllQueues();
    }

    /**
     * 콘서트 설정
     * 테스트에서 사용할 콘서트 ID를 설정합니다.
     *
     * @param concertId 콘서트 ID (예: "CONCERT-001")
     */
    @Given("콘서트 {string}이 있다")
    public void 콘서트가_있다(String concertId) {
        log.info(">>> Given: 콘서트 설정 - {}", concertId);
        this.currentConcertId = concertId;
    }

    /**
     * 사용자 설정
     * 테스트에서 사용할 사용자 ID를 설정합니다.
     *
     * @param userId 사용자 ID (예: "USER-001")
     */
    @Given("사용자 {string}이 있다")
    public void 사용자가_있다(String userId) {
        log.info(">>> Given: 사용자 설정 - {}", userId);
        this.currentUserId = userId;
    }

    /**
     * 사전 조건: 사용자가 이미 대기 큐에 있음 (WAITING 상태)
     * 사용자를 대기열에 진입시켜 WAITING 상태로 만듭니다.
     *
     * @param userId 사용자 ID
     */
    @And("사용자 {string}이 대기 큐에 있다")
    public void 사용자가_대기_큐에_있다(String userId) {
        log.info(">>> Given: 사용자를 대기 큐에 추가 - {}", userId);
        this.currentUserId = userId;
        // POST /api/v1/queue/enter 호출
        queueAdapter.enterQueue(currentConcertId, userId);
    }

    /**
     * 사전 조건: 사용자가 이미 활성 큐에 있음 (READY 상태)
     * 사용자를 대기열에 진입시킨 후, 활성 큐로 전환하여 READY 상태로 만듭니다.
     *
     * @param userId 사용자 ID
     */
    @And("사용자 {string}이 활성 큐에 있다")
    public void 사용자가_활성_큐에_있다(String userId) {
        log.info(">>> Given: 사용자를 활성 큐에 추가 - {}", userId);
        this.currentUserId = userId;
        // 1. 대기열 진입
        queueAdapter.enterQueue(currentConcertId, userId);
        // 2. 활성 큐로 전환 (스케줄러 동작 시뮬레이션)
        moveToActiveQueueUseCase.moveWaitingToActive(currentConcertId);
        // 3. 상태 조회하여 토큰 정보 저장
        lastTokenResponse = queueAdapter.getQueueStatus(currentConcertId, userId);
        currentToken = lastTokenResponse.token();
    }

    /**
     * 사전 조건: 사용자가 이미 활성 상태임 (ACTIVE 상태)
     * 사용자를 대기열 진입 → 활성 큐 전환 → 토큰 활성화까지 완료하여 ACTIVE 상태로 만듭니다.
     *
     * @param userId 사용자 ID
     */
    @And("사용자 {string}이 활성 상태이다")
    public void 사용자가_활성_상태이다(String userId) {
        log.info(">>> Given: 사용자를 활성 상태로 설정 - {}", userId);
        this.currentUserId = userId;
        // 1. 대기열 진입
        queueAdapter.enterQueue(currentConcertId, userId);
        // 2. 활성 큐로 전환
        moveToActiveQueueUseCase.moveWaitingToActive(currentConcertId);
        // 3. 토큰 활성화 (POST /api/v1/queue/activate)
        lastTokenResponse = queueAdapter.activateToken(currentConcertId, userId);
        currentToken = lastTokenResponse.token();
    }

    /**
     * 사전 조건: 사용자가 이미 N회 토큰 연장을 완료함
     * 지정된 횟수만큼 토큰 연장 API를 호출합니다.
     *
     * @param times 연장 횟수 (1 또는 2)
     */
    @And("이미 {int}회 연장했다")
    public void 이미_회_연장했다(Integer times) {
        log.info(">>> Given: {}회 토큰 연장 완료", times);
        for (int i = 0; i < times; i++) {
            // POST /api/v1/queue/extend 호출
            lastTokenResponse = queueAdapter.extendToken(currentConcertId, currentUserId);
        }
    }

    /**
     * 사전 조건: 사용자가 유효한 토큰을 보유함
     * 현재 발급된 토큰을 유효한 토큰으로 설정합니다.
     */
    @And("유효한 토큰을 가지고 있다")
    public void 유효한_토큰을_가지고_있다() {
        assertThat(lastTokenResponse)
                .as("유효한 토큰 Given 단계 전에 토큰 발급이 선행되어야 합니다")
                .isNotNull();
        assertThat(lastTokenResponse.token())
                .as("발급된 토큰이 존재해야 합니다")
                .isNotNull();
        this.currentToken = lastTokenResponse.token();
        log.info(">>> Given: 유효한 토큰 설정 - token={}", currentToken);
    }

    // ==========================================
    // When 단계: API 호출 및 동작 실행
    // ==========================================

    /**
     * API 호출: POST /api/v1/queue/enter
     * 사용자가 대기열 진입 API를 호출합니다.
     */
    @When("사용자가 대기열 진입 API를 호출한다")
    public void 사용자가_대기열_진입_API를_호출한다() {
        log.info(">>> When: POST /api/v1/queue/enter 호출 - concertId={}, userId={}",
                currentConcertId, currentUserId);

        // API 호출
        lastResponse = queueAdapter.enterQueueRequest(currentConcertId, currentUserId);

        // 성공 시 응답 데이터 저장
        if (lastResponse.getStatusCode() == HttpStatus.CREATED) {
            @SuppressWarnings("unchecked")
            ApiResponse<QueuePositionResponse> apiResponse = (ApiResponse<QueuePositionResponse>) lastResponse
                    .getBody();
            lastPosition = apiResponse.data();
        }
    }

    /**
     * API 호출: POST /api/v1/queue/enter (동시성 테스트)
     * 여러 사용자가 동시에 대기열 진입 API를 호출합니다.
     * Java 21 Virtual Threads를 사용하여 대규모 동시 요청을 처리합니다.
     *
     * @param count 동시 진입할 사용자 수 (예: 100)
     */
    @When("{int}명의 사용자가 동시에 진입 API를 호출한다")
    public void 명의_사용자가_동시에_진입_API를_호출한다(Integer count) throws InterruptedException {
        log.info(">>> When: {}명의 사용자가 동시에 POST /api/v1/queue/enter 호출", count);

        multipleUserIds.clear();
        var latch = new java.util.concurrent.CountDownLatch(count);
        var errors = new java.util.concurrent.CopyOnWriteArrayList<Throwable>();

        // Virtual Thread Executor 사용 (Java 21)
        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < count; i++) {
                String userId = "USER-CONCURRENT-" + i;
                multipleUserIds.add(userId);

                executor.submit(() -> {
                    try {
                        queueAdapter.enterQueue(currentConcertId, userId);
                    } catch (Exception e) {
                        log.error("동시 진입 실패: userId={}", userId, e);
                        errors.add(e);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            // 타임아웃과 함께 대기 (30초)
            boolean completed = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);
            assertThat(completed).as("동시 진입이 30초 내에 완료되어야 합니다").isTrue();
            assertThat(errors).as("동시 진입 중 에러가 발생하면 안 됩니다").isEmpty();
        }

        log.info(">>> 모든 동시 진입 요청 완료: count={}", count);
    }

    /**
     * API 호출: GET /api/v1/queue/status
     * 사용자가 상태 조회 API를 호출합니다.
     */
    @When("상태 조회 API를 호출한다")
    public void 상태_조회_API를_호출한다() {
        log.info(">>> When: GET /api/v1/queue/status 호출 - concertId={}, userId={}",
                currentConcertId, currentUserId);

        // API 호출
        lastTokenResponse = queueAdapter.getQueueStatus(currentConcertId, currentUserId);
    }

    /**
     * API 호출: POST /api/v1/queue/activate
     * 사용자가 토큰 활성화 API를 호출합니다 (READY → ACTIVE).
     */
    @When("토큰 활성화 API를 호출한다")
    public void 토큰_활성화_API를_호출한다() {
        log.info(">>> When: POST /api/v1/queue/activate 호출 - concertId={}, userId={}",
                currentConcertId, currentUserId);

        // API 호출
        lastTokenResponse = queueAdapter.activateToken(currentConcertId, currentUserId);
        currentToken = lastTokenResponse.token();
    }

    /**
     * API 호출: POST /api/v1/queue/extend
     * 사용자가 토큰 연장 API를 호출합니다.
     */
    @When("토큰 연장 API를 호출한다")
    public void 토큰_연장_API를_호출한다() {
        log.info(">>> When: POST /api/v1/queue/extend 호출 - concertId={}, userId={}",
                currentConcertId, currentUserId);

        // API 호출
        lastTokenResponse = queueAdapter.extendToken(currentConcertId, currentUserId);
        lastResponse = ResponseEntity.ok().build();
    }

    /**
     * API 호출: POST /api/v1/queue/validate
     * 사용자가 토큰 검증 API를 호출합니다.
     */
    @When("토큰 검증 API를 호출한다")
    public void 토큰_검증_API를_호출한다() {
        log.info(">>> When: POST /api/v1/queue/validate 호출 - concertId={}, userId={}, token={}",
                currentConcertId, currentUserId, currentToken);

        try {
            // API 호출
            queueAdapter.validateToken(currentConcertId, currentUserId, currentToken);
            lastResponse = ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error(">>> 토큰 검증 실패", e);
            lastResponse = ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e);
        }
    }

    /**
     * API 호출: GET /api/v1/queue/subscribe (실제 SSE 대신 폴링 Mock)
     * 
     * Acceptance Test에서는 SSE 연결 대신 상태 폴링으로 결과 검증
     * 실제 SSE 동작은 Integration Test에서 검증
     */
    @When("SSE 구독 API를 호출한다")
    public void SSE_구독_API를_호출한다() {
        log.info(">>> When: SSE 구독 API 호출 (Mock: 상태 폴링) - concertId={}, userId={}",
                currentConcertId, currentUserId);

        // SSE 연결 대신 상태 조회로 대체
        lastTokenResponse = queueAdapter.getQueueStatus(currentConcertId, currentUserId);
        log.info(">>> SSE Mock: 현재 상태 = {}", lastTokenResponse.status());
    }

    /**
     * 이벤트 발행: Kafka 결제 완료 이벤트
     * 결제가 완료되어 사용자를 대기열에서 제거하는 이벤트를 발행합니다.
     */
    @When("결제 완료 이벤트가 발행된다")
    public void 결제_완료_이벤트가_발행된다() {
        log.info(">>> When: 결제 완료 이벤트 발행 - concertId={}, userId={}",
                currentConcertId, currentUserId);

        // Kafka 이벤트 발행
        queueAdapter.publishPaymentCompletedEvent(currentConcertId, currentUserId);

        // 이벤트 처리 완료 대기 (조건 기반 - Awaitility)
        await().atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() -> {
                    QueueTokenResponse status = queueAdapter.getQueueStatus(currentConcertId, currentUserId);
                    assertThat(status.status()).isEqualTo(QueueStatus.NOT_FOUND);
                });
    }

    /**
     * 내부 동작: 대기열 전환 (스케줄러 시뮬레이션)
     * 대기 큐(WAITING)에서 활성 큐(READY)로 사용자를 전환합니다.
     * 실제로는 스케줄러가 주기적으로 실행하지만, 테스트에서는 수동으로 호출합니다.
     */
    @When("대기열이 전환된다")
    public void 대기열이_전환된다() {
        log.info(">>> When: 대기열 전환 실행 (스케줄러 시뮬레이션) - concertId={}", currentConcertId);

        // 대기 큐 → 활성 큐 전환
        moveToActiveQueueUseCase.moveWaitingToActive(currentConcertId);
    }

    /**
     * SSE 이벤트: 사용자 상태 변경
     * SSE를 통해 사용자의 상태가 변경되는 시뮬레이션입니다.
     *
     * @param status 변경될 상태 (예: "READY")
     */
    @When("사용자 상태가 {string}로 변경된다")
    public void 사용자_상태가_로_변경된다(String status) {
        log.info(">>> When: 사용자 상태 변경 시뮬레이션 - {}", status);

        // 대기열 전환 (WAITING → READY)
        if ("READY".equals(status)) {
            moveToActiveQueueUseCase.moveWaitingToActive(currentConcertId);
        }

        // SSE 이벤트 데이터 설정
        lastTokenResponse = queueAdapter.getQueueStatus(currentConcertId, currentUserId);
    }

    // ==========================================
    // Then/And 단계: 결과 검증
    // ==========================================

    /**
     * 검증: 대기열 진입 성공
     */
    @Then("대기열 진입이 성공한다")
    public void 대기열_진입이_성공한다() {
        log.info(">>> Then: 대기열 진입 성공 검증");
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    /**
     * 검증: 대기 순번 수신
     */
    @And("대기 순번을 받는다")
    public void 대기_순번을_받는다() {
        assertThat(lastPosition).as("대기 순번 응답이 null입니다").isNotNull();
        assertThat(lastPosition.position()).as("대기 순번이 null입니다").isNotNull();
        assertThat(lastPosition.position()).isGreaterThan(0);
        log.info(">>> Then: 대기 순번 확인 - position={}", lastPosition.position());
    }

    /**
     * 검증: 대기열 상태 확인
     *
     * @param status 예상 상태 (WAITING, READY, ACTIVE, NOT_FOUND 등)
     */
    @Then("상태가 {string}이다")
    public void 상태가_이다(String status) {
        assertThat(lastTokenResponse).as("토큰 응답이 null입니다").isNotNull();
        assertThat(lastTokenResponse.status()).isEqualTo(QueueStatus.valueOf(status));
        log.info(">>> Then: 상태 확인 - expected={}, actual={}", status, lastTokenResponse.status());
    }

    /**
     * 검증: 상태가 특정 값으로 변경됨
     *
     * @param status 변경된 상태
     */
    @And("상태가 {string}로 변경된다")
    public void 상태가_로_변경된다(String status) {
        assertThat(lastTokenResponse).as("토큰 응답이 null입니다").isNotNull();
        assertThat(lastTokenResponse.status()).isEqualTo(QueueStatus.valueOf(status));
        log.info(">>> Then: 상태 변경 확인 - status={}", lastTokenResponse.status());
    }

    /**
     * 검증: 대기 순번 표시됨
     */
    @And("대기 순번이 표시된다")
    public void 대기_순번이_표시된다() {
        assertThat(lastTokenResponse).as("토큰 응답이 null입니다").isNotNull();
        assertThat(lastTokenResponse.position()).as("대기 순번이 null입니다").isNotNull();
        assertThat(lastTokenResponse.position()).isGreaterThan(0);
        log.info(">>> Then: 대기 순번 표시 확인 - position={}", lastTokenResponse.position());
    }

    /**
     * 검증: 토큰이 반환됨
     */
    @And("토큰이 반환된다")
    public void 토큰이_반환된다() {
        assertThat(lastTokenResponse).as("토큰 응답이 null입니다").isNotNull();
        assertThat(lastTokenResponse.token()).as("토큰이 null입니다").isNotNull();
        assertThat(lastTokenResponse.token()).isNotEmpty();
        log.info(">>> Then: 토큰 반환 확인 - token={}", lastTokenResponse.token());
    }

    /**
     * 검증: 만료 시간이 표시됨
     */
    @And("만료 시간이 표시된다")
    public void 만료_시간이_표시된다() {
        assertThat(lastTokenResponse).as("토큰 응답이 null입니다").isNotNull();
        assertThat(lastTokenResponse.expiredAt()).as("만료 시간이 null입니다").isNotNull();
        assertThat(lastTokenResponse.expiredAt()).isAfter(Instant.now());
        log.info(">>> Then: 만료 시간 확인 - expiredAt={}", lastTokenResponse.expiredAt());
    }

    /**
     * 검증: 연장 횟수가 표시됨
     */
    @And("연장 횟수가 표시된다")
    public void 연장_횟수가_표시된다() {
        assertThat(lastTokenResponse).as("토큰 응답이 null입니다").isNotNull();
        assertThat(lastTokenResponse.extendCount()).as("연장 횟수가 null입니다").isNotNull();
        log.info(">>> Then: 연장 횟수 확인 - extendCount={}", lastTokenResponse.extendCount());
    }

    /**
     * 검증: 토큰 활성화 성공
     */
    @Then("토큰 활성화가 성공한다")
    public void 토큰_활성화가_성공한다() {
        log.info(">>> Then: 토큰 활성화 성공 검증");
        assertThat(lastTokenResponse).isNotNull();
        assertThat(lastTokenResponse.token()).isNotNull();
    }

    /**
     * 검증: 만료 시간이 10분으로 설정됨
     */
    @And("만료 시간이 10분으로 설정된다")
    public void 만료_시간이_10분으로_설정된다() {
        assertThat(lastTokenResponse).as("토큰 응답이 null입니다").isNotNull();
        assertThat(lastTokenResponse.expiredAt()).as("만료 시간이 null입니다").isNotNull();

        Instant now = Instant.now();
        Instant expiredAt = lastTokenResponse.expiredAt();
        Instant expectedMin = now.plusSeconds(10 * 60 - 10); // 9분 50초 후
        Instant expectedMax = now.plusSeconds(10 * 60 + 10); // 10분 10초 후

        assertThat(expiredAt).isBetween(expectedMin, expectedMax);
        log.info(">>> Then: 만료 시간 10분 설정 확인 - expiredAt={}", expiredAt);
    }

    /**
     * 검증: 토큰 연장 성공
     */
    @Then("토큰 연장이 성공한다")
    public void 토큰_연장이_성공한다() {
        log.info(">>> Then: 토큰 연장 성공 검증");
        assertThat(lastTokenResponse).isNotNull();
        assertThat(lastTokenResponse.expiredAt()).isAfter(Instant.now());
    }

    /**
     * 검증: 연장 횟수가 1회
     */
    @And("연장 횟수가 1이다")
    public void 연장_횟수가_1이다() {
        log.info(">>> Then: 연장 횟수 1회 검증 - extendCount={}", lastTokenResponse.extendCount());
        assertThat(lastTokenResponse.extendCount()).isEqualTo(1);
    }

    /**
     * 검증: 연장 횟수가 2회
     */
    @And("연장 횟수가 2이다")
    public void 연장_횟수가_2이다() {
        log.info(">>> Then: 연장 횟수 2회 검증 - extendCount={}", lastTokenResponse.extendCount());
        assertThat(lastTokenResponse.extendCount()).isEqualTo(2);
    }

    /**
     * 검증: 만료 시간이 갱신됨
     */
    @And("만료 시간이 갱신된다")
    public void 만료_시간이_갱신된다() {
        log.info(">>> Then: 만료 시간 갱신 검증 - expiredAt={}", lastTokenResponse.expiredAt());
        assertThat(lastTokenResponse.expiredAt()).isAfter(Instant.now());
    }

    /**
     * 검증: 토큰 검증 성공
     */
    @Then("토큰 검증이 성공한다")
    public void 토큰_검증이_성공한다() {
        log.info(">>> Then: 토큰 검증 성공");
        assertThat(lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    /**
     * 검증: SSE 연결 성공 (Mock: 상태가 존재함)
     */
    @Then("SSE 연결이 성공한다")
    public void SSE_연결이_성공한다() {
        log.info(">>> Then: SSE 연결 성공 검증 (Mock: 상태 조회 가능)");
        // SSE 연결 대신 상태 조회 가능 여부로 검증
        assertThat(lastTokenResponse).isNotNull();
        assertThat(lastTokenResponse.status()).isNotNull();
    }

    /**
     * 검증: SSE 이벤트 수신 (Mock: 상태 변경 확인)
     */
    @Then("SSE 이벤트를 수신한다")
    public void SSE_이벤트를_수신한다() {
        log.info(">>> Then: SSE 이벤트 수신 검증 (Mock: 상태 변경 확인)");
        // SSE 이벤트 대신 상태 조회로 변경 확인
        assertThat(lastTokenResponse).isNotNull();
    }

    /**
     * 검증: SSE 이벤트 데이터의 상태 확인
     *
     * @param status 예상 상태
     */
    @And("이벤트 데이터의 상태는 {string}이다")
    public void 이벤트_데이터의_상태는_이다(String status) {
        log.info(">>> Then: SSE 이벤트 상태 검증 - expected={}", status);
        // SSE 이벤트 대신 현재 상태 조회로 검증
        assertThat(lastTokenResponse.status()).isEqualTo(QueueStatus.valueOf(status));
    }

    /**
     * 검증: SSE 이벤트 데이터에 토큰 포함
     */
    @And("이벤트 데이터에 토큰이 포함된다")
    public void 이벤트_데이터에_토큰이_포함된다() {
        log.info(">>> Then: SSE 이벤트 토큰 포함 검증");
        // SSE 이벤트 대신 현재 상태 조회로 검증
        assertThat(lastTokenResponse.token()).isNotNull();
    }

    /**
     * 검증: 대기열에서 제거됨
     */
    @Then("대기열에서 제거된다")
    public void 대기열에서_제거된다() {
        log.info(">>> Then: 대기열 제거 검증");

        // 상태 조회하여 NOT_FOUND 확인
        QueueTokenResponse status = queueAdapter.getQueueStatus(currentConcertId, currentUserId);
        assertThat(status.status()).isEqualTo(QueueStatus.NOT_FOUND);
    }

    /**
     * 검증: 모든 사용자가 대기열에 추가됨 (동시성 테스트)
     */
    @Then("모든 사용자가 대기열에 추가된다")
    public void 모든_사용자가_대기열에_추가된다() {
        log.info(">>> Then: 모든 사용자 대기열 추가 검증 - count={}", multipleUserIds.size());

        for (String userId : multipleUserIds) {
            QueueTokenResponse status = queueAdapter.getQueueStatus(currentConcertId, userId);
            assertThat(status.status()).isIn(QueueStatus.WAITING, QueueStatus.READY, QueueStatus.ACTIVE);
        }
    }

    /**
     * 검증: 각 사용자가 고유한 순번을 받음 (동시성 테스트)
     */
    @And("각 사용자는 고유한 순번을 받는다")
    public void 각_사용자는_고유한_순번을_받는다() {
        log.info(">>> Then: 고유 순번 할당 검증 - count={}", multipleUserIds.size());

        // 모든 사용자의 순번을 수집
        List<Long> positions = new ArrayList<>();
        for (String userId : multipleUserIds) {
            QueueTokenResponse status = queueAdapter.getQueueStatus(currentConcertId, userId);
            if (status.position() != null) {
                positions.add(status.position());
            }
        }

        // 순번 중복 검증
        long uniqueCount = positions.stream().distinct().count();
        assertThat(uniqueCount).isEqualTo(positions.size());
        log.info(">>> 고유 순번 개수: {}", uniqueCount);
    }
}
