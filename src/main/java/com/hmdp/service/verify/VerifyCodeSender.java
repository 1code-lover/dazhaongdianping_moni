package com.hmdp.service.verify;

/**
 * 验证码下发接口
 * 按配置选择具体通道实现（日志 / 邮箱 / 预留短信），便于扩展与替换
 */
public interface VerifyCodeSender {

    /**
     * 当前实现对应的通道类型
     *
     * @return 通道枚举
     */
    VerifyCodeChannel channel();

    /**
     * 发送验证码
     *
     * @param target 接收方（手机号或邮箱）
     * @param code   6 位数字验证码
     * @throws Exception 发送失败时抛出，由调用方统一处理
     */
    void send(String target, String code) throws Exception;
}
