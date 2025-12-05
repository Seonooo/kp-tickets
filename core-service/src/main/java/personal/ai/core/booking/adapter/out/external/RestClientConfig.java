package personal.ai.core.booking.adapter.out.external;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * RestClient Configuration
 * Spring Boot 3.4 RestClient 설정
 * Virtual Thread는 Spring Boot 3.2+에서 자동으로 적용됨
 */
@Configuration
public class RestClientConfig {

    @Value("${external.queue-service.base-url}")
    private String queueServiceBaseUrl;

    @Value("${external.queue-service.timeout-seconds:5}")
    private int timeoutSeconds;

    @Bean
    public RestClient queueServiceRestClient() {
        // HttpClient 생성 (HTTP/2 지원)
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))  // TCP 연결 타임아웃
                .build();

        // RequestFactory 생성 및 Read Timeout 설정
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(timeoutSeconds));  // HTTP 응답 타임아웃

        return RestClient.builder()
                .baseUrl(queueServiceBaseUrl)
                .requestFactory(requestFactory)
                .build();
    }
}
