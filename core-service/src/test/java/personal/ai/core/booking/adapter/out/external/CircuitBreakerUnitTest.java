package personal.ai.core.booking.adapter.out.external;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import personal.ai.common.exception.BusinessException;
import personal.ai.common.exception.ErrorCode;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.await;

/**
 * Circuit Breaker 단위 테스트 (순수 WireMock + Resilience4j)
 * Spring Context 없이 Circuit Breaker 동작 검증
 *
 * 핵심 테스트 시나리오:
 * 1. Circuit CLOSED: 정상 응답 시 Circuit 닫힌 상태 유지
 * 2. Circuit OPEN: 지속적인 에러 시 Circuit 열림
 * 3. Circuit HALF_OPEN: Wait Duration 후 Half-Open 전환
 * 4. Fallback: Circuit OPEN 시 즉시 실패
 * 5. Slow Call: Read Timeout 발생하여 실패로 간주
 */
@WireMockTest
@DisplayName("Circuit Breaker 단위 테스트 (순수 WireMock)")
class CircuitBreakerUnitTest {

    private RestClient restClient;
    private CircuitBreaker circuitBreaker;
    private String baseUrl;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        baseUrl = wmRuntimeInfo.getHttpBaseUrl();

        // RestClient 생성 (WireMock 서버로 연결)
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1) // WireMock은 HTTP/1.1
                .connectTimeout(Duration.ofMillis(200))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(1000)); // 1초 Read Timeout

        restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        // Circuit Breaker 설정
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.TIME_BASED)
                .slidingWindowSize(5) // 5초 윈도우
                .minimumNumberOfCalls(5) // 테스트용으로 5로 줄임 (원래 100)
                .failureRateThreshold(50) // 50% 실패율
                .slowCallDurationThreshold(Duration.ofMillis(1000)) // 1초 이상 Slow Call
                .slowCallRateThreshold(50) // 50% Slow Call
                .waitDurationInOpenState(Duration.ofSeconds(2)) // 테스트용 2초 (원래 5초)
                .permittedNumberOfCallsInHalfOpenState(3) // 테스트용 3 (원래 10)
                .ignoreExceptions(BusinessException.class) // 비즈니스 예외는 Circuit 실패로 카운트하지 않음
                .build();

        circuitBreaker = CircuitBreakerRegistry.of(cbConfig).circuitBreaker("queueService");

        // 각 테스트 전 상태 초기화
        circuitBreaker.reset();
    }

    /**
     * Queue 토큰 검증 호출 (Circuit Breaker로 감싸진)
     * 프로덕션 QueueServiceRestClientAdapter와 동일한 동작
     * - 4xx: BusinessException (ignoreExceptions)
     * - 5xx: 기본 예외 전파 (Circuit 실패로 카운트)
     */
    private void validateToken(Long userId, String queueToken) {
        Supplier<Void> decoratedSupplier = CircuitBreaker.decorateSupplier(circuitBreaker, () -> {
            restClient.get()
                    .uri("/api/v1/queue/validate")
                    .header("X-Queue-Token", queueToken)
                    .header("X-User-Id", String.valueOf(userId))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError(), (request, response) -> {
                        throw new BusinessException(ErrorCode.QUEUE_TOKEN_INVALID, "유효하지 않은 대기열 토큰입니다.");
                    })
                    // 5xx 핸들러 제거 - 프로덕션과 동일하게 기본 예외 전파
                    .toBodilessEntity();
            return null;
        });

        decoratedSupplier.get();
    }

    @Test
    @DisplayName("[CLOSED] 정상 응답 시 Circuit은 닫힌 상태를 유지한다")
    void circuitRemainsClosed_whenRequestSucceeds() {
        // Given: Queue Service가 200 OK 반환
        stubFor(get(urlEqualTo("/api/v1/queue/validate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")));

        // When: 토큰 검증 요청
        assertThatCode(() -> validateToken(1L, "valid-token"))
                .doesNotThrowAnyException();

        // Then: Circuit은 CLOSED 상태
        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls())
                .isGreaterThanOrEqualTo(1);

        // Then: WireMock 호출 확인
        verify(getRequestedFor(urlEqualTo("/api/v1/queue/validate"))
                .withHeader("X-Queue-Token", equalTo("valid-token"))
                .withHeader("X-User-Id", equalTo("1")));
    }

    @Test
    @DisplayName("[OPEN] 지속적인 Timeout 시 Circuit이 열린다")
    void circuitOpens_whenContinuousTimeouts() {
        // Given: Queue Service가 계속 Timeout 발생 (Read Timeout: 1000ms)
        stubFor(get(urlEqualTo("/api/v1/queue/validate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(1500))); // 1.5초 지연 -> Read Timeout 발생

        // When: minimumNumberOfCalls(5) 이상 실패 (병렬 호출로 5초 윈도우 내 포함)
        // Timeout은 BusinessException이 아니므로 Circuit 실패로 카운트됨
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 6; i++) {
                executor.submit(() -> {
                    try {
                        validateToken(1L, "test-token");
                    } catch (Exception e) {
                        // 예외 예상 (Timeout)
                    }
                });
            }
        } // ExecutorService 종료 시 모든 태스크 완료 대기

        // Then: Circuit이 OPEN 상태로 전환
        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        // Then: 실패율 확인
        assertThat(circuitBreaker.getMetrics().getFailureRate())
                .isGreaterThan(50f);
    }

    @Test
    @DisplayName("[OPEN] Circuit이 열리면 즉시 CallNotPermittedException이 발생한다")
    void callNotPermitted_whenCircuitIsOpen() {
        // Given: Circuit을 수동으로 OPEN 상태로 전환
        circuitBreaker.transitionToOpenState();

        // When: 토큰 검증 시도
        // Then: CallNotPermittedException 발생 (요청이 아예 나가지 않음)
        assertThatThrownBy(() -> validateToken(1L, "test-token"))
                .isInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);

        // Then: WireMock에 요청이 가지 않음 (Circuit이 열려서 차단됨)
        verify(0, getRequestedFor(urlEqualTo("/api/v1/queue/validate")));
    }

    @Test
    @DisplayName("[HALF_OPEN] 대기 시간 후 요청 시 Circuit은 HALF_OPEN으로 전환된다")
    void circuitTransitionsToHalfOpen_afterWaitDuration() {
        // Given: Circuit을 OPEN 상태로 만듦
        circuitBreaker.transitionToOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Given: Queue Service가 정상 응답 (Half-Open 전환 후 테스트 요청용)
        stubFor(get(urlEqualTo("/api/v1/queue/validate"))
                .willReturn(aResponse().withStatus(200)));

        // When: waitDurationInOpenState(2초) 대기 후 요청 시도
        // Resilience4j는 요청이 들어올 때 시간을 체크하여 HALF_OPEN으로 전환함
        await().atMost(Duration.ofSeconds(3))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    try {
                        validateToken(1L, "test-token");
                    } catch (Exception ignored) {
                    }
                    // Then: Circuit이 HALF_OPEN 또는 CLOSED 상태로 전환
                    assertThat(circuitBreaker.getState())
                            .isIn(CircuitBreaker.State.HALF_OPEN, CircuitBreaker.State.CLOSED);
                });
    }

    @Test
    @DisplayName("[HALF_OPEN → CLOSED] HALF_OPEN 상태에서 성공하면 Circuit이 다시 닫힌다")
    void circuitCloses_whenSuccessfulInHalfOpenState() {
        // Given: Queue Service가 정상 응답
        stubFor(get(urlEqualTo("/api/v1/queue/validate"))
                .willReturn(aResponse().withStatus(200)));

        // Given: Circuit을 OPEN 상태로 만든 후 HALF_OPEN으로 전환
        // (CLOSED -> HALF_OPEN 직접 전환은 불가능, OPEN을 거쳐야 함)
        circuitBreaker.transitionToOpenState();
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // When: permittedNumberOfCallsInHalfOpenState(3번) 성공
        for (int i = 0; i < 3; i++) {
            validateToken(1L, "valid-token");
        }

        // Then: Circuit이 CLOSED 상태로 전환
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    assertThat(circuitBreaker.getState())
                            .isEqualTo(CircuitBreaker.State.CLOSED);
                });
    }

    @Test
    @DisplayName("[Slow Call] Read Timeout 초과 시 Circuit 실패로 카운트된다")
    void slowCallFails_whenReadTimeoutExceeds() {
        // Given: Queue Service가 1.5초 지연 후 응답 (Read Timeout: 1000ms)
        stubFor(get(urlEqualTo("/api/v1/queue/validate"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withFixedDelay(1500))); // 1.5초 지연 → Read Timeout 발생

        // When: Read Timeout 발생 (1초 초과)
        assertThatThrownBy(() -> validateToken(1L, "slow-token"))
                .isInstanceOf(Exception.class); // Read Timeout 예외

        // Then: Circuit Breaker가 실패로 카운트 (slowCallDurationThreshold 이전에 Timeout 발생)
        await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> {
                    assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls())
                            .isGreaterThanOrEqualTo(1);
                });
    }

    @Test
    @DisplayName("[4xx Error] 401 에러도 정상적으로 처리된다")
    void handles401Error_asBusinessException() {
        // Given: Queue Service가 401 반환 (잘못된 토큰)
        stubFor(get(urlEqualTo("/api/v1/queue/validate"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("Unauthorized")));

        // When: 토큰 검증 시도
        // Then: BusinessException 발생
        assertThatThrownBy(() -> validateToken(1L, "invalid-token"))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException be = (BusinessException) exception;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.QUEUE_TOKEN_INVALID);
                    assertThat(be.getMessage()).contains("유효하지 않은 대기열 토큰입니다");
                });

        // Then: BusinessException은 ignoreExceptions 설정으로 Circuit Breaker 실패로 카운트되지 않음
        // 4xx는 비즈니스 로직 에러이므로 Circuit을 열지 않음 (시스템 장애가 아님)
    }

    @Test
    @DisplayName("[4xx Error] 4xx 에러는 Circuit 실패율에 카운트되지 않는다")
    void fourxxErrors_shouldNotCountAsCircuitFailures() {
        // Given: Queue Service가 계속 401 반환
        stubFor(get(urlEqualTo("/api/v1/queue/validate"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withBody("Unauthorized")));

        // When: minimumNumberOfCalls(5) 이상 4xx 에러 발생
        for (int i = 0; i < 10; i++) {
            try {
                validateToken(1L, "invalid-token");
            } catch (BusinessException e) {
                // BusinessException 예상됨 (ignoreExceptions)
            }
        }

        // Then: Circuit은 CLOSED 상태 유지 (4xx는 실패로 카운트되지 않음)
        assertThat(circuitBreaker.getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);

        // Then: 실패 카운트가 0 (ignoreExceptions로 인해 무시됨)
        assertThat(circuitBreaker.getMetrics().getNumberOfFailedCalls())
                .isEqualTo(0);

        // Then: 성공 카운트도 0 (ignoreExceptions는 성공도 실패도 아님)
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls())
                .isEqualTo(0);
    }

    @Test
    @DisplayName("[State Transition] Circuit 상태 전환이 정상적으로 동작한다")
    void circuitStateTransitions_workCorrectly() {
        // Given: CLOSED 상태
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // When: OPEN으로 전환
        circuitBreaker.transitionToOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // When: HALF_OPEN으로 전환
        circuitBreaker.transitionToHalfOpenState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.HALF_OPEN);

        // When: CLOSED로 전환
        circuitBreaker.transitionToClosedState();
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    @DisplayName("[Metrics] Circuit Breaker 메트릭이 정상적으로 수집된다")
    void circuitBreakerMetrics_areCollectedCorrectly() {
        // Given: Queue Service 정상 응답
        stubFor(get(urlEqualTo("/api/v1/queue/validate"))
                .willReturn(aResponse().withStatus(200)));

        // When: 여러 번 호출
        for (int i = 0; i < 5; i++) {
            validateToken(1L, "test-token");
        }

        // Then: 성공 호출 수 집계
        assertThat(circuitBreaker.getMetrics().getNumberOfSuccessfulCalls())
                .isGreaterThanOrEqualTo(5);
    }
}
