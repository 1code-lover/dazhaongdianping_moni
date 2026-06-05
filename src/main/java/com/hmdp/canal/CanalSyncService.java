package com.hmdp.canal;

import com.hmdp.config.CanalConfig;
import com.hmdp.service.IShopSearchService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

@Slf4j
@Service
@ConditionalOnProperty(prefix = "canal", name = "enabled", havingValue = "true")
public class CanalSyncService {

    @Resource
    private IShopSearchService shopSearchService;
    
    @Resource
    private CanalConfig canalConfig;

    /**
     * 启动Canal增量同步。注意：Canal Server需要单独部署（如Docker），本服务是Spring Boot集成端。
     * startSync()依赖Canal Server运行后才能建立连接并解析binlog；fullSync()可独立执行初始全量数据加载。
     */
    public void startSync() {
        log.info("启动Canal同步服务，连接 {}:{}, destination: {}", 
                canalConfig.getHost(), canalConfig.getPort(), canalConfig.getDestination());
        // TODO: Canal Server is a separate deployment (e.g. Docker). Once running, implement
        //       CanalConnector.connect(), subscribe, and batch binlog parsing loop here.
        //       fullSync() works independently for initial data load without Canal Server.
    }
    
    public void fullSync() {
        log.info("执行全量同步...");
        shopSearchService.syncAllShopsToEs();
    }
}
