package personal.ai.core.acceptance.support;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Booking Service HTTP Adapter
 * 인수 테스트용 순수 HTTP 클라이언트
 * Environment를 통해 런타임에 포트를 가져옴 (lazy initialization)
 * 
 * QueueHttpAdapter 패턴을 따라 구현
 */
@Slf4j
@Component
public class BookingHttpAdapter {

    private static final String BASE_URI = "http://localhost";
    private final Environment environment;

    public BookingHttpAdapter(Environment environment) {
        this.environment = environment;
    }

    /**
     * 서버 포트를 가져옴 (lazy)
     */
    private int getPort() {
        return environment.getProperty("local.server.port", Integer.class, 8080);
    }

    /**
     * 공통 RequestSpecification 생성
     */
    private RequestSpecification givenRequest() {
        return RestAssured.given()
                .baseUri(BASE_URI)
                .port(getPort())
                .contentType(ContentType.JSON);
    }

    /**
     * 공통 RequestSpecification 생성 (헤더 포함)
     */
    private RequestSpecification givenRequestWithHeaders(Long userId, String queueToken) {
        return givenRequest()
                .header("X-User-Id", userId)
                .header("X-Queue-Token", queueToken);
    }

    // ==========================================
    // 좌석 API
    // ==========================================

    /**
     * 예약 가능한 좌석 목록 조회
     * GET /api/v1/schedules/{scheduleId}/seats
     */
    public Response getAvailableSeats(Long scheduleId, Long userId, String queueToken) {
        log.debug(">>> HTTP: GET /schedules/{}/seats - userId={}", scheduleId, userId);

        return givenRequestWithHeaders(userId, queueToken)
                .when()
                .get("/api/v1/schedules/{scheduleId}/seats", scheduleId);
    }

    // ==========================================
    // 예약 API
    // ==========================================

    /**
     * 좌석 예약
     * POST /api/v1/reservations
     */
    public Response reserveSeat(Long scheduleId, Long seatId, Long userId, String queueToken) {
        log.debug(">>> HTTP: POST /reservations - scheduleId={}, seatId={}, userId={}",
                scheduleId, seatId, userId);

        return givenRequestWithHeaders(userId, queueToken)
                .body(Map.of("scheduleId", scheduleId, "seatId", seatId))
                .when()
                .post("/api/v1/reservations");
    }

    /**
     * 예약 조회
     * GET /api/v1/reservations/{reservationId}
     */
    public Response getReservation(Long reservationId, Long userId) {
        log.debug(">>> HTTP: GET /reservations/{} - userId={}", reservationId, userId);

        return givenRequest()
                .header("X-User-Id", userId)
                .when()
                .get("/api/v1/reservations/{reservationId}", reservationId);
    }

    // ==========================================
    // 결제 API
    // ==========================================

    /**
     * 결제 처리
     * POST /api/v1/payments
     */
    public Response processPayment(Long reservationId, Long userId, BigDecimal amount,
            String paymentMethod, String concertId) {
        log.debug(">>> HTTP: POST /payments - reservationId={}, userId={}", reservationId, userId);

        return givenRequest()
                .body(Map.of(
                        "reservationId", reservationId,
                        "userId", userId,
                        "amount", amount,
                        "paymentMethod", paymentMethod,
                        "concertId", concertId))
                .when()
                .post("/api/v1/payments");
    }
}
