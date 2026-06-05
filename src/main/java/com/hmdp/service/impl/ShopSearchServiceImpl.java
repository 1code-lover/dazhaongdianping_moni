package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopDocument;
import com.hmdp.mapper.ShopDocumentRepository;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import static org.elasticsearch.index.query.QueryBuilders.matchQuery;

@Slf4j
@Service
public class ShopSearchServiceImpl implements IShopSearchService {

    @Resource
    private ShopDocumentRepository shopDocumentRepository;
    
    @Resource
    private ShopMapper shopMapper;
    
    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Override
    public Result searchByKeyword(String keyword, Integer page, Integer size) {
        NativeSearchQuery query = new NativeSearchQueryBuilder()
                .withQuery(matchQuery("name", keyword).boost(2.0f)
                        .or(matchQuery("address", keyword))
                        .or(matchQuery("area", keyword)))
                .withPageable(PageRequest.of(page - 1, size))
                .build();
        
        SearchHits<ShopDocument> hits = elasticsearchRestTemplate.search(query, ShopDocument.class);
        
        List<ShopDocument> shops = hits.getSearchHits().stream()
                .map(hit -> hit.getContent())
                .collect(Collectors.toList());
        
        return Result.ok(shops);
    }

    @Override
    public void syncShopToEs(Long shopId) {
        Shop shop = shopMapper.selectById(shopId);
        if (shop == null) {
            return;
        }
        
        ShopDocument document = BeanUtil.copyProperties(shop, ShopDocument.class);
        shopDocumentRepository.save(document);
        log.info("同步商户到ES: {}", shopId);
    }

    @Override
    public void syncAllShopsToEs() {
        List<Shop> shops = shopMapper.selectList(null);
        List<ShopDocument> documents = BeanUtil.copyToList(shops, ShopDocument.class);
        shopDocumentRepository.saveAll(documents);
        log.info("全量同步商户到ES: {} 条", documents.size());
    }
}
