package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.service.IUserService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@SpringBootTest
public class BatchTokenGeneratorTest {

    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 批量写入 Redis 登录态并输出 token 列表，供 JMeter / 脚本压测。
     * <p>
     * 数量建议：不超过「券库存 + 你希望模拟的并发用户数」太多即可；过大只会拉长首次建用户时间、占 Redis。
     * JVM 参数（可选）：
     * <ul>
     *   <li>{@code -Dbatch.token.count=5000} 生成条数，默认 1000</li>
     *   <li>{@code -Dbatch.token.output=tokens.txt} 输出文件路径</li>
     *   <li>{@code -Dbatch.token.beginPhone=13900000000} 起始手机号（连续递增）</li>
     * </ul>
     */
    @Test
    void generateTokensForJmeter() throws IOException {
        int userCount = Integer.getInteger("batch.token.count", 3000);
        if (userCount < 1 || userCount > 50_000) {
            throw new IllegalArgumentException("batch.token.count 建议 1~50000，当前: " + userCount);
        }
        long beginPhone = Long.parseLong(System.getProperty("batch.token.beginPhone", "13900000000"));
        Path outputPath = Paths.get(System.getProperty("batch.token.output", "tokens.txt"));

        List<User> users = prepareUsers(userCount, beginPhone);
        List<String> tokenLines = new ArrayList<>(users.size());

        for (User user : users) {
            String token = UUID.randomUUID().toString(true);
            UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
            Map<String, Object> userMap = BeanUtil.beanToMap(
                    userDTO,
                    new HashMap<>(),
                    CopyOptions.create()
                            .setIgnoreNullValue(true)
                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue == null ? null : fieldValue.toString())
            );

            String tokenKey = LOGIN_USER_KEY + token;
            stringRedisTemplate.opsForHash().putAll(tokenKey, userMap);
            stringRedisTemplate.expire(tokenKey, LOGIN_USER_TTL, TimeUnit.MINUTES);

            tokenLines.add(token);
        }

        writeTokens(outputPath, tokenLines);
        System.out.println("Generated " + tokenLines.size() + " tokens to: " + outputPath.toAbsolutePath());
    }

    private List<User> prepareUsers(int userCount, long beginPhone) {
        List<User> users = new ArrayList<>(userCount);
        for (int i = 0; i < userCount; i++) {
            String phone = String.valueOf(beginPhone + i);
            User user = userService.query().eq("phone", phone).one();
            if (user == null) {
                user = new User();
                user.setPhone(phone);
                user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
                userService.save(user);
            }
            users.add(user);
        }
        return users;
    }

    private void writeTokens(Path outputPath, List<String> tokenLines) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputPath, StandardCharsets.UTF_8)) {
            for (String tokenLine : tokenLines) {
                writer.write(tokenLine);
                writer.newLine();
            }
        }
    }
}
