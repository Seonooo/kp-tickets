package personal.ai.acceptance.steps;

import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.restassured.response.Response;
import lombok.RequiredArgsConstructor;
import personal.ai.acceptance.support.TestAdapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * HealthCheck Feature에 대한 Step Definitions
 * BDD 시나리오와 실제 테스트 코드를 연결합니다.
 */
@RequiredArgsConstructor
public class HealthCheckSteps {

    private final TestAdapter testAdapter;
    private Response response;

    @Given("애플리케이션이 실행 중입니다")
    public void 애플리케이션이_실행_중입니다() {
        // Spring Boot가 실행되었는지 확인
        Response healthResponse = testAdapter.get("/actuator/health");
        assertThat(healthResponse.statusCode()).isEqualTo(200);
    }

    @When("{string} 엔드포인트에 GET 요청을 보낸다")
    public void 엔드포인트에_GET_요청을_보낸다(String endpoint) {
        response = testAdapter.get(endpoint);
    }

    @Then("응답 상태 코드는 {int}이다")
    public void 응답_상태_코드는_이다(int statusCode) {
        assertThat(response.statusCode()).isEqualTo(statusCode);
    }

    @And("응답 본문의 {string} 필드는 {string}이다")
    public void 응답_본문의_필드는_이다(String jsonPath, String expectedValue) {
        response.then()
                .body(jsonPath, equalTo(expectedValue));
    }
}
