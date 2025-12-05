-- remove_expired_tokens.lua
-- KEYS[1]: Active Queue Key (ZSet)
-- ARGV[1]: Min Score (0)
-- ARGV[2]: Max Score (Current Time Epoch Second)
-- ARGV[3]: Token Key Prefix ("queue:active:token:")
-- ARGV[4]: ConcertId (for key generation)

-- 1. Get Expired User IDs
local expiredUserIds = redis.call('ZRANGEBYSCORE', KEYS[1], ARGV[1], ARGV[2])

if #expiredUserIds == 0 then
    return 0
end

-- 2. Remove from ZSet
redis.call('ZREMRANGEBYSCORE', KEYS[1], ARGV[1], ARGV[2])

-- 3. Build token keys array for batch deletion
local tokenKeys = {}
for _, userId in ipairs(expiredUserIds) do
    tokenKeys[#tokenKeys + 1] = ARGV[3] .. ARGV[4] .. ":" .. userId
end

-- 4. Batch delete with UNLINK (non-blocking, asynchronous)
if #tokenKeys > 0 then
     local batchSize = 1000
             for i = 1, #tokenKeys, batchSize do
                 local batch = {}
                 for j = i, math.min(i + batchSize - 1, #tokenKeys) do
                    batch[#batch + 1] = tokenKeys[j]
                 end
                 redis.call('UNLINK', unpack(batch))
             end
end

return #expiredUserIds
