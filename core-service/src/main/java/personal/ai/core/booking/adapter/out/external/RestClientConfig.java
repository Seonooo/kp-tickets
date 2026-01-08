package personal.ai.core.booking.adapter.out.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * RestClient Configuration with SimpleClientHttpRequestFactory
 *
 * CRITICAL FIX: Java HttpClient's JdkClientHttpRequestFactory doesn't properly
 * apply read timeout, causing 60-second hangs. Switched to SimpleClientHttpRequestFactory
 * which uses java.net.HttpURLConnection with reliable timeout support.
 *
 * Connection Pool 최적화:
 * - SimpleClientHttpRequestFactory uses HttpURLConnection (JDK 내장)
 * - Connect Timeout: 200ms (빠른 실패)
 * - Read Timeout: 2000ms (Queue Service P99 여유)
 *
 * 성능:
 * - Virtual Thread와 완벽 호환
 * - Proven timeout reliability
 * - 외부 의존성 불필요
 */
@Configuration
public class RestClientConfig {

    @Value("${external.queue-service.base-url}")
    private String queueServiceBaseUrl;

    @Value("${external.queue-service.connect-timeout-ms:200}")
    private int connectTimeoutMs;

    @Value("${external.queue-service.read-timeout-ms:2000}")
    private int readTimeoutMs;

    @Bean
    public RestClient queueServiceRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

        // Timeout 설정 (밀리초 단위)
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(queueServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
