-- ============================================================================
-- 秒杀下单 Lua 脚本（无 Stream 版本）—— 原子完成：库存校验 + 一人一单 + 预扣库存
-- ============================================================================
--
-- 用途：
--   用于 sync-db 模式压测。脚本只负责 Redis 资格校验与预扣，不写入 Stream，
--   后续由请求线程直接同步落库，便于和异步链路做真实对照。
--
-- 参数：
--   ARGV[1]：voucherId
--   ARGV[2]：userId
--   ARGV[3]：orderId（保留参数位，便于与 seckill.lua 保持一致）
--

local voucherId = ARGV[1]
local userId = ARGV[2]

local stockKey = 'seckill:stock:' .. voucherId
local orderKey = 'seckill:order:' .. voucherId

local stock = tonumber(redis.call('get', stockKey))
if ((not stock) or stock <= 0) then
    return 1
end

if (redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

redis.call('incrby', stockKey, -1)
redis.call('sadd', orderKey, userId)

return 0
