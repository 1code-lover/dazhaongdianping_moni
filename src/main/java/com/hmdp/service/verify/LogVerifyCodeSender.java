package com.hmdp.service.verify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 日志验证码发送器（默认通道）
 * 不接入真实下发平台时，验证码输出到日志并由 Redis 暂存，便于本地/演示环境联调
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.verify-code.channel", havingValue = "log", matchIfMissing = true)
public class LogVerifyCodeSender implements VerifyCodeSender {

    /**
     * 当前实现对应的通道类型
     *
     * @return 通道枚举
     */
    @Override
    public VerifyCodeChannel channel() {
        return VerifyCodeChannel.PHONE;
    }

    /**
     * 发送验证码（仅打印日志，不实际下发）
     *
     * @param target 接收方（手机号或邮箱）
     * @param code   6 位数字验证码
     */
    @Override
    public void send(String target, String code) {
        log.info("【验证码】target={}，code={}（log通道：请从日志或 Redis login:{} 获取）", target, code, target);
    }
}
