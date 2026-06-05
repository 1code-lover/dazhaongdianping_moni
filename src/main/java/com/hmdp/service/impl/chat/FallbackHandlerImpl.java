package com.hmdp.service.impl.chat;

import com.hmdp.enums.IntentType;
import com.hmdp.service.chat.FallbackHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class FallbackHandlerImpl implements FallbackHandler {

    @Override
    public String handleFallback(String userMessage, IntentType intent) {
        log.info("执行降级处理，意图: {}", intent);

        switch (intent) {
            case QUERY_SHOP:
                return "抱歉，智能客服暂时无法查询商户信息。您可以：\n" +
                       "1. 使用搜索功能直接搜索商户\n" +
                       "2. 浏览商户分类列表\n" +
                       "3. 稍后再试";

            case QUERY_COUPON:
                return "抱歉，智能客服暂时无法查询优惠券信息。您可以：\n" +
                       "1. 在商户详情页查看可用优惠券\n" +
                       "2. 访问优惠券专区\n" +
                       "3. 稍后再试";

            case GUIDE:
                return "抱歉，智能客服暂时无法回答使用问题。您可以：\n" +
                       "1. 查看帮助中心\n" +
                       "2. 联系人工客服：400-xxx-xxxx\n" +
                       "3. 稍后再试";

            case CHITCHAT:
            default:
                return "抱歉，智能客服暂时无法回复。您可以：\n" +
                       "1. 提问关于商户、优惠券、使用指南的问题\n" +
                       "2. 联系人工客服：400-xxx-xxxx\n" +
                       "3. 稍后再试";
        }
    }
}
