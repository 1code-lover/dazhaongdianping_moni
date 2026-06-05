package com.hmdp.service;

import com.hmdp.dto.Result;

public interface IShopSearchService {
    Result searchByKeyword(String keyword, Integer page, Integer size, Double x, Double y);
    void syncShopToEs(Long shopId);
    void syncAllShopsToEs();
}
