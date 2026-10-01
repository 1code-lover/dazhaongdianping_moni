package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.config.VerifyCodeProperties;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.service.verify.VerifyCodeChannel;
import com.hmdp.service.verify.VerifyCodeSender;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private VerifyCodeProperties verifyCodeProperties;

    @Resource
    private VerifyCodeSender verifyCodeSender;

    /**
     * 发送登录验证码
     * 流程：识别通道 → 单日/IP 限流 → 重发冷却 → 生成验证码 → 通道下发 → 存 Redis
     *
     * @param target 登录标识（手机号或邮箱）
     * @param ip     请求来源 IP（用于单日发送上限控制）
     * @return 发送结果
     */
    @Override
    public Result sendCode(String target, String ip) {
        // 1.识别通道并校验格式
        VerifyCodeChannel channel = VerifyCodeChannel.detect(target);
        if (channel == null) {
            return Result.fail("手机号或邮箱格式错误！");
        }
        // 2.单日发送上限：登录标识维度
        if (overDailyLimit(VERIFY_DAY_KEY + target, verifyCodeProperties.getMaxPerTargetPerDay())) {
            return Result.fail("该账号今日验证码发送次数已达上限，请明天再试");
        }
        // 3.单日发送上限：IP 维度（防轰炸）
        if (StrUtil.isNotBlank(ip) && overDailyLimit(VERIFY_DAY_IP_KEY + ip, verifyCodeProperties.getMaxPerIpPerDay())) {
            return Result.fail("当前网络今日验证码发送次数已达上限，请明天再试");
        }
        // 4.重发冷却：SETNX 占位，已存在说明冷却期内
        String resendKey = VERIFY_RESEND_KEY + target;
        Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(resendKey, "1", verifyCodeProperties.getResendIntervalSeconds(), TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(acquired)) {
            return Result.fail("发送过于频繁，请 " + verifyCodeProperties.getResendIntervalSeconds() + " 秒后再试");
        }
        // 5.生成 6 位数字验证码
        String code = RandomUtil.randomNumbers(6);
        // 6.通道下发；不支持或失败时清除冷却键，允许用户修正后立即重试
        if (verifyCodeSender.channel() == VerifyCodeChannel.EMAIL && channel == VerifyCodeChannel.PHONE) {
            stringRedisTemplate.delete(resendKey);
            return Result.fail("暂未接入短信通道，请使用邮箱获取验证码");
        }
        try {
            verifyCodeSender.send(target, code);
        } catch (Exception e) {
            stringRedisTemplate.delete(resendKey);
            log.error("验证码发送失败，target={}", target, e);
            return Result.fail("验证码发送失败，请稍后重试");
        }
        // 7.保存验证码到 Redis（2 分钟有效），覆盖旧码，并清除历史错误计数
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + target, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);
        stringRedisTemplate.delete(VERIFY_ATTEMPT_KEY + target);
        return Result.ok();
    }

    /**
     * 判断单日计数是否超出上限（自增并设置 24 小时过期）
     *
     * @param key       Redis 计数键
     * @param maxCount  单日上限
     * @return true=已超限
     */
    private boolean overDailyLimit(String key, int maxCount) {
        Long count = stringRedisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            // 首次计数时设置 24 小时过期
            stringRedisTemplate.expire(key, VERIFY_DAY_TTL, TimeUnit.SECONDS);
        }
        return count != null && count > maxCount;
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        // 1.取登录标识并识别通道（target 优先，兼容旧 phone 字段）
        String target = StrUtil.isNotBlank(loginForm.getTarget()) ? loginForm.getTarget() : loginForm.getPhone();
        VerifyCodeChannel channel = VerifyCodeChannel.detect(target);
        if (channel == null) {
            return Result.fail("手机号或邮箱格式错误！");
        }
        // 2.校验验证码格式
        String code = loginForm.getCode();
        if (RegexUtils.isCodeInvalid(code)) {
            return Result.fail("验证码格式错误");
        }
        // 3.从 Redis 获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + target);
        if (cacheCode == null) {
            return Result.fail("验证码已过期，请重新获取");
        }
        if (!cacheCode.equals(code)) {
            // 3.1 不一致：累计错误次数，达到上限后作废验证码（防枚举爆破）
            String attemptKey = VERIFY_ATTEMPT_KEY + target;
            Long attempts = stringRedisTemplate.opsForValue().increment(attemptKey);
            if (attempts != null && attempts == 1L) {
                // 错误计数与验证码同生命周期（2 分钟）
                stringRedisTemplate.expire(attemptKey, LOGIN_CODE_TTL, TimeUnit.MINUTES);
            }
            if (attempts != null && attempts >= verifyCodeProperties.getMaxVerifyAttempts()) {
                stringRedisTemplate.delete(LOGIN_CODE_KEY + target);
                stringRedisTemplate.delete(attemptKey);
                return Result.fail("错误次数过多，验证码已失效，请重新获取");
            }
            return Result.fail("验证码错误");
        }

        // 4.验证通过：验证码一次性使用，立即删除码与错误计数
        stringRedisTemplate.delete(LOGIN_CODE_KEY + target);
        stringRedisTemplate.delete(VERIFY_ATTEMPT_KEY + target);

        // 5.根据通道查询用户（手机号或邮箱）
        User user = channel == VerifyCodeChannel.EMAIL
                ? query().eq("email", target).one()
                : query().eq("phone", target).one();

        // 6.判断用户是否存在
        if (user == null) {
            // 7.不存在，按对应通道创建新用户并保存
            user = createUser(target, channel);
        }

        // 7.保存用户信息到 redis中
        // 7.1.随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        // 7.2.将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 7.3.存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
        // 7.4.设置token有效期
        stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 8.返回token
        return Result.ok(token);
    }

    @Override
    public Result sign() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.写入Redis SETBIT key offset 1
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    @Override
    public Result signCount() {
        // 1.获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.获取日期
        LocalDateTime now = LocalDateTime.now();
        // 3.拼接key
        String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        String key = USER_SIGN_KEY + userId + keySuffix;
        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 5.获取本月截止今天为止的所有的签到记录，返回的是一个十进制的数字 BITFIELD sign:5:202203 GET u14 0
        List<Long> result = stringRedisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );
        if (result == null || result.isEmpty()) {
            // 没有任何签到结果
            return Result.ok(0);
        }
        Long num = result.get(0);
        if (num == null || num == 0) {
            return Result.ok(0);
        }
        // 6.循环遍历
        int count = 0;
        while (true) {
            // 6.1.让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((num & 1) == 0) {
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
            // 把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            num >>>= 1;
        }
        return Result.ok(count);
    }

    /**
     * 按登录通道创建新用户
     * 手机号通道填写 phone；邮箱通道填写 email
     *
     * @param target  登录标识（手机号或邮箱）
     * @param channel 登录通道
     * @return 创建后的用户
     */
    private User createUser(String target, VerifyCodeChannel channel) {
        // 1.创建用户
        User user = new User();
        if (channel == VerifyCodeChannel.EMAIL) {
            user.setEmail(target);
        } else {
            user.setPhone(target);
        }
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        // 2.保存用户
        save(user);
        return user;
    }
}
