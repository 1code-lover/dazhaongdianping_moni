package com.hmdp.annotation;

import com.hmdp.enums.LimitType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {

    String keyPrefix();

    int timeWindowSeconds();

    int maxRequests();

    LimitType limitType() default LimitType.IP;

    String message() default "请求过于频繁，请稍后再试";

    int cooldownSeconds() default 0;

    boolean pathKey() default false;
}
