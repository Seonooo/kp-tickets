package personal.ai.core.acceptance.support;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import personal.ai.core.booking.adapter.in.web.dto.ReservationResponse;
import personal.ai.core.booking.adapter.in.web.dto.ReserveSeatRequest;
import personal.ai.core.booking.adapter.in.web.dto.SeatResponse;
import personal.ai.core.booking.adapter.out.persistence.JpaReservationRepository;
import personal.ai.core.booking.adapter.out.persistence.JpaSeatRepository;
import personal.ai.core.booking.adapter.out.persistence.ReservationEntity;
import personal.ai.core.booking.adapter.out.persistence.SeatEntity;
import personal.ai.core.booking.domain.model.*;
import personal.ai.core.payment.adapter.in.web.dto.PaymentResponse;
import personal.ai.core.payment.adapter.in.web.dto.ProcessPaymentRequest;
import personal.ai.core.payment.adapter.out.persistence.JpaPaymentOutboxRepository;
import personal.ai.core.payment.adapter.out.persistence.JpaPaymentRepository;
import personal.ai.core.payment.adapter.out.persistence.PaymentOutboxEventEntity;
import personal.ai.core.user.adapter.out.persistence.JpaUserRepository;
import personal.ai.core.user.adapter.out.persistence.UserEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Booking & Payment API 테스트 어댑터
 *
 * Cucumber Step Definition에서 사용하는 헬퍼 클래스로,
 * API 호출, 테스트 데이터 생성, 검증 등의 기능을 제공합니다.
 *
 * Queue Service의 QueueTestAdapter를 참고하여 작성되었습니다.
 *
 * @author AI Core Team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BookingTestAdapter {

    // ==========================================
    // 의존성 주입
    // ==========================================

    private static final String BASE_URI = "http://localhost";
    private static final int DEFAULT_PORT = 8080;
    private final JpaSeatRepository seatRepository;
    private final JpaReservationRepository reservationRepository;
    private final JpaUserRepository userRepository;
    private final JpaPaymentRepository paymentRepository;

    // ==========================================
    // 상수
    // ==========================================
    private final JpaPaymentOutboxRepository outboxRepository;
    private final Environment environment;

    // ==========================================
    // Private 헬퍼 메서드
    // ==========================================

    /**
     * 서버 포트 조회
     */
    private int getPort() {
        return environment.getProperty("local.server.port", Integer.class, DEFAULT_PORT);
    }

    // ==========================================
    // 데이터 초기화
    // ==========================================

    /**
     * 모든 테스트 데이터 초기화
     * 각 시나리오 시작 전에 호출되어 깨끗한 상태를 보장합니다.
     */
    public void clearAllData() {
        log.info(">>> Adapter: 모든 테스트 데이터 초기화");
        outboxRepository.deleteAll();
        paymentRepository.deleteAll();
        reservationRepository.deleteAll();
        seatRepository.deleteAll();
        userRepository.deleteAll();
        log.info(">>> Adapter: 데이터 초기화 완료");
    }

    // ==========================================
    // 테스트 데이터 생성
    // ==========================================

    /**
     * 스케줄 생성 (존재하지 않는 경우)
     * Schedule은 별도 엔티티가 없으므로 ID만 사용
     *
     * @param scheduleId 스케줄 ID
     */
    public void createScheduleIfNotExists(Long scheduleId) {
        log.info(">>> Adapter: 스케줄 ID {} 사용 (별도 엔티티 없음)", scheduleId);
        // Schedule은 별도 테이블이 없고, Seat에 scheduleId로만 관리됨
    }

    /**
     * 여러 좌석 생성
     *
     * @param scheduleId 스케줄 ID
     * @param status     좌석 상태
     * @param count      생성할 좌석 개수
     */
    public void createSeats(Long scheduleId, SeatStatus status, int count) {
        log.info(">>> Adapter: {} 상태 좌석 {}개 생성 시작 - scheduleId={}", status, count, scheduleId);
        for (int i = 0; i < count; i++) {
            String seatNumber = "A" + (i + 1);
            createSeat(scheduleId, seatNumber, status);
        }
        log.info(">>> Adapter: 좌석 생성 완료 - count={}", count);
    }

    /**
     * 단일 좌석 생성
     *
     * @param scheduleId 스케줄 ID
     * @param seatNumber 좌석 번호
     * @param status     좌석 상태
     * @return 생성된 좌석 ID
     */
    public Long createSeat(Long scheduleId, String seatNumber, SeatStatus status) {
        SeatEntity seat = SeatEntity.fromDomain(
                Seat.create(
                        scheduleId,
                        seatNumber,
                        SeatGrade.A,
                        BigDecimal.valueOf(50000),
                        status));
        SeatEntity saved = seatRepository.save(seat);
        log.info(">>> Adapter: 좌석 생성 - id={}, number={}, status={}", saved.getId(), seatNumber, status);
        return saved.getId();
    }

    /**
     * 사용자 생성
     *
     * @param username 사용자 이름
     * @return 생성된 사용자 ID
     */
    public Long createUser(String username) {
        UserEntity user = UserEntity.of(username, username + "@test.com");
        UserEntity saved = userRepository.save(user);
        log.info(">>> Adapter: 사용자 생성 - id={}, name={}", saved.getId(), username);
        return saved.getId();
    }

    /**
     * 대기열 활성 토큰 발급 (모의)
     * 실제로는 Queue Service에서 발급받아야 하지만, 테스트에서는 고정 토큰 사용
     *
     * @param userId 사용자 ID
     * @return 발급된 토큰
     */
    public String issueActiveQueueToken(Long userId) {
        String token = "TEST-ACTIVE-TOKEN-" + userId;
        log.info(">>> Adapter: 대기열 토큰 발급 - userId={}, token={}", userId, token);
        return token;
    }

    /**
     * 예약 생성
     *
     * @param reservationId 예약 ID (null이면 자동 생성)
     * @param userId        사용자 ID
     * @param seatId        좌석 ID
     * @param status        예약 상태
     * @return 생성된 예약 ID
     */
    public Long createReservation(Long reservationId, Long userId, Long seatId, ReservationStatus status) {
        SeatEntity seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));

        // Reservation 생성 (도메인 모델 사용)
        ReservationEntity reservation = ReservationEntity.fromDomain(
                Reservation.create(
                        userId,
                        seatId,
                        seat.getScheduleId(),
                        5 // TTL: 5 minutes
                ));

        // 상태 설정 (필요 시)
        if (status != ReservationStatus.PENDING) {
            reservation.updateStatus(status);
        }

        // 좌석 상태를 RESERVED로 변경
        seat.updateStatus(SeatStatus.RESERVED);
        seatRepository.save(seat);

        ReservationEntity saved = reservationRepository.save(reservation);
        log.info(">>> Adapter: 예약 생성 - id={}, userId={}, seatId={}, status={}",
                saved.getId(), userId, seatId, status);
        return saved.getId();
    }

    // ==========================================
    // API 호출 메서드
    // ==========================================

    /**
     * 좌석 조회 API 호출
     * GET /api/v1/schedules/{scheduleId}/seats
     *
     * @param scheduleId 스케줄 ID
     * @param userId     사용자 ID
     * @param queueToken 대기열 토큰
     * @return 좌석 목록
     */
    public List<SeatResponse> getAvailableSeats(Long scheduleId, Long userId, String queueToken) {
        log.info(">>> Adapter: GET /api/v1/schedules/{}/seats 호출", scheduleId);

        List<SeatResponse> response = RestAssured.given()
                .baseUri(BASE_URI)
                .port(getPort())
                .header("X-User-Id", userId)
                .header("X-Queue-Token", queueToken)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/schedules/{scheduleId}/seats", scheduleId)
                .then()
                .statusCode(200)
                .extract()
                .body()
                .jsonPath()
                .getList(".", SeatResponse.class);

        log.info(">>> Adapter: 좌석 조회 성공 - count={}", response.size());
        return response;
    }

    /**
     * 좌석 예약 API 호출
     * POST /api/v1/reservations
     *
     * @param scheduleId 스케줄 ID
     * @param seatId     좌석 ID
     * @param userId     사용자 ID
     * @param queueToken 대기열 토큰
     * @return 예약 응답
     */
    public ReservationResponse reserveSeat(Long scheduleId, Long seatId, Long userId, String queueToken) {
        log.info(">>> Adapter: POST /api/v1/reservations 호출 - scheduleId={}, seatId={}", scheduleId, seatId);

        ReserveSeatRequest request = new ReserveSeatRequest(scheduleId, seatId);

        Map<String, Object> response = RestAssured.given()
                .baseUri(BASE_URI)
                .port(getPort())
                .header("X-User-Id", userId)
                .header("X-Queue-Token", queueToken)
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/reservations")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .as(Map.class);

        Map<String, Object> data = (Map<String, Object>) response.get("data");

        ReservationResponse reservationResponse = new ReservationResponse(
                ((Number) data.get("reservationId")).longValue(),
                ((Number) data.get("userId")).longValue(),
                ((Number) data.get("seatId")).longValue(),
                ((Number) data.get("scheduleId")).longValue(),
                ReservationStatus.valueOf((String) data.get("status")),
                data.get("expiresAt") != null ? java.time.LocalDateTime.parse((String) data.get("expiresAt")) : null,
                data.get("createdAt") != null ? java.time.LocalDateTime.parse((String) data.get("createdAt")) : null);

        log.info(">>> Adapter: 예약 성공 - reservationId={}", reservationResponse.reservationId());
        return reservationResponse;
    }

    /**
     * 예약 조회 API 호출
     * GET /api/v1/reservations/{reservationId}
     *
     * @param reservationId 예약 ID
     * @param userId        사용자 ID
     * @return 예약 응답
     */
    public ReservationResponse getReservation(Long reservationId, Long userId) {
        log.info(">>> Adapter: GET /api/v1/reservations/{} 호출", reservationId);

        ReservationResponse response = RestAssured.given()
                .baseUri(BASE_URI)
                .port(getPort())
                .header("X-User-Id", userId)
                .contentType(ContentType.JSON)
                .when()
                .get("/api/v1/reservations/{reservationId}", reservationId)
                .then()
                .statusCode(200)
                .extract()
                .body()
                .as(ReservationResponse.class);

        log.info(">>> Adapter: 예약 조회 성공 - reservationId={}", reservationId);
        return response;
    }

    /**
     * 결제 처리 API 호출
     * POST /api/v1/payments
     *
     * @param reservationId 예약 ID
     * @param userId        사용자 ID
     * @return 결제 응답
     */
    public PaymentResponse processPayment(Long reservationId, Long userId) {
        log.info(">>> Adapter: POST /api/v1/payments 호출 - reservationId={}", reservationId);

        // 예약 정보 조회
        ReservationEntity reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));

        SeatEntity seat = seatRepository.findById(reservation.getSeatId())
                .orElseThrow(() -> new IllegalArgumentException("Seat not found"));

        ProcessPaymentRequest request = new ProcessPaymentRequest(
                reservationId,
                userId,
                seat.getPrice(),
                "CREDIT_CARD",
                "CONCERT-001");

        Map<String, Object> response = RestAssured.given()
                .baseUri(BASE_URI)
                .port(getPort())
                .contentType(ContentType.JSON)
                .body(request)
                .when()
                .post("/api/v1/payments")
                .then()
                .statusCode(201)
                .extract()
                .body()
                .as(Map.class);

        Map<String, Object> data = (Map<String, Object>) response.get("data");

        PaymentResponse paymentResponse = new PaymentResponse(
                ((Number) data.get("paymentId")).longValue(),
                ((Number) data.get("reservationId")).longValue(),
                ((Number) data.get("userId")).longValue(),
                new BigDecimal(data.get("amount").toString()),
                personal.ai.core.payment.domain.model.PaymentStatus.valueOf((String) data.get("status")),
                (String) data.get("paymentMethod"),
                data.get("paidAt") != null ? java.time.LocalDateTime.parse((String) data.get("paidAt")) : null,
                data.get("createdAt") != null ? java.time.LocalDateTime.parse((String) data.get("createdAt")) : null);

        log.info(">>> Adapter: 결제 성공 - paymentId={}", paymentResponse.paymentId());
        return paymentResponse;
    }

    // ==========================================
    // 검증 헬퍼 메서드
    // ==========================================

    /**
     * 좌석 상태 조회
     *
     * @param seatId 좌석 ID
     * @return 좌석 상태
     */
    public SeatStatus getSeatStatus(Long seatId) {
        SeatEntity seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new IllegalArgumentException("Seat not found: " + seatId));
        log.info(">>> Adapter: 좌석 상태 조회 - seatId={}, status={}", seatId, seat.getStatus());
        return seat.getStatus();
    }

    /**
     * 예약 상태 조회
     *
     * @param reservationId 예약 ID
     * @return 예약 상태
     */
    public ReservationStatus getReservationStatus(Long reservationId) {
        ReservationEntity reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new IllegalArgumentException("Reservation not found: " + reservationId));
        log.info(">>> Adapter: 예약 상태 조회 - reservationId={}, status={}", reservationId, reservation.getStatus());
        return reservation.getStatus();
    }

    /**
     * Outbox 이벤트 존재 확인
     *
     * @param paymentId 결제 ID
     * @param status    Outbox 이벤트 상태
     * @return 이벤트 존재 여부
     */
    public boolean verifyOutboxEventExists(Long paymentId, String status) {
        log.info(">>> Adapter: Outbox 이벤트 검증 - paymentId={}, status={}", paymentId, status);
        List<PaymentOutboxEventEntity> events = outboxRepository.findAll();
        boolean exists = events.stream()
                .anyMatch(e -> e.getAggregateId().equals(paymentId)
                        && e.getStatus().name().equals(status));
        log.info(">>> Adapter: Outbox 이벤트 존재 여부 - {}", exists);
        return exists;
    }

    /**
     * Outbox 이벤트 페이로드 검증
     *
     * @param paymentId 결제 ID
     * @return 페이로드 존재 여부
     */
    public boolean verifyOutboxEventPayload(Long paymentId) {
        log.info(">>> Adapter: Outbox 이벤트 페이로드 검증 - paymentId={}", paymentId);
        List<PaymentOutboxEventEntity> events = outboxRepository.findAll();
        boolean hasPayload = events.stream()
                .anyMatch(e -> e.getAggregateId().equals(paymentId)
                        && e.getPayload() != null
                        && !e.getPayload().isEmpty());
        log.info(">>> Adapter: Outbox 이벤트 페이로드 존재 여부 - {}", hasPayload);
        return hasPayload;
    }
}
