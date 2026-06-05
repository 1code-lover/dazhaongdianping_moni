package com.hmdp.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum IntentType {
    QUERY_SHOP("QUERY_SHOP", "查询商户"),
    QUERY_COUPON("QUERY_COUPON", "查询优惠券"),
    GUIDE("GUIDE", "使用指南"),
    CHITCHAT("CHITCHAT", "闲聊");
    
    private final String code;
    private final String desc;
}
