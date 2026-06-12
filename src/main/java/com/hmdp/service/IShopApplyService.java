package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopApply;

public interface IShopApplyService extends IService<ShopApply> {
    
    /**
     * 提交入驻申请
     */
    Result submitApply(ShopApply apply);
    
    /**
     * 查看申请状态
     */
    Result getApplyStatus();
    
    /**
     * 获取申请列表（平台端）
     */
    Result getApplyList(Integer status, Integer current, Integer size);
    
    /**
     * 查看申请详情（平台端）
     */
    Result getApplyDetail(Long id);
    
    /**
     * 审核申请（平台端）
     */
    Result auditApply(Long applyId, Integer status, String rejectReason);
    
    /**
     * 重新申请
     */
    Result reapply(Long applyId, ShopApply apply);
}
