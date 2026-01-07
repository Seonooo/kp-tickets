package personal.ai.e2e.client;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import static io.restassured.RestAssured.given;

/**
 * Queue Service API Client
 * 대기열 서비스와의 통신을 담당
 */
@Slf4j
public class QueueServiceClient {

    private final String baseUrl;

    public QueueServiceClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 대기열 진입
     */
    public Response enterQueue(String concertId, String userId) {
        log.info("Entering queue: concertId={}, userId={}", concertId, userId);

        return given()
                .contentType("application/json")
                .body(String.format("""
                        {
                            "concertId": "%s",
                            "userId": "%s"
                        }
                        """, concertId, userId))
                .when()
                .post(baseUrl + "/api/v1/queue/enter")
                .then()
                .extract().response();
    }

    /**
     * 대기열 상태 조회
     */
    public Response getQueueStatus(String concertId, String userId) {
        return given()
                .queryParam("concertId", concertId)
                .queryParam("userId", userId)
                .when()
                .get(baseUrl + "/api/v1/queue/status")
                .then()
                .extract().response();
    }

    /**
     * 헬스 체크
     */
    public Response healthCheck() {
        return given()
                .when()
                .get(baseUrl + "/actuator/health")
                .then()
                .extract().response();
    }
}
