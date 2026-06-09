package com.hmdp.service;

import com.hmdp.dto.Result;

/**
 * 核销服务接口
 */
public interface IVerifyService {
    
    /**
     * 核销订单
     * @param verifyCode 核销码
     * @return 核销结果
     */
    Result verify(String verifyCode);
    
    /**
     * 验证核销码有效性
     * @param verifyCode 核销码
     * @return 是否有效
     */
    Result checkVerifyCode(String verifyCode);
    
    /**
     * 查询核销记录
     * @param shopId 商户ID
     * @param current 页码
     * @param size 每页大小
     * @return 核销记录列表
     */
    Result queryVerifyRecords(Long shopId, Integer current, Integer size);
}
