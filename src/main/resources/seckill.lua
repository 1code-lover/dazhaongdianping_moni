-- ============================================================================
-- 秒杀下单 Lua 脚本 —— 在 Redis 中原子性完成：库存校验 + 一人一一单 + 预扣库存 + 发送订单消息
-- ============================================================================
--
-- 【在整个秒杀链路中的位置】
--
--   用户请求秒杀
--     │
--     ▼
--   VoucherOrderServiceImpl.seckillVoucher()   ← Java入口
--     │
--     ▼
--   ★ 本脚本（seckill.lua）在Redis中原子执行 ★
--     │
--     ├─ 返回0 → 成功：库存已预扣，订单消息已发到Stream
--     ├─ 返回1 → 失败：库存不足
--     └─ 返回2 → 失败：重复下单
--     │
--     ▼
--   RedisStreamToKafkaRelay 中转
--     │
--     ▼
--   Kafka → VoucherOrderConsumer 消费
--     │
--     ▼
--   数据库落库（createVoucherOrder）
--
-- 【为什么要用Lua脚本】
--   秒杀场景下，库存校验和预扣必须是原子操作。
--   如果拆成多个Redis命令：GET判断库存 → DECR扣库存 → SADD记录用户
--   中间可能被其他请求插入，导致超卖。
--   Lua脚本在Redis中单线程执行，天然原子性，不会被其他命令打断。
--
-- 【Redis中存储的数据结构】
--   seckill:stock:{voucherId}  → String  库存数量（启动时从DB预热）
--   seckill:order:{voucherId}  → Set     已下单用户ID集合（一人一单校验）
--   stream.orders              → Stream  订单消息队列（发给Kafka中转）
--
-- 【参数说明】
--   KEYS：无（本脚本不使用KEYS参数，所有key由ARGV动态拼接）
--   ARGV[1]：voucherId  优惠券ID
--   ARGV[2]：userId     用户ID
--   ARGV[3]：orderId    全局唯一订单ID（由RedisIdWorker生成）
--

-- ==================== 第1步：获取参数 ====================
local voucherId = ARGV[1]   -- 优惠券ID
local userId = ARGV[2]       -- 用户ID
local orderId = ARGV[3]      -- 订单ID（全局唯一，由RedisIdWorker的日期+序列号生成）

-- ==================== 第2步：拼接Redis Key ====================
local stockKey = 'seckill:stock:' .. voucherId   -- 库存key，如 seckill:stock:10
local orderKey = 'seckill:order:' .. voucherId   -- 已下单用户集合key，如 seckill:order:10

-- ==================== 第3步：校验库存 ====================
-- GET seckill:stock:10 → 获取当前剩余库存
local stock = tonumber(redis.call('get', stockKey))
if((not stock) or stock <= 0) then
    -- 库存不足或key不存在，返回1
    -- 这个校验在Redis层面拦截了大量无效请求，不会打到数据库
    return 1
end

-- ==================== 第4步：校验一人一单 ====================
-- SISMEMBER seckill:order:10 userId → 判断用户是否已下单
-- 返回1表示已存在（已下过单），返回0表示不存在
if(redis.call('sismember', orderKey, userId) == 1) then
    -- 用户已下过单，不允许重复购买，返回2
    return 2
end

-- ==================== 第5步：预扣库存 ====================
-- INCRBY seckill:stock:10 -1 → 库存-1
-- 注意：这是"预扣"，不是最终扣减。如果后续DB落库失败，需要回滚（seckill_rollback.lua）
redis.call('incrby', stockKey, -1)

-- ==================== 第6步：记录已下单用户 ====================
-- SADD seckill:order:10 userId → 将用户ID加入已下单集合
-- 用于一人一单校验，防止同一用户重复秒杀
redis.call('sadd', orderKey, userId)

-- ==================== 第7步：发送订单消息到Redis Stream ====================
-- XADD stream.orders * userId xxx voucherId xxx orderId xxx
-- 将订单数据写入Redis Stream消息队列
-- 后续由 RedisStreamToKafkaRelay 消费并转发到Kafka
-- * 表示由Redis自动生成消息ID（格式：时间戳-序列号）
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'orderId', orderId)

-- ==================== 返回成功 ====================
return 0