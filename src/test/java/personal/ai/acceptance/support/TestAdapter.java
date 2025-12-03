package personal.ai.acceptance.support;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import static io.restassured.RestAssured.given;

/**
 * TestAdapter - API 호출 및 내부 상태 검증을 위한 추상화 계층
 * 테스트 코드가 비즈니스 로직에 강결합되지 않도록 인터페이스 역할을 수행합니다.
 */
@Component
@RequiredArgsConstructor
public class TestAdapter {

    private final Environment environment;

    private Response lastResponse;

    /**
     * GET 요청을 보냅니다
     */
    public Response get(String endpoint) {
        lastResponse = givenSpec()
                .when()
                .get(endpoint);
        return lastResponse;
    }

    /**
     * POST 요청을 보냅니다
     */
    public Response post(String endpoint, Object body) {
        lastResponse = givenSpec()
                .contentType("application/json")
                .body(body)
                .when()
                .post(endpoint);
        return lastResponse;
    }

    /**
     * PUT 요청을 보냅니다
     */
    public Response put(String endpoint, Object body) {
        lastResponse = givenSpec()
                .contentType("application/json")
                .body(body)
                .when()
                .put(endpoint);
        return lastResponse;
    }

    /**
     * DELETE 요청을 보냅니다
     */
    public Response delete(String endpoint) {
        lastResponse = givenSpec()
                .when()
                .delete(endpoint);
        return lastResponse;
    }

    /**
     * 마지막 응답을 반환합니다
     */
    public Response getLastResponse() {
        return lastResponse;
    }

    /**
     * 공통 RequestSpecification을 생성합니다
     */
    private RequestSpecification givenSpec() {
        return given()
                .port(getPort())
                .log().all();
    }

    /**
     * 베이스 URL을 반환합니다
     */
    public String getBaseUrl() {
        return "http://localhost:" + getPort();
    }

    /**
     * 서버 포트를 가져옵니다
     */
    private int getPort() {
        String port = environment.getProperty("local.server.port");
        return port != null ? Integer.parseInt(port) : 8080;
    }
}
