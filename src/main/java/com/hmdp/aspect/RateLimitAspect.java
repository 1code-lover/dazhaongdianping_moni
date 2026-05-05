package com.hmdp.aspect;

import com.hmdp.annotation.RateLimit;
import com.hmdp.exception.RateLimitException;
import com.hmdp.service.RateLimitService;
import com.hmdp.utils.RateLimitKeyResolver;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@Slf4j
@Aspect
@Component
public class RateLimitAspect {

    @Resource
    private RateLimitService rateLimitService;

    @Resource
    private RateLimitKeyResolver rateLimitKeyResolver;

    @Around("@annotation(rateLimit)")
    public Object doRateLimit(ProceedingJoinPoint joinPoint, RateLimit rateLimit) throws Throwable {
        HttpServletRequest request = getCurrentRequest();
        String suffix = rateLimitKeyResolver.resolveKeySuffix(rateLimit.limitType(), joinPoint.getArgs(), request);
        String countKey = buildCountKey(rateLimit.keyPrefix(), rateLimit.pathKey() ? request.getRequestURI() : "", suffix);

        if (rateLimit.cooldownSeconds() > 0) {
            String cooldownKey = "rl:cooldown:" + rateLimit.keyPrefix() + ":" + suffix;
            boolean cooldownAllowed = rateLimitService.tryAcquireCooldown(cooldownKey, rateLimit.cooldownSeconds());
            if (!cooldownAllowed) {
                log.warn("Rate limit cooldown hit, uri={}, key={}", request.getRequestURI(), cooldownKey);
                throw new RateLimitException(rateLimit.message());
            }
        }

        boolean allowed = rateLimitService.isAllowed(countKey, rateLimit.timeWindowSeconds(), rateLimit.maxRequests());
        if (!allowed) {
            Long currentCount = rateLimitService.getCurrentCount(countKey);
            log.warn("Rate limit hit, uri={}, key={}, count={}, window={}s, max={}",
                    request.getRequestURI(), countKey, currentCount, rateLimit.timeWindowSeconds(), rateLimit.maxRequests());
            throw new RateLimitException(rateLimit.message());
        }
        return joinPoint.proceed();
    }

    private HttpServletRequest getCurrentRequest() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (!(requestAttributes instanceof ServletRequestAttributes)) {
            throw new IllegalStateException("当前请求上下文不存在，无法执行限流");
        }
        return ((ServletRequestAttributes) requestAttributes).getRequest();
    }

    private String buildCountKey(String keyPrefix, String path, String suffix) {
        String normalizedPath = path == null ? "" : path.replace("/", ":");
        if (normalizedPath.startsWith(":")) {
            normalizedPath = normalizedPath.substring(1);
        }
        // 统一 key 结构，方便后续排查某类规则的命中情况。
        if (normalizedPath.isEmpty()) {
            return "rl:" + keyPrefix + ":" + suffix;
        }
        return "rl:" + keyPrefix + ":" + normalizedPath + ":" + suffix;
    }
}
