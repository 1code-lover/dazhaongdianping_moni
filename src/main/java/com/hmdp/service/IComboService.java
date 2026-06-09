package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Combo;

/**
 * 套餐服务接口
 */
public interface IComboService extends IService<Combo> {
    
    /**
     * 发布套餐
     * @param combo 套餐信息
     * @return 套餐ID
     */
    Result addCombo(Combo combo);
    
    /**
     * 更新套餐
     * @param combo 套餐信息
     * @return 操作结果
     */
    Result updateCombo(Combo combo);
    
    /**
     * 上下架套餐
     * @param id 套餐ID
     * @param status 状态：0下架 1上架
     * @return 操作结果
     */
    Result updateStatus(Long id, Integer status);
    
    /**
     * 查询商户套餐列表
     * @param shopId 商户ID
     * @param current 页码
     * @param size 每页大小
     * @return 套餐列表
     */
    Result listByShopId(Long shopId, Integer current, Integer size);
    
    /**
     * 查询套餐详情
     * @param id 套餐ID
     * @return 套餐详情
     */
    Result queryById(Long id);
}
