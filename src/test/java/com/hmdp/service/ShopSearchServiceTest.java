package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopDocument;
import com.hmdp.mapper.ShopDocumentRepository;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.impl.ShopSearchServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopSearchServiceTest {

    @Mock
    private ShopDocumentRepository shopDocumentRepository;
    
    @Mock
    private ShopMapper shopMapper;
    
    @Mock
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @InjectMocks
    private ShopSearchServiceImpl shopSearchService;

    @Test
    void testSearchByKeyword() {
        // Given
        ShopDocument shop = new ShopDocument();
        shop.setId(1L);
        shop.setName("测试餐厅");
        shop.setAddress("测试地址");
        
        SearchHit<ShopDocument> searchHit = new SearchHit<>(
                "1", "1", null, 0.0f, null, null, null, null, null, null, shop);
        SearchHits<ShopDocument> hits = new SearchHits<>(
                1, null, null, null, null, Collections.singletonList(searchHit));
        
        when(elasticsearchRestTemplate.search(any(), any(Class.class)))
                .thenReturn(hits);
        
        // When
        Result result = shopSearchService.searchByKeyword("餐厅", 1, 10, null, null);
        
        // Then
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
    }
    
    @Test
    void testSearchByKeywordWithGeo() {
        // Given
        when(elasticsearchRestTemplate.search(any(), any(Class.class)))
                .thenReturn(SearchHits.empty(0));
        
        // When
        Result result = shopSearchService.searchByKeyword("餐厅", 1, 10, 120.15, 30.32);
        
        // Then
        assertNotNull(result);
        assertEquals(200, result.getCode());
    }
    
    @Test
    void testSearchByKeywordNoResult() {
        // Given
        when(elasticsearchRestTemplate.search(any(), any(Class.class)))
                .thenReturn(SearchHits.empty(0));
        
        // When
        Result result = shopSearchService.searchByKeyword("不存在的餐厅", 1, 10, null, null);
        
        // Then
        assertNotNull(result);
        assertEquals(200, result.getCode());
    }
}
