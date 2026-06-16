package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopApply;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopApplyMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopApplyService;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

@Slf4j
@Service
public class ShopApplyServiceImpl extends ServiceImpl<ShopApplyMapper, ShopApply> implements IShopApplyService {

    @Resource
    private ShopMapper shopMapper;
    
    @Resource
    private ShopTypeMapper shopTypeMapper;

    @Override
    @Transactional
    public Result submitApply(ShopApply apply) {
        Long userId = UserHolder.getUser().getId();
        
        // 检查是否有待审核的申请
        long count = lambdaQuery()
                .eq(ShopApply::getUserId, userId)
                .eq(ShopApply::getStatus, 0)
                .eq(ShopApply::getIsDeleted, 0)
                .count();
        if (count > 0) {
            return Result.fail("您已有待审核的申请，请等待审核结果");
        }
        
        // 参数校验
        if (StrUtil.isBlank(apply.getShopName())) {
            return Result.fail("店铺名称不能为空");
        }
        if (StrUtil.isBlank(apply.getContactName())) {
            return Result.fail("联系人姓名不能为空");
        }
        if (StrUtil.isBlank(apply.getContactPhone())) {
            return Result.fail("联系电话不能为空");
        }
        if (StrUtil.isBlank(apply.getAddress())) {
            return Result.fail("店铺地址不能为空");
        }
        
        // 校验店铺类型是否合法
        ShopType shopType = shopTypeMapper.selectById(apply.getShopTypeId());
        if (shopType == null) {
            return Result.fail("店铺类型不存在");
        }
        
        // 校验店铺名称是否已被占用
        long shopCount = shopMapper.selectCount(
                new LambdaQueryWrapper<Shop>().eq(Shop::getName, apply.getShopName())
        );
        if (shopCount > 0) {
            return Result.fail("店铺名称已被占用");
        }
        
        // 保存申请
        apply.setUserId(userId);
        apply.setStatus(0);
        apply.setIsDeleted(0);
        apply.setCreateTime(LocalDateTime.now());
        apply.setUpdateTime(LocalDateTime.now());
        save(apply);
        
        log.info("商家入驻申请提交成功: userId={}, shopName={}", userId, apply.getShopName());
        return Result.ok(apply.getId());
    }

    @Override
    public Result getApplyStatus() {
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }
        
        ShopApply apply = lambdaQuery()
                .eq(ShopApply::getUserId, userId)
                .eq(ShopApply::getIsDeleted, 0)
                .orderByDesc(ShopApply::getCreateTime)
                .last("LIMIT 1")
                .one();
        
        if (apply == null) {
            return Result.fail("暂无申请记录");
        }
        
        return Result.ok(apply);
    }

    @Override
    public Result getApplyList(Integer status, Integer current, Integer size) {
        Page<ShopApply> page = lambdaQuery()
                .eq(status != null, ShopApply::getStatus, status)
                .eq(ShopApply::getIsDeleted, 0)
                .orderByDesc(ShopApply::getCreateTime)
                .page(new Page<>(current, size));
        
        return Result.ok(page.getRecords());
    }

    @Override
    public Result getApplyDetail(Long id) {
        ShopApply apply = getById(id);
        if (apply == null || apply.getIsDeleted() == 1) {
            return Result.fail("申请记录不存在");
        }
        return Result.ok(apply);
    }

    @Override
    @Transactional
    public Result auditApply(Long applyId, Integer status, String rejectReason) {
        // 直接用Mapper查询，避免getById的潜在问题
        ShopApply apply = baseMapper.selectById(applyId);
        log.info("审核申请 - applyId: {}, 查询结果: {}", applyId, apply);
        if (apply == null) {
            return Result.fail("申请记录不存在");
        }
        log.info("审核申请 - 当前状态: {}, 目标状态: {}, isDeleted: {}", apply.getStatus(), status, apply.getIsDeleted());
        if (apply.getIsDeleted() != null && apply.getIsDeleted() == 1) {
            return Result.fail("申请记录已删除");
        }
        if (apply.getStatus() != 0) {
            return Result.fail("该申请已审核，当前状态: " + apply.getStatus());
        }
        
        // 更新申请状态
        apply.setStatus(status);
        apply.setAuditTime(LocalDateTime.now());
        // 获取审核人ID（admin接口可能没有登录用户）
        try {
            apply.setAuditorId(UserHolder.getUser().getId());
        } catch (Exception e) {
            log.warn("无法获取审核人ID，设置为系统审核");
            apply.setAuditorId(0L);
        }
        if (status == 2) {
            if (StrUtil.isBlank(rejectReason)) {
                return Result.fail("拒绝原因不能为空");
            }
            apply.setRejectReason(rejectReason);
        }
        updateById(apply);
        
        // 审核通过，创建店铺
        if (status == 1) {
            Shop shop = new Shop();
            shop.setName(apply.getShopName());
            shop.setTypeId(apply.getShopTypeId());
            // images字段不能为空，设置默认值
            shop.setImages(apply.getShopImg() != null ? apply.getShopImg() : "");
            shop.setAddress(apply.getAddress());
            shop.setX(apply.getX() != null ? apply.getX().doubleValue() : null);
            shop.setY(apply.getY() != null ? apply.getY().doubleValue() : null);
            shop.setCreateTime(LocalDateTime.now());
            shop.setUpdateTime(LocalDateTime.now());
            shopMapper.insert(shop);
            
            log.info("商家入驻审核通过，店铺创建成功: shopId={}, shopName={}, userId={}", 
                    shop.getId(), shop.getName(), apply.getUserId());
        }
        
        log.info("商家入驻审核完成: applyId={}, status={}", applyId, status);
        return Result.ok();
    }

    @Override
    @Transactional
    public Result reapply(Long applyId, ShopApply apply) {
        Long userId = UserHolder.getUser().getId();
        
        ShopApply oldApply = getById(applyId);
        if (oldApply == null) {
            return Result.fail("申请记录不存在");
        }
        if (!oldApply.getUserId().equals(userId)) {
            return Result.fail("无权操作");
        }
        if (oldApply.getStatus() != 2) {
            return Result.fail("只有被拒绝的申请才能重新申请");
        }
        
        // 将原记录标记为已删除（保留历史）
        oldApply.setIsDeleted(1);
        oldApply.setUpdateTime(LocalDateTime.now());
        updateById(oldApply);
        
        // 创建新申请记录
        ShopApply newApply = new ShopApply();
        newApply.setUserId(userId);
        newApply.setShopName(apply.getShopName());
        newApply.setShopTypeId(apply.getShopTypeId());
        newApply.setShopImg(apply.getShopImg());
        newApply.setContactName(apply.getContactName());
        newApply.setContactPhone(apply.getContactPhone());
        newApply.setAddress(apply.getAddress());
        newApply.setX(apply.getX());
        newApply.setY(apply.getY());
        newApply.setLicenseNo(apply.getLicenseNo());
        newApply.setLicenseImg(apply.getLicenseImg());
        newApply.setDescription(apply.getDescription());
        newApply.setStatus(0);
        newApply.setIsDeleted(0);
        newApply.setCreateTime(LocalDateTime.now());
        newApply.setUpdateTime(LocalDateTime.now());
        save(newApply);
        
        log.info("商家重新提交入驻申请: oldApplyId={}, newApplyId={}", applyId, newApply.getId());
        return Result.ok(newApply.getId());
    }
}
