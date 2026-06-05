package com.hmdp.mapper;

import com.hmdp.entity.ShopDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import java.util.List;

public interface ShopDocumentRepository extends ElasticsearchRepository<ShopDocument, Long> {
    
    List<ShopDocument> findByNameOrAddressOrAreaContaining(String name, String address, String area);
}
