package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Combo;
import org.apache.ibatis.annotations.Mapper;

/**
 * 套餐Mapper接口
 * 提供套餐表的CRUD操作
 */
@Mapper
public interface ComboMapper extends BaseMapper<Combo> {
}
