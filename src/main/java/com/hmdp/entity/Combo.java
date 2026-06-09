package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 套餐实体类
 * 对应数据库表：tb_combo
 */
@Data
@TableName("tb_combo")
public class Combo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    
    /**
     * 商户ID
     */
    private Long shopId;
    
    /**
     * 套餐标题
     */
    private String title;
    
    /**
     * 副标题
     */
    private String subTitle;
    
    /**
     * 封面图URL
     */
    private String cover;
    
    /**
     * 详情图片JSON数组
     */
    private String images;
    
    /**
     * 原价（单位：分）
     */
    private Long originalPrice;
    
    /**
     * 团购价（单位：分）
     */
    private Long price;
    
    /**
     * 包含内容描述
     */
    private String content;
    
    /**
     * 使用规则
     */
    private String rules;
    
    /**
     * 库存数量
     */
    private Integer stock;
    
    /**
     * 已售数量
     */
    private Integer sales;
    
    /**
     * 状态：0下架 1上架
     */
    private Integer status;
    
    /**
     * 逻辑删除：0未删除 1已删除
     */
    private Integer isDeleted;
    
    /**
     * 生效时间
     */
    private LocalDateTime beginTime;
    
    /**
     * 失效时间
     */
    private LocalDateTime endTime;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
