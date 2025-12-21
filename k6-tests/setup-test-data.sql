-- ============================================================================
-- 성능 테스트용 데이터 초기화 및 생성 스크립트
-- ============================================================================

-- 기존 데이터 삭제 (외래키 제약 순서 고려)
SET FOREIGN_KEY_CHECKS = 0;

TRUNCATE TABLE payment_outbox_events;
TRUNCATE TABLE outbox_events;
TRUNCATE TABLE payments;
TRUNCATE TABLE reservations;
TRUNCATE TABLE seats;
TRUNCATE TABLE concert_schedules;
TRUNCATE TABLE concerts;

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================================
-- 1. 테스트용 콘서트 데이터 생성
-- ============================================================================
-- concerts 테이블 구조: id, name, description
INSERT INTO concerts (id, name, description) VALUES
(1, 'K6 Performance Test Concert', '성능 테스트용 콘서트');

-- ============================================================================
-- 2. 테스트용 스케줄 데이터 생성
-- ============================================================================
-- concert_schedules 테이블 구조: id, concert_id, performance_date, venue
INSERT INTO concert_schedules (id, concert_id, performance_date, venue) VALUES
(1, 1, '2025-12-25 19:00:00', 'Test Arena');

-- ============================================================================
-- 3. 테스트용 좌석 데이터 생성 (100개 - AVAILABLE 상태)
-- ============================================================================
-- seats 테이블 구조: id, schedule_id, seat_number, grade, price, status
-- SeatGrade: VIP, R, S, A
-- SeatStatus: AVAILABLE, RESERVED, OCCUPIED

-- VIP석 25개 (50,000원)
INSERT INTO seats (schedule_id, seat_number, grade, price, status)
SELECT
    1,
    CONCAT('VIP-', LPAD(seq, 3, '0')),
    'VIP',
    50000.00,
    'AVAILABLE'
FROM (
    SELECT (@row := @row + 1) as seq
    FROM
        (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) t1,
        (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) t2,
        (SELECT @row := 0) r
    LIMIT 25
) as numbers;

-- R석 25개 (40,000원)
INSERT INTO seats (schedule_id, seat_number, grade, price, status)
SELECT
    1,
    CONCAT('R-', LPAD(seq, 3, '0')),
    'R',
    40000.00,
    'AVAILABLE'
FROM (
    SELECT (@row := @row + 1) as seq
    FROM
        (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) t1,
        (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) t2,
        (SELECT @row := 0) r
    LIMIT 25
) as numbers;

-- S석 25개 (30,000원)
INSERT INTO seats (schedule_id, seat_number, grade, price, status)
SELECT
    1,
    CONCAT('S-', LPAD(seq, 3, '0')),
    'S',
    30000.00,
    'AVAILABLE'
FROM (
    SELECT (@row := @row + 1) as seq
    FROM
        (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) t1,
        (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) t2,
        (SELECT @row := 0) r
    LIMIT 25
) as numbers;

-- A석 25개 (20,000원)
INSERT INTO seats (schedule_id, seat_number, grade, price, status)
SELECT
    1,
    CONCAT('A-', LPAD(seq, 3, '0')),
    'A',
    20000.00,
    'AVAILABLE'
FROM (
    SELECT (@row := @row + 1) as seq
    FROM
        (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) t1,
        (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) t2,
        (SELECT @row := 0) r
    LIMIT 25
) as numbers;

-- ============================================================================
-- 데이터 확인
-- ============================================================================
SELECT 'Concerts' as table_name, COUNT(*) as count FROM concerts
UNION ALL
SELECT 'Concert Schedules', COUNT(*) FROM concert_schedules
UNION ALL
SELECT 'Seats', COUNT(*) FROM seats;
