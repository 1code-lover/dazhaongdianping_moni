package com.hmdp.monitor;

import com.hmdp.config.KafkaLagMonitorScheduler;
import com.hmdp.config.SeckillStockReconcileScheduler;
import com.hmdp.service.impl.VoucherOrderServiceImpl;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class MonitoringWiringTest {

    @Test
    void voucherOrderServiceShouldInjectCustomMetricsService() throws NoSuchFieldException {
        assertNotNull(VoucherOrderServiceImpl.class.getDeclaredField("customMetricsService"));
    }

    @Test
    void cacheClientShouldInjectCustomMetricsService() throws NoSuchFieldException {
        assertNotNull(CacheClient.class.getDeclaredField("customMetricsService"));
    }

    @Test
    void kafkaLagMonitorShouldExposeMeterRegistry() throws NoSuchFieldException {
        Field field = KafkaLagMonitorScheduler.class.getDeclaredField("meterRegistry");
        assertNotNull(field);
    }

    @Test
    void stockReconcileShouldExposeMeterRegistry() throws NoSuchFieldException {
        Field field = SeckillStockReconcileScheduler.class.getDeclaredField("meterRegistry");
        assertNotNull(field);
    }
}
