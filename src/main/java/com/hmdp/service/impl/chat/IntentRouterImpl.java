package com.hmdp.service.impl.chat;

import com.hmdp.enums.IntentType;
import com.hmdp.service.chat.IntentRouter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class IntentRouterImpl implements IntentRouter {

    private static final Map<String, IntentType> KEYWORD_MAP = new HashMap<>();

    static {
        KEYWORD_MAP.put("餐厅", IntentType.QUERY_SHOP);
        KEYWORD_MAP.put("美食", IntentType.QUERY_SHOP);
        KEYWORD_MAP.put("好吃", IntentType.QUERY_SHOP);
        KEYWORD_MAP.put("推荐", IntentType.QUERY_SHOP);
        KEYWORD_MAP.put("附近", IntentType.QUERY_SHOP);

        KEYWORD_MAP.put("优惠券", IntentType.QUERY_COUPON);
        KEYWORD_MAP.put("折扣", IntentType.QUERY_COUPON);
        KEYWORD_MAP.put("秒杀", IntentType.QUERY_COUPON);
        KEYWORD_MAP.put("优惠", IntentType.QUERY_COUPON);

        KEYWORD_MAP.put("怎么", IntentType.GUIDE);
        KEYWORD_MAP.put("如何", IntentType.GUIDE);
        KEYWORD_MAP.put("帮助", IntentType.GUIDE);
        KEYWORD_MAP.put("使用", IntentType.GUIDE);
    }

    @Override
    public IntentType classify(String userMessage) {
        for (Map.Entry<String, IntentType> entry : KEYWORD_MAP.entrySet()) {
            if (userMessage.contains(entry.getKey())) {
                log.debug("关键词匹配成功: {} -> {}", entry.getKey(), entry.getValue());
                return entry.getValue();
            }
        }
        return IntentType.CHITCHAT;
    }
}
