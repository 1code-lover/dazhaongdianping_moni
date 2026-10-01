package com.hmdp.config;

import com.hmdp.entity.Shop;
import com.hmdp.service.IShopService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

/**
 * 商户坐标预热器 —— 应用启动时将数据库中的商户坐标加载到 Redis GEO
 *
 * 【背景】
 *   附近商户查询（/shop/of/type 带 x/y）依赖 Redis GEO 做距离排序，
 *   但 Geo 数据此前从未加载，导致距离查询始终走降级分页。
 *   本类仿 SeckillStockPreheatRunner，在启动时按分类分组写入 GEO。
 *
 * 【写入结构】
 *   key: shop:geo:{typeId}  value: GEOADD (经度x, 纬度y, 商户id)
 */
@Component
public class ShopGeoPreheatRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ShopGeoPreheatRunner.class);

    @Resource
    private IShopService shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 应用启动后执行：全量加载商户坐标到 Redis GEO
     *
     * @param args 启动参数（未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        // 1.查询所有有坐标的商户
        List<Shop> shops = shopService.query()
                .isNotNull("x")
                .isNotNull("y")
                .list();
        if (shops.isEmpty()) {
            log.warn("ShopGeoPreheatRunner: 无可用坐标商户，跳过 GEO 预热");
            return;
        }

        // 2.按分类分组，逐组写入 GEO（先清旧数据保证幂等）
        Map<Long, List<Shop>> byType = shops.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));
        int total = 0;
        for (Map.Entry<Long, List<Shop>> entry : byType.entrySet()) {
            String key = SHOP_GEO_KEY + entry.getKey();
            stringRedisTemplate.delete(key);
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(entry.getValue().size());
            for (Shop shop : entry.getValue()) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new org.springframework.data.geo.Point(shop.getX(), shop.getY())));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
            total += locations.size();
        }
        log.info("ShopGeoPreheatRunner: 商户坐标 GEO 预热完成，共 {} 条，覆盖 {} 个分类", total, byType.size());
    }
}
