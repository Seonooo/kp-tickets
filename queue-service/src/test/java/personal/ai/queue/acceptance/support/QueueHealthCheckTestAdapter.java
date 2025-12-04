package personal.ai.queue.acceptance.support;

import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.stereotype.Component;
import personal.ai.common.test.HealthCheckTestAdapter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Queue Service Health Check Test Adapter
 * Redis, Kafka 상태만 검증 (Database 없음)
 */
@Component
public class QueueHealthCheckTestAdapter implements HealthCheckTestAdapter {

    private int port;

    public void setPort(int port) {
        this.port = port;
    }

    @Override
    public Response callHealthCheckApi() {
        RestAssured.port = port;
        return RestAssured
                .given()
                .contentType("application/json")
                .when()
                .get("/api/v1/health");
    }

    @Override
    public void verifyStatusCode(Response response, int expectedStatusCode) {
        assertThat(response.statusCode()).isEqualTo(expectedStatusCode);
    }

    @Override
    public void verifyResult(Response response, String expectedResult) {
        assertThat(response.jsonPath().getString("result")).isEqualTo(expectedResult);
    }

    @Override
    public void verifyDatabaseStatus(Response response) {
        // Queue Service는 Database를 사용하지 않으므로 구현하지 않음
    }

    @Override
    public void verifyRedisStatus(Response response) {
        assertThat(response.jsonPath().getString("data.redis"))
                .as("Redis status should not be null")
                .isNotNull();
    }

    @Override
    public void verifyKafkaStatus(Response response) {
        assertThat(response.jsonPath().getString("data.kafka"))
                .as("Kafka status should not be null")
                .isNotNull();
    }
}
