-- move_to_active_queue.lua
-- Wait Queue에서 Pop하고 Active Queue에 추가하는 작업을 원자적으로 처리
-- 실패 시 롤백으로 데이터 손실 방지
--
-- KEYS[1]: Wait Queue Key (ZSet)
-- KEYS[2]: Active Queue Key (ZSet)
-- ARGV[1]: Batch Size (전환할 인원 수)
-- ARGV[2]: Expiration Time (epoch seconds, READY 상태 만료 시간)
-- ARGV[3]: Token Key Prefix ("active:token:")
-- ARGV[4]: Concert ID
-- ARGV[5]: TTL (seconds)
--
-- Return: JSON array of moved user IDs
-- Example: ["USER-001", "USER-002", "USER-003"]
--
-- 동작:
-- 1. Wait Queue에서 ZPOPMIN (가장 먼저 대기한 N명)
-- 2. 각 유저별로 토큰 생성 및 Active Queue 추가
-- 3. 실패 시 해당 유저는 Wait Queue에 다시 추가 (롤백)

local waitQueueKey = KEYS[1]
local activeQueueKey = KEYS[2]
local batchSize = tonumber(ARGV[1])
local expiredAt = tonumber(ARGV[2])
local tokenPrefix = ARGV[3]
local concertId = ARGV[4]
local ttl = tonumber(ARGV[5])

-- 1. Wait Queue에서 Pop
local poppedUsers = redis.call('ZPOPMIN', waitQueueKey, batchSize)

if #poppedUsers == 0 then
    return "[]"  -- 빈 배열 반환 (JSON 문자열)
end

-- 2. 성공한 유저 ID 목록
local movedUserIds = {}

-- 3. Popped users 파싱 (ZPOPMIN은 [member, score, member, score, ...] 형태로 반환)
for i = 1, #poppedUsers, 2 do
    local userId = poppedUsers[i]
    local originalScore = poppedUsers[i + 1]

    -- 토큰 생성 (간단한 UUID 형태)
    local token = redis.call('INCR', 'queue:token:counter')
    token = concertId .. ':' .. userId .. ':' .. token

    -- Active Queue에 추가 시도
    local success = pcall(function()
        -- Active Queue (ZSet)에 추가
        redis.call('ZADD', activeQueueKey, expiredAt, userId)

        -- Token Hash 생성 (Hash Tag 형식: active:token:{concertId}:userId)
        local tokenKey = tokenPrefix .. '{' .. concertId .. '}:' .. userId
        redis.call('HSET', tokenKey,
            'token', token,
            'status', 'READY',
            'extend_count', '0',
            'expired_at', expiredAt
        )

        -- TTL 설정
        redis.call('EXPIRE', tokenKey, ttl)
    end)

    if success then
        -- 성공: 이동 완료
        movedUserIds[#movedUserIds + 1] = userId
    else
        -- 실패: Wait Queue에 되돌리기 (롤백)
        redis.call('ZADD', waitQueueKey, originalScore, userId)
    end
end

-- 4. 성공한 유저 ID 목록 반환 (JSON 배열)
if #movedUserIds == 0 then
    return "[]"  -- 빈 배열 (모든 유저가 롤백된 경우)
end
return cjson.encode(movedUserIds)
