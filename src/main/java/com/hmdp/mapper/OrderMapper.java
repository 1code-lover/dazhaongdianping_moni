package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Order;
import org.apache.ibatis.annotations.Mapper;

/**
 * 订单Mapper接口
 * 提供订单表的CRUD操作
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {
}
