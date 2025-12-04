package personal.ai.queue.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import personal.ai.common.test.HealthCheckTestAdapter;
import personal.ai.queue.acceptance.support.QueueHealthCheckTestAdapter;

/**
 * Health Check Feature Step Definitions
 * Adapter 패턴을 사용하여 모든 로직을 위임
 */
public class HealthCheckSteps {

    @LocalServerPort
    private int port;

    @Autowired
    private QueueHealthCheckTestAdapter adapter;

    private Response response;

    @Given("Queue 서비스가 정상 동작 중이다")
    public void queueServiceIsRunning() {
        adapter.setPort(port);
    }

    @When("헬스 체크 API를 호출하면")
    public void callHealthCheckApi() {
        response = adapter.callHealthCheckApi();
    }

    @Then("응답 상태 코드는 {int}이다")
    public void responseStatusCodeIs(int statusCode) {
        adapter.verifyStatusCode(response, statusCode);
    }

    @And("응답 결과는 {string}이다")
    public void responseResultIs(String result) {
        adapter.verifyResult(response, result);
    }

    @And("Redis 상태 정보가 포함되어 있다")
    public void redisStatusIsIncluded() {
        adapter.verifyRedisStatus(response);
    }

    @And("Kafka 상태 정보가 포함되어 있다")
    public void kafkaStatusIsIncluded() {
        adapter.verifyKafkaStatus(response);
    }
}
