package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.CacheInvalidationPublisher;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;

    @Resource
    private CacheInvalidationPublisher cacheInvalidationPublisher;

    @Override
    public Result queryById(Long id) {
        Shop shop = cacheClient.queryWithLocalCache(
                cacheClient.getShopLocalCache(),
                id,
                CACHE_SHOP_KEY,
                Shop.class,
                this::getById,
                CACHE_SHOP_TTL,
                TimeUnit.MINUTES
        );
        if (shop == null) {
            return Result.fail("店铺不存在！");
        }
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        updateById(shop);
        cacheClient.delete(CACHE_SHOP_KEY + id);
        cacheClient.deleteShopLocalCache(id);
        log.info("Shop updated, cache cleared on current node, shopId={}", id);
        cacheInvalidationPublisher.publishShopInvalidation(id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }

        int pageSize = SystemConstants.DEFAULT_PAGE_SIZE;
        int from = (current - 1) * pageSize;
        int end = current * pageSize;

        String key = SHOP_GEO_KEY + typeId;
        GeoResults<RedisGeoCommands.GeoLocation<String>> results;
        try {
            results = stringRedisTemplate.opsForGeo()
                    .radius(
                            key,
                            new Circle(new Point(x, y), new Distance(5.0, Metrics.KILOMETERS)),
                            RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                                    .includeDistance()
                                    .limit(end)
                    );
        } catch (Exception e) {
            log.error("Redis GEORADIUS query failed", e);
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, pageSize));
            return Result.ok(page.getRecords());
        }

        if (results == null || results.getContent().isEmpty()) {
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, pageSize));
            return Result.ok(page.getRecords());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> geoList = results.getContent();
        if (geoList.size() <= from) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> shopIds = new ArrayList<>(pageSize);
        Map<String, Distance> distanceMap = new HashMap<>(pageSize);
        geoList.stream()
                .skip(from)
                .limit(pageSize)
                .forEach(geoResult -> {
                    String shopIdStr = geoResult.getContent().getName();
                    shopIds.add(Long.valueOf(shopIdStr));
                    distanceMap.put(shopIdStr, geoResult.getDistance());
                });

        if (shopIds.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        String idStr = StrUtil.join(",", shopIds);
        List<Shop> shops = query()
                .in("id", shopIds)
                .last("ORDER BY FIELD(id," + idStr + ")")
                .list();

        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        return Result.ok(shops);
    }
}
