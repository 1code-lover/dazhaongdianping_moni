package com.hmdp;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Shop;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IVoucherOrderService voucherOrderService;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    void testSaveShop() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }

    @Test
    void loadShopData() {
        List<Shop> list = shopService.list();
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            List<Shop> value = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
            for (Shop shop : value) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
    }

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];
        int j = 0;
        for (int i = 0; i < 1000000; i++) {
            j = i % 1000;
            values[j] = "user_" + i;
            if(j == 999){
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println("count = " + count);
    }

    @Test
    void benchmarkSeckillVoucherSingleUserContention() throws InterruptedException {
        long voucherId = 10L;
        int threadCount = 500;
        runSeckillBenchmark(voucherId, threadCount, false, 1000L);
    }

    @Test
    void benchmarkSeckillVoucherMultiUserContention() throws InterruptedException {
        long voucherId = 10L;
        int threadCount = 500;
        runSeckillBenchmark(voucherId, threadCount, true, 100000L);
    }

    private void runSeckillBenchmark(long voucherId, int threadCount, boolean multiUser, long userBaseId) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(threadCount, 200));
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);
        AtomicInteger success = new AtomicInteger();
        AtomicInteger duplicate = new AtomicInteger();
        AtomicInteger noStock = new AtomicInteger();
        AtomicInteger error = new AtomicInteger();
        AtomicInteger started = new AtomicInteger();
        AtomicInteger finished = new AtomicInteger();

        System.out.println("Benchmark init: voucherId=" + voucherId + ", threadCount=" + threadCount + ", multiUser=" + multiUser);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    UserDTO user = new UserDTO();
                    user.setId(multiUser ? userBaseId + index : userBaseId);
                    user.setNickName("bench_" + user.getId());
                    UserHolder.saveUser(user);
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    started.incrementAndGet();
                    Result result = voucherOrderService.seckillVoucher(voucherId);
                    if (result != null && Boolean.TRUE.equals(result.getSuccess())) {
                        success.incrementAndGet();
                    } else if (result != null && "不能重复下单".equals(result.getErrorMsg())) {
                        duplicate.incrementAndGet();
                    } else if (result != null && "库存不足".equals(result.getErrorMsg())) {
                        noStock.incrementAndGet();
                    } else {
                        error.incrementAndGet();
                    }
                } catch (Exception e) {
                    error.incrementAndGet();
                    System.out.println("Benchmark worker error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                } finally {
                    finished.incrementAndGet();
                    UserHolder.removeUser();
                    done.countDown();
                }
            });
        }

        boolean allReady = ready.await(10, TimeUnit.SECONDS);
        System.out.println("Benchmark ready: allReady=" + allReady + ", readyCount=" + (threadCount - ready.getCount()));
        long begin = System.nanoTime();
        start.countDown();
        boolean completed = done.await(30, TimeUnit.SECONDS);
        long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
        long qps = costMs == 0 ? threadCount : threadCount * 1000L / costMs;

        System.out.println("====== Seckill Benchmark ======");
        System.out.println("voucherId = " + voucherId);
        System.out.println("threadCount = " + threadCount);
        System.out.println("multiUser = " + multiUser);
        System.out.println("completed = " + completed);
        System.out.println("started = " + started.get());
        System.out.println("finished = " + finished.get());
        System.out.println("remaining = " + done.getCount());
        System.out.println("costMs = " + costMs);
        System.out.println("qps = " + qps);
        System.out.println("success = " + success.get());
        System.out.println("duplicate = " + duplicate.get());
        System.out.println("noStock = " + noStock.get());
        System.out.println("error = " + error.get());
        System.out.println("===============================");

        executor.shutdownNow();
    }
}
