package personal.ai.e2e.context;

import io.restassured.response.Response;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Test Context
 * Cucumber 시나리오 간 데이터 공유를 위한 컨텍스트
 */
@Data
public class TestContext {

    // 환경 설정
    private String queueServiceUrl = System.getenv().getOrDefault("QUEUE_URL", "http://localhost:8081");
    private String coreServiceUrl = System.getenv().getOrDefault("CORE_URL", "http://localhost:8080");

    // 테스트 데이터
    private String userId;
    private String concertId;
    private String scheduleId;
    private String queueToken;
    private Long reservationId;
    private Long paymentId;
    private Long selectedSeatId;

    // API 응답
    private Response lastResponse;
    private Map<String, Response> responses = new HashMap<>();

    // 측정 메트릭
    private Map<String, Long> stepDurations = new HashMap<>();
    private long stepStartTime;

    /**
     * 단계 시작 시간 기록
     */
    public void startStep(String stepName) {
        stepStartTime = System.currentTimeMillis();
    }

    /**
     * 단계 종료 및 소요 시간 기록
     */
    public void endStep(String stepName) {
        long duration = System.currentTimeMillis() - stepStartTime;
        stepDurations.put(stepName, duration);
    }

    /**
     * 응답 저장
     */
    public void saveResponse(String key, Response response) {
        responses.put(key, response);
        this.lastResponse = response;
    }

    /**
     * 컨텍스트 초기화
     */
    public void reset() {
        userId = null;
        concertId = null;
        scheduleId = null;
        queueToken = null;
        reservationId = null;
        paymentId = null;
        selectedSeatId = null;
        lastResponse = null;
        responses.clear();
        stepDurations.clear();
    }
}
