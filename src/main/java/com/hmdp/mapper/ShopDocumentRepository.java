package com.hmdp.mapper;

import com.hmdp.entity.ShopDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ShopDocumentRepository extends ElasticsearchRepository<ShopDocument, Long> {
}
