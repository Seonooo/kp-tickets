package personal.ai.e2e.client;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import static io.restassured.RestAssured.given;

/**
 * Core Service API Client
 * 예매/결제 서비스와의 통신을 담당
 */
@Slf4j
public class CoreServiceClient {

    private final String baseUrl;

    public CoreServiceClient(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /**
     * 좌석 목록 조회
     */
    public Response getSeats(String scheduleId, String userId, String queueToken) {
        log.info("Getting seats: scheduleId={}, userId={}", scheduleId, userId);

        return given()
                .header("X-User-Id", userId)
                .header("X-Queue-Token", queueToken)
                .when()
                .get(baseUrl + "/api/v1/schedules/" + scheduleId + "/seats")
                .then()
                .extract().response();
    }

    /**
     * 좌석 예약
     */
    public Response reserveSeat(String scheduleId, Long seatId, String userId, String queueToken) {
        log.info("Reserving seat: scheduleId={}, seatId={}, userId={}", scheduleId, seatId, userId);

        return given()
                .contentType("application/json")
                .header("X-User-Id", userId)
                .header("X-Queue-Token", queueToken)
                .body(String.format("""
                        {
                            "scheduleId": %s,
                            "seatId": %d
                        }
                        """, scheduleId, seatId))
                .when()
                .post(baseUrl + "/api/v1/reservations")
                .then()
                .extract().response();
    }

    /**
     * 결제 처리
     */
    public Response processPayment(Long reservationId, String userId, int amount, String concertId) {
        log.info("Processing payment: reservationId={}, userId={}, amount={}",
                reservationId, userId, amount);

        return given()
                .contentType("application/json")
                .body(String.format("""
                        {
                            "reservationId": %d,
                            "userId": "%s",
                            "amount": %d,
                            "paymentMethod": "CREDIT_CARD",
                            "concertId": "%s"
                        }
                        """, reservationId, userId, amount, concertId))
                .when()
                .post(baseUrl + "/api/v1/payments")
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
