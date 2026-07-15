package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Review;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 评价Mapper接口
 */
@Mapper
public interface ReviewMapper extends BaseMapper<Review> {

    /**
     * 更新商户评分
     * 使用SQL原子更新，避免并发问题
     *
     * @param shopId 商户ID
     */
    @Update("UPDATE tb_shop SET avg_score = (SELECT AVG(score) FROM tb_review WHERE shop_id = #{shopId} AND is_deleted = 0), review_count = (SELECT COUNT(*) FROM tb_review WHERE shop_id = #{shopId} AND is_deleted = 0) WHERE id = #{shopId}")
    void updateShopScore(@Param("shopId") Long shopId);
}
