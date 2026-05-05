package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.hmdp.utils.CacheClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;
import static com.hmdp.utils.RedisConstants.LOCAL_CACHE_SHOP_TYPE_LIST_KEY;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ObjectMapper objectMapper;

    @Resource
    private CacheClient cacheClient;

    @Override
    public List<ShopType> queryTypeListWithCache() {
        return cacheClient.queryListWithLocalCache(
                cacheClient.getShopTypeLocalCache(),
                LOCAL_CACHE_SHOP_TYPE_LIST_KEY,
                CACHE_SHOP_TYPE_KEY,
                new TypeReference<List<ShopType>>() {},
                () -> query().orderByAsc("sort").list(),
                CACHE_SHOP_TYPE_TTL,
                TimeUnit.MINUTES
        );
    }
}
