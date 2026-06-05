package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.ShopDocument;
import com.hmdp.mapper.ShopDocumentRepository;
import com.hmdp.service.impl.ShopSearchServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHits;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopSearchServiceTest {

    @Mock
    private ShopDocumentRepository shopDocumentRepository;
    
    @Mock
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @InjectMocks
    private ShopSearchServiceImpl shopSearchService;

    @Test
    void testSearchByKeyword() {
        when(elasticsearchRestTemplate.search(any(), any(Class.class)))
                .thenReturn(SearchHits.empty(0));
        
        Result result = shopSearchService.searchByKeyword("餐厅", 1, 10, null, null);
        assertNotNull(result);
    }
    
    @Test
    void testSearchByKeywordWithGeo() {
        when(elasticsearchRestTemplate.search(any(), any(Class.class)))
                .thenReturn(SearchHits.empty(0));
        
        Result result = shopSearchService.searchByKeyword("餐厅", 1, 10, 120.15, 30.32);
        assertNotNull(result);
    }
}
