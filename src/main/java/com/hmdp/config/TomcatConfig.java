package com.hmdp.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

/**
 * Tomcat 配置
 * 允许 URL 中包含中文字符，解决 ES 搜索中文时报 400 的问题
 *
 * @author ethan
 * @date 2026-07-11
 */
@Configuration
public class TomcatConfig implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        // 允许 URL 查询参数包含特殊字符（包括中文）
        factory.addConnectorCustomizers(connector -> {
            connector.setProperty("relaxedQueryChars", "[]|{}^\\`\"<>");
            connector.setURIEncoding("UTF-8");
        });
    }
}
