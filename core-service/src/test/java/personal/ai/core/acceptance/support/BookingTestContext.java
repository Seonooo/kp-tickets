package personal.ai.core.acceptance.support;

import io.cucumber.spring.ScenarioScope;
import io.restassured.response.Response;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Booking Acceptance Test Context
 * 시나리오 간 상태 공유를 위한 컨텍스트 클래스
 *
 * @ScenarioScope: Cucumber 시나리오당 하나의 인스턴스 생성
 * 같은 시나리오 내의 모든 Step 클래스가 이 인스턴스를 공유
 */
@Getter
@Setter
@Component
@ScenarioScope
public class BookingTestContext {

    // ==========================================
    // HTTP 응답
    // ==========================================

    /** 기본 콘서트 ID */
    private static final String DEFAULT_CONCERT_ID = "CONCERT-001";

    // ==========================================
    // 기본 테스트 데이터
    // ==========================================
    /** 성공한 예약 수 (동시성 테스트) */
    private final AtomicInteger successfulReservations = new AtomicInteger(0);
    /** 실패한 예약 수 (동시성 테스트) */
    private final AtomicInteger failedReservations = new AtomicInteger(0);
    /** 마지막 HTTP API 응답 (모든 Step에서 공유) */
    private Response lastHttpResponse;
    /** 현재 테스트 중인 스케줄 ID */
    private Long currentScheduleId;
    /** 현재 테스트 중인 좌석 ID */
    private Long currentSeatId;
    /** 현재 테스트 중인 사용자 ID */
    private Long currentUserId;

    // ==========================================
    // 예외 테스트용 데이터
    // ==========================================
    /** 현재 발급된 대기열 토큰 */
    private String currentQueueToken;
    /** 현재 예약 ID */
    private Long currentReservationId;
    /** 다른 사용자 ID (권한 테스트용) */
    private Long otherUserId;

    // ==========================================
    // 동시성 테스트용
    // ==========================================
    /** 다른 사용자의 예약 ID */
    private Long otherReservationId;
    /** 내 예약 ID (예외 시나리오용) */
    private Long myReservationId;

    // ==========================================
    // 헬퍼 메서드
    // ==========================================

    /**
     * 기본 콘서트 ID 반환
     */
    public String getDefaultConcertId() {
        return DEFAULT_CONCERT_ID;
    }

    /**
     * 컨텍스트 초기화 (시나리오 시작 시)
     */
    public void reset() {
        lastHttpResponse = null;
        currentScheduleId = null;
        currentSeatId = null;
        currentUserId = null;
        currentQueueToken = null;
        currentReservationId = null;
        otherUserId = null;
        otherReservationId = null;
        myReservationId = null;
        successfulReservations.set(0);
        failedReservations.set(0);
    }
}
