-- enter_queue.lua
-- 대기열 진입 처리 (Phase 3-2 최적화)
--
-- 목적: 6회 Redis 호출을 1회 Lua 스크립트 실행으로 통합
--   - 네트워크 RTT 5회 절약 (약 5ms)
--   - 원자성 보장
--   - 예상 TPS 향상: +30~50%
--
-- 로직:
--   1. Active Queue 확인 (이미 활성화된 사용자)
--   2. Wait Queue 확인 (이미 대기 중인 사용자)
--   3. Wait Queue 신규 진입
--
-- KEYS[1]: active:token:{concertId}:userId (Hash)
-- KEYS[2]: queue:wait:{concertId} (ZSet)
-- ARGV[1]: userId (사용자 ID)
-- ARGV[2]: score (진입 시각 timestamp)
-- ARGV[3]: currentTime (현재 시각, 만료 확인용)
--
-- Return: JSON string
-- {
--   "status": "ACTIVE" | "WAITING" | "NEW",
--   "position": number (0-based rank),
--   "totalWaiting": number,
--   "token": {...} (ACTIVE 상태일 때만)
-- }

local activeTokenKey = KEYS[1]
local waitQueueKey = KEYS[2]
local userId = ARGV[1]
local score = tonumber(ARGV[2])
local currentTime = tonumber(ARGV[3])

-- ============================================
-- 1. Active Token 확인
-- ============================================
local activeToken = redis.call('HGETALL', activeTokenKey)

if #activeToken > 0 then
    -- Active Token이 존재하면 만료 여부 확인
    -- HGETALL 결과: {key1, value1, key2, value2, ...}
    local tokenData = {}
    for i = 1, #activeToken, 2 do
        tokenData[activeToken[i]] = activeToken[i + 1]
    end

    local expiredAt = tonumber(tokenData['expired_at'])

    if expiredAt and expiredAt > currentTime then
        -- 만료되지 않은 Active Token 존재
        -- 상태: ACTIVE
        -- 토큰 정보를 그대로 반환
        return cjson.encode({
            status = 'ACTIVE',
            position = 0,
            totalWaiting = 0,
            token = tokenData
        })
    end

    -- 만료된 토큰은 무시하고 Wait Queue 확인으로 진행
end

-- ============================================
-- 2. Wait Queue 확인 (이미 대기 중인지)
-- ============================================
local existingRank = redis.call('ZRANK', waitQueueKey, userId)

if existingRank then
    -- 이미 Wait Queue에 존재
    -- 상태: WAITING (재진입)
    local totalWaiting = redis.call('ZCARD', waitQueueKey)

    return cjson.encode({
        status = 'WAITING',
        position = existingRank,
        totalWaiting = totalWaiting,
        token = cjson.null
    })
end

-- ============================================
-- 3. Wait Queue 신규 진입
-- ============================================
-- ZADD: Wait Queue에 추가
redis.call('ZADD', waitQueueKey, score, userId)

-- 추가 후 순번 조회
local newRank = redis.call('ZRANK', waitQueueKey, userId)
local totalWaiting = redis.call('ZCARD', waitQueueKey)

return cjson.encode({
    status = 'NEW',
    position = newRank,
    totalWaiting = totalWaiting,
    token = cjson.null
})
