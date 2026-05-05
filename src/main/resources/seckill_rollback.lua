-- ============================================================================
-- 秒杀库存回滚 Lua 脚本 —— 回滚 Redis 预扣的库存和一人一单标记
-- ============================================================================
--
-- 【什么时候需要回滚】
--
--   当 Kafka 消费者处理订单消息失败，且异常被判定为"不可恢复"时，
--   需要回滚 Lua 脚本预扣的库存和用户标记。
--
--   场景举例：
--     - DB库存不足（说明 Lua 预扣了但 DB 没扣成功）
--     - 数据错误（无法创建订单）
--     - 其他系统错误
--
-- 【为什么要回滚】
--
--   Lua 脚本已经执行了：
--     1. INCRBY seckill:stock:{voucherId} -1  （库存预扣）
--     2. SADD seckill:order:{voucherId} {userId}  （用户标记）
--
--   如果 DB 落库失败，这个预扣仍然有效，导致：
--     - 库存少算了（实际没卖出去但库存减少了）
--     - 用户被标记了（无法再次购买）
--
--   所以需要回滚：恢复库存 + 移除用户标记
--
-- 【幂等性设计】
--
--   如果用户已经不在 order 集合中（SISMEMBER 返回 0），
--   说明已经回滚过了，直接返回成功。
--   这是幂等保证，防止重复回滚。
--
-- 【参数说明】
--   ARGV[1]：voucherId  优惠券ID
--   ARGV[2]：userId     用户ID
--

-- ==================== 第1步：获取参数 ====================
local voucherId = ARGV[1]
local userId = ARGV[2]
local stockKey = 'seckill:stock:' .. voucherId   -- 库存key
local orderKey = 'seckill:order:' .. voucherId   -- 用户订单key

-- ==================== 第2步：检查用户是否在订单集合中 ====================
-- SISMEMBER seckill:order:{voucherId} {userId}
-- 返回1=存在，0=不存在
if redis.call('sismember', orderKey, userId) == 0 then
    -- 用户已不在订单集合中
    -- 说明已经回滚过了，或者预扣根本没成功
    -- 直接返回（幂等）
    return 1
end

-- ==================== 第3步：执行回滚 ====================
-- SREM seckill:order:{voucherId} {userId}
-- 移除用户标记，允许用户重新购买
redis.call('srem', orderKey, userId)

-- INCRBY seckill:stock:{voucherId} 1
-- 恢复库存
redis.call('incrby', stockKey, 1)

-- ==================== 返回成功 ====================
return 0