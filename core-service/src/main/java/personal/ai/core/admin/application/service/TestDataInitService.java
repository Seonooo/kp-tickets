package personal.ai.core.admin.application.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Test Data Initialization Service
 *
 * 성능 테스트를 위한 초기 데이터 생성 서비스
 *
 * WARNING: 테스트 전용 API - 프로덕션에서 비활성화 필요
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TestDataInitService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 테스트 데이터 초기화
     *
     * @return 생성된 데이터 개수 (concerts, schedules, seats)
     */
    @Transactional
    public Map<String, Long> initializeTestData() {
        log.info("Starting test data initialization...");

        // 1. 기존 데이터 삭제
        cleanupExistingData();

        // 2. Concert 생성 (id=1)
        createConcert();

        // 3. Schedule 생성 (id=1, concert_id=1)
        createSchedule();

        // 4. Seats 생성 (10,000개)
        long seatCount = createSeats();

        log.info("Test data initialization completed - Concerts: 1, Schedules: 1, Seats: {}", seatCount);

        return Map.of(
                "concerts", 1L,
                "schedules", 1L,
                "seats", seatCount
        );
    }

    /**
     * 기존 데이터 삭제
     */
    private void cleanupExistingData() {
        log.debug("Cleaning up existing data...");

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");

        jdbcTemplate.execute("TRUNCATE TABLE payment_outbox_events");
        jdbcTemplate.execute("TRUNCATE TABLE outbox_events");
        jdbcTemplate.execute("TRUNCATE TABLE payments");
        jdbcTemplate.execute("TRUNCATE TABLE reservations");
        jdbcTemplate.execute("TRUNCATE TABLE seats");
        jdbcTemplate.execute("TRUNCATE TABLE concert_schedules");
        jdbcTemplate.execute("TRUNCATE TABLE concerts");

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");

        log.debug("Existing data cleaned up");
    }

    /**
     * Concert 생성 (id=1)
     */
    private void createConcert() {
        log.debug("Creating test concert...");

        jdbcTemplate.update(
                "INSERT INTO concerts (id, name, description) VALUES (1, 'K6 Performance Test Concert', '성능 테스트용 콘서트')"
        );

        log.debug("Concert created - id: 1");
    }

    /**
     * Schedule 생성 (id=1)
     */
    private void createSchedule() {
        log.debug("Creating test schedule...");

        jdbcTemplate.update(
                "INSERT INTO concert_schedules (id, concert_id, performance_date, venue) " +
                        "VALUES (1, 1, '2025-12-25 19:00:00', 'Test Arena')"
        );

        log.debug("Schedule created - id: 1");
    }

    /**
     * Seats 생성 (10,000개)
     * - VIP: 2,500개 (50,000원)
     * - R: 2,500개 (40,000원)
     * - S: 2,500개 (30,000원)
     * - A: 2,500개 (20,000원)
     */
    private long createSeats() {
        log.debug("Creating test seats...");

        long totalSeats = 0;

        // VIP석 2,500개
        totalSeats += createSeatsByGrade("VIP", 2500, "50000.00");

        // R석 2,500개
        totalSeats += createSeatsByGrade("R", 2500, "40000.00");

        // S석 2,500개
        totalSeats += createSeatsByGrade("S", 2500, "30000.00");

        // A석 2,500개
        totalSeats += createSeatsByGrade("A", 2500, "20000.00");

        log.debug("Seats created - total: {}", totalSeats);
        return totalSeats;
    }

    /**
     * 특정 등급의 좌석 생성
     */
    private int createSeatsByGrade(String grade, int count, String price) {
        String sql = "INSERT INTO seats (schedule_id, seat_number, grade, price, status) " +
                "SELECT " +
                "  1, " +
                "  CONCAT(?, '-', LPAD(seq, 4, '0')), " +
                "  ?, " +
                "  ?, " +
                "  'AVAILABLE' " +
                "FROM (" +
                "  SELECT @row := @row + 1 as seq " +
                "  FROM " +
                "    (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 " +
                "     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1, " +
                "    (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 " +
                "     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2, " +
                "    (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 " +
                "     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t3, " +
                "    (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 " +
                "     UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t4, " +
                "    (SELECT @row := 0) r " +
                "  LIMIT ? " +
                ") as numbers";

        jdbcTemplate.update(sql, grade, grade, price, count);

        log.debug("Created {} {} seats (price: {})", count, grade, price);
        return count;
    }
}
