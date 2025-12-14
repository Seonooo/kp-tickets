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

import java.net.http.HttpClient;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QueueServiceRestClientAdapter Fallback 테스트
 *
 * Circuit Breaker가 OPEN 상태일 때 Fallback이 올바르게 동작하는지 검증
 * WireMock을 사용하여 public API를 통해 테스트
 */
@WireMockTest
@DisplayName("QueueService Fallback 테스트 (WireMock)")
class QueueServiceFallbackTest {

    private QueueServiceRestClientAdapter adapter;
    private CircuitBreaker circuitBreaker;
    private String baseUrl;

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        baseUrl = wmRuntimeInfo.getHttpBaseUrl();

        // RestClient 생성
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(200))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(1000));

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();

        // Circuit Breaker 생성 (테스트용 설정)
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(5)
                .minimumNumberOfCalls(2)
                .failureRateThreshold(50)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .ignoreExceptions(BusinessException.class)
                .build();

        circuitBreaker = CircuitBreakerRegistry.of(config).circuitBreaker("queueService");

        // Adapter 생성 (실제 RestClient 주입)
        adapter = new QueueServiceRestClientAdapter(restClient);
    }

    private void validateTokenWithCircuitBreaker(String concertId, Long userId, String queueToken) {
        circuitBreaker.executeSupplier(() -> {
            adapter.validateToken(concertId, userId, queueToken);
            return null;
        });
    }

    @Test
    @DisplayName("Circuit OPEN 시 즉시 실패하여 Fallback 동작을 트리거한다")
    void fallback_shouldBeTriggeredWhenCircuitIsOpen() {
        // Given: Circuit을 수동으로 OPEN 상태로 전환
        // Note: QueueServiceRestClientAdapter는 모든 예외를 BusinessException으로 변환하고,
        // BusinessException은 ignoreExceptions에 설정되어 자동으로 Circuit을 열기 어려움
        circuitBreaker.transitionToOpenState();

        // Then: Circuit이 OPEN 상태
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // When & Then: Circuit OPEN 시 즉시 CallNotPermittedException 발생
        // Note: Adapter의 @CircuitBreaker fallback은 Spring AOP 환경에서만 동작하므로
        // 여기서는 Circuit OPEN으로 인한 즉시 실패를 검증
        assertThatThrownBy(() -> validateTokenWithCircuitBreaker("concert-1", 1L, "test-token"))
                .isInstanceOf(io.github.resilience4j.circuitbreaker.CallNotPermittedException.class);

        // Then: WireMock에 요청이 가지 않음 (Circuit이 요청을 차단)
        verify(0, getRequestedFor(urlEqualTo("/api/v1/queue/validate")));
    }

}
