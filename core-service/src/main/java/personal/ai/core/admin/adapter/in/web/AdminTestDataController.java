package personal.ai.core.admin.adapter.in.web;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import personal.ai.core.admin.application.service.TestDataInitService;

import java.util.Map;

/**
 * Admin Test Data Controller
 *
 * WARNING: 성능 테스트 전용 API입니다.
 * 프로덕션 환경에서는 자동 비활성화됩니다. (@Profile("!prod"))
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/test-data")
@Profile("!prod")
@RequiredArgsConstructor
public class AdminTestDataController {

    private final TestDataInitService testDataInitService;

    /**
     * 테스트 데이터 초기화
     *
     * - 기존 데이터 삭제 (Payments, Reservations, Seats, Schedules, Concerts)
     * - 테스트용 Concert 1개 생성 (id=1)
     * - 테스트용 Schedule 1개 생성 (id=1, 2025-12-25 19:00)
     * - 테스트용 Seats 10,000개 생성 (VIP/R/S/A 각 2,500개, AVAILABLE)
     *
     * @return 초기화 결과 (생성된 데이터 개수)
     */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initializeTestData() {
        log.info("Initializing test data via API...");

        long startTime = System.currentTimeMillis();
        Map<String, Long> counts = testDataInitService.initializeTestData();
        long duration = System.currentTimeMillis() - startTime;

        log.info("Test data initialized successfully in {}ms - Concerts: {}, Schedules: {}, Seats: {}",
                duration, counts.get("concerts"), counts.get("schedules"), counts.get("seats"));

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Test data initialized successfully",
                "duration_ms", duration,
                "data", counts));
    }
}
