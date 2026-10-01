package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.utils.PageUtils;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopDocument;
import com.hmdp.mapper.ShopDocumentRepository;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.geo.GeoPoint;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_SEARCH_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_SEARCH_TTL;
import static org.elasticsearch.index.query.QueryBuilders.*;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;

@Slf4j
@Service
public class ShopSearchServiceImpl implements IShopSearchService {

    @Resource
    private ShopDocumentRepository shopDocumentRepository;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 按关键字搜索商户
     * 读路径：Redis 短 TTL 缓存（30s）→ ES；ES 异常时降级为 DB 模糊查询兜底
     *
     * @param keyword 搜索关键字
     * @param page    页码（从1开始）
     * @param size    每页条数
     * @param x       经度（可选，距离排序）
     * @param y       纬度（可选，距离排序）
     * @return 搜索结果
     */
    @Override
    public Result searchByKeyword(String keyword, Integer page, Integer size, Double x, Double y) {
        // 1.构造缓存键：关键字+分页+坐标共同参与，避免排序语义错乱
        String cacheKey = CACHE_SHOP_SEARCH_KEY + buildCacheKey(keyword, page, size, x, y);
        String cached = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(cached)) {
            List<ShopDocument> shops = JSONUtil.toList(cached, ShopDocument.class);
            return Result.ok(shops);
        }

        // 2.查 ES；异常时降级 DB 模糊查询
        try {
            NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder()
                    .withQuery(boolQuery()
                            .should(matchQuery("name", keyword).boost(2.0f))
                            .should(matchQuery("address", keyword))
                            .should(matchQuery("area", keyword)))
                    .withPageable(PageRequest.of(PageUtils.toZeroBasedPage(page), PageUtils.normalizePageSize(size)));

            if (x != null && y != null) {
                queryBuilder.withSort(SortBuilders.geoDistanceSort("location", new org.elasticsearch.common.geo.GeoPoint(y, x))
                        .order(SortOrder.ASC)
                        .unit(org.elasticsearch.common.unit.DistanceUnit.KILOMETERS));
            }

            NativeSearchQuery query = queryBuilder.build();
            SearchHits<ShopDocument> hits = elasticsearchRestTemplate.search(query, ShopDocument.class);

            List<ShopDocument> shops = hits.getSearchHits().stream()
                    .map(hit -> hit.getContent())
                    .collect(Collectors.toList());
            // 2.1 写缓存（30s）
            stringRedisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(shops),
                    CACHE_SHOP_SEARCH_TTL, TimeUnit.SECONDS);
            return Result.ok(shops);
        } catch (Exception e) {
            log.warn("ES 搜索异常，降级为数据库模糊查询，keyword={}", keyword, e);
            List<Shop> fallback = shopMapper.selectList(new LambdaQueryWrapper<Shop>()
                    .like(Shop::getName, keyword)
                    .or()
                    .like(Shop::getAddress, keyword)
                    .last("LIMIT " + PageUtils.normalizePageSize(size)));
            List<ShopDocument> shops = fallback.stream()
                    .map(shop -> BeanUtil.copyProperties(shop, ShopDocument.class))
                    .collect(Collectors.toList());
            return Result.ok(shops);
        }
    }

    /**
     * 构造搜索缓存键指纹：关键字、分页、坐标拼接后取 MD5 摘要
     *
     * @param keyword 搜索关键字
     * @param page    页码
     * @param size    每页条数
     * @param x       经度（可空）
     * @param y       纬度（可空）
     * @return MD5 摘要字符串
     */
    private String buildCacheKey(String keyword, Integer page, Integer size, Double x, Double y) {
        String raw = keyword + "|" + page + "|" + size + "|" + (x == null ? "0" : x) + "|" + (y == null ? "0" : y);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // MD5 算法不可用时退化为原始串（不会发生，兜底）
            return Integer.toHexString(raw.hashCode());
        }
    }

    /**
     * 清除全部商户搜索缓存（商户数据变更后调用）
     */
    private void evictSearchCache() {
        Set<String> keys = stringRedisTemplate.keys(CACHE_SHOP_SEARCH_KEY + "*");
        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    @Override
    public void syncShopToEs(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            return;
        }
        ShopDocument document = BeanUtil.copyProperties(shop, ShopDocument.class);
        if (shop.getX() != null && shop.getY() != null) {
            document.setLocation(new GeoPoint(shop.getY(), shop.getX()));
        }
        shopDocumentRepository.save(document);
        evictSearchCache();
        log.info("同步商户到ES: {}", shopId);
    }

    @Override
    public void syncAllShopsToEs() {
        List<Shop> shops = shopMapper.selectList(null);
        List<ShopDocument> documents = shops.stream()
                .map(shop -> {
                    ShopDocument doc = BeanUtil.copyProperties(shop, ShopDocument.class);
                    if (shop.getX() != null && shop.getY() != null) {
                        doc.setLocation(new GeoPoint(shop.getY(), shop.getX()));
                    }
                    return doc;
                })
                .collect(Collectors.toList());
        shopDocumentRepository.saveAll(documents);
        evictSearchCache();
        log.info("全量同步商户到ES: {} 条", documents.size());
    }
}
