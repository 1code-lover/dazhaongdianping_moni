package com.hmdp.service;

import com.hmdp.dto.Result;

public interface IShopSearchService {
    
    Result searchByKeyword(String keyword, Integer page, Integer size);
    
    void syncShopToEs(Long shopId);
    
    void syncAllShopsToEs();
}
