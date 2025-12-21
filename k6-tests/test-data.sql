-- K6 테스트용 데이터 준비 (실제 스키마에 맞춤)
USE concert_db;

-- 콘서트 데이터
INSERT INTO concerts (id, name, description)
VALUES (1, 'K6 Test Concert', 'Performance Test Concert')
ON DUPLICATE KEY UPDATE name = 'K6 Test Concert';

-- 스케줄 데이터 (concert_schedules 테이블)
INSERT INTO concert_schedules (id, concert_id, performance_date, venue)
VALUES (1, 1, '2025-12-25 19:00:00', 'Seoul Olympic Stadium')
ON DUPLICATE KEY UPDATE venue = 'Seoul Olympic Stadium';

-- 기존 좌석 삭제
DELETE FROM seats WHERE schedule_id = 1;

-- 좌석 데이터 생성 (100개) - R등급
INSERT INTO seats (schedule_id, seat_number, status, price, grade)
SELECT
    1,
    CONCAT('R-', LPAD(seq, 3, '0')),
    'AVAILABLE',
    50000.00,
    'R'
FROM (
    SELECT @row := @row + 1 as seq
    FROM information_schema.columns t1,
         information_schema.columns t2,
         (SELECT @row := 0) r
    LIMIT 100
) as numbers;

-- 확인
SELECT 
    'Concerts' as table_name, COUNT(*) as count FROM concerts WHERE id = 1
UNION ALL
SELECT 'Schedules', COUNT(*) FROM concert_schedules WHERE id = 1  
UNION ALL
SELECT 'Seats', COUNT(*) FROM seats WHERE schedule_id = 1;
