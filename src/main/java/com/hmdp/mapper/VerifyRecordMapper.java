package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.VerifyRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 核销记录Mapper接口
 * 提供核销记录表的CRUD操作
 */
@Mapper
public interface VerifyRecordMapper extends BaseMapper<VerifyRecord> {
}
