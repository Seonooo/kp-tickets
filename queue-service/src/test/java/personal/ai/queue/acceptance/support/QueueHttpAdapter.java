package personal.ai.queue.acceptance.support;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Queue Service HTTP Adapter
 * 인수 테스트용 순수 HTTP 클라이언트
 * Environment를 통해 런타임에 포트를 가져옴 (lazy initialization)
 */
@Slf4j
@Component
public class QueueHttpAdapter {

    private static final String BASE_URI = "http://localhost";
    private static final String BASE_PATH = "/api/v1/queue";
    private final Environment environment;

    public QueueHttpAdapter(Environment environment) {
        this.environment = environment;
    }

    /**
     * 서버 포트를 가져옴 (lazy)
     */
    private int getPort() {
        return environment.getProperty("local.server.port", Integer.class, 8081);
    }

    /**
     * 공통 RequestSpecification 생성
     */
    private RequestSpecification givenRequest() {
        return RestAssured.given()
                .baseUri(BASE_URI)
                .port(getPort())
                .basePath(BASE_PATH)
                .contentType(ContentType.JSON);
    }

    /**
     * 대기열 진입 API
     * POST /api/v1/queue/enter
     */
    public Response enterQueue(String concertId, String userId) {
        log.debug(">>> HTTP: POST /enter - concertId={}, userId={}", concertId, userId);

        return givenRequest()
                .body(Map.of("concertId", concertId, "userId", userId))
                .when()
                .post("/enter");
    }

    /**
     * 상태 조회 API
     * GET /api/v1/queue/status
     */
    public Response getQueueStatus(String concertId, String userId) {
        log.debug(">>> HTTP: GET /status - concertId={}, userId={}", concertId, userId);

        return givenRequest()
                .queryParam("concertId", concertId)
                .queryParam("userId", userId)
                .when()
                .get("/status");
    }

    /**
     * 토큰 활성화 API
     * POST /api/v1/queue/activate
     */
    public Response activateToken(String concertId, String userId) {
        log.debug(">>> HTTP: POST /activate - concertId={}, userId={}", concertId, userId);

        return givenRequest()
                .queryParam("concertId", concertId)
                .queryParam("userId", userId)
                .when()
                .post("/activate");
    }

    /**
     * 토큰 연장 API
     * POST /api/v1/queue/extend
     */
    public Response extendToken(String concertId, String userId) {
        log.debug(">>> HTTP: POST /extend - concertId={}, userId={}", concertId, userId);

        return givenRequest()
                .body(Map.of("concertId", concertId, "userId", userId))
                .when()
                .post("/extend");
    }

    /**
     * 토큰 검증 API
     * POST /api/v1/queue/validate
     */
    public Response validateToken(String concertId, String userId, String token) {
        log.debug(">>> HTTP: POST /validate - concertId={}, userId={}", concertId, userId);

        return givenRequest()
                .body(Map.of("concertId", concertId, "userId", userId, "token", token))
                .when()
                .post("/validate");
    }
}
