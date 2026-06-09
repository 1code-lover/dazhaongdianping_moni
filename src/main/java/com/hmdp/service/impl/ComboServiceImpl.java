package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Combo;
import com.hmdp.mapper.ComboMapper;
import com.hmdp.service.IComboService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 套餐服务实现类
 */
@Slf4j
@Service
public class ComboServiceImpl extends ServiceImpl<ComboMapper, Combo> implements IComboService {

    /**
     * 发布套餐
     * 1. 参数校验
     * 2. 标题唯一性校验（同一商户下）
     * 3. 保存套餐
     */
    @Override
    @Transactional
    public Result addCombo(Combo combo) {
        // 1. 参数校验
        if (combo.getShopId() == null || StrUtil.isBlank(combo.getTitle())) {
            return Result.fail("参数不完整");
        }
        if (combo.getPrice() == null || combo.getPrice() <= 0) {
            return Result.fail("价格不合法");
        }
        if (combo.getStock() == null || combo.getStock() < 0) {
            return Result.fail("库存不合法");
        }
        
        // 2. 标题唯一性校验（同一商户下）
        int count = lambdaQuery()
                .eq(Combo::getShopId, combo.getShopId())
                .eq(Combo::getTitle, combo.getTitle())
                .eq(Combo::getIsDeleted, 0)
                .count();
        if (count > 0) {
            return Result.fail("该商户下已存在同名套餐");
        }
        
        // 3. 设置默认值
        if (combo.getSales() == null) {
            combo.setSales(0);
        }
        if (combo.getStatus() == null) {
            combo.setStatus(1);
        }
        combo.setIsDeleted(0);
        combo.setCreateTime(LocalDateTime.now());
        combo.setUpdateTime(LocalDateTime.now());
        
        // 4. 保存套餐
        save(combo);
        log.info("发布套餐成功: shopId={}, comboId={}, title={}", combo.getShopId(), combo.getId(), combo.getTitle());
        return Result.ok(combo.getId());
    }

    /**
     * 更新套餐
     * 1. 校验套餐是否存在
     * 2. 更新套餐信息
     */
    @Override
    @Transactional
    public Result updateCombo(Combo combo) {
        if (combo.getId() == null) {
            return Result.fail("套餐ID不能为空");
        }
        
        // 校验套餐是否存在
        Combo existing = getById(combo.getId());
        if (existing == null || existing.getIsDeleted() == 1) {
            return Result.fail("套餐不存在");
        }
        
        combo.setUpdateTime(LocalDateTime.now());
        updateById(combo);
        log.info("更新套餐成功: comboId={}", combo.getId());
        return Result.ok();
    }

    /**
     * 上下架套餐
     */
    @Override
    @Transactional
    public Result updateStatus(Long id, Integer status) {
        if (id == null || status == null) {
            return Result.fail("参数不完整");
        }
        
        Combo combo = getById(id);
        if (combo == null || combo.getIsDeleted() == 1) {
            return Result.fail("套餐不存在");
        }
        
        combo.setStatus(status);
        combo.setUpdateTime(LocalDateTime.now());
        updateById(combo);
        log.info("更新套餐状态: comboId={}, status={}", id, status);
        return Result.ok();
    }

    /**
     * 查询商户套餐列表（只返回上架且未删除的套餐）
     */
    @Override
    public Result listByShopId(Long shopId, Integer current, Integer size) {
        Page<Combo> page = lambdaQuery()
                .eq(Combo::getShopId, shopId)
                .eq(Combo::getStatus, 1)
                .eq(Combo::getIsDeleted, 0)
                .orderByDesc(Combo::getCreateTime)
                .page(new Page<>(current, size));
        return Result.ok(page.getRecords());
    }

    /**
     * 查询套餐详情
     */
    @Override
    public Result queryById(Long id) {
        Combo combo = getById(id);
        if (combo == null || combo.getIsDeleted() == 1) {
            return Result.fail("套餐不存在");
        }
        return Result.ok(combo);
    }
}
