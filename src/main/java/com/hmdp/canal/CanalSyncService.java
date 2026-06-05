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

    public void startSync() {
        log.info("启动Canal同步服务，连接 {}:{}, destination: {}", 
                canalConfig.getHost(), canalConfig.getPort(), canalConfig.getDestination());
        // TODO: 实现Canal Client连接和binlog解析
    }
    
    public void fullSync() {
        log.info("执行全量同步...");
        shopSearchService.syncAllShopsToEs();
    }
}
