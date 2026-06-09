package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Combo;
import com.hmdp.entity.Order;
import com.hmdp.mapper.OrderMapper;
import com.hmdp.mapper.VerifyRecordMapper;
import com.hmdp.service.IComboService;
import com.hmdp.service.impl.OrderServiceImpl;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 订单服务测试
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private IComboService comboService;
    
    @Mock
    private OrderMapper orderMapper;
    
    @Mock
    private VerifyRecordMapper verifyRecordMapper;
    
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    
    @Mock
    private ValueOperations valueOperations;
    
    @InjectMocks
    private OrderServiceImpl orderService;

    @BeforeEach
    void setUp() {
        UserDTO user = new UserDTO();
        user.setId(1L);
        UserHolder.saveUser(user);
    }
    
    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    /**
     * 测试创建订单-套餐不存在
     */
    @Test
    void testCreateOrder_ComboNotFound() {
        when(comboService.getById(999L)).thenReturn(null);
        
        Result result = orderService.createOrder(2, 999L, 1);
        assertFalse(result.getSuccess());
        assertEquals("套餐不存在或已下架", result.getErrorMsg());
    }
    
    /**
     * 测试创建订单-套餐已下架
     */
    @Test
    void testCreateOrder_ComboDisabled() {
        Combo combo = new Combo();
        combo.setId(1L);
        combo.setStatus(0); // 已下架
        
        when(comboService.getById(1L)).thenReturn(combo);
        
        Result result = orderService.createOrder(2, 1L, 1);
        assertFalse(result.getSuccess());
        assertEquals("套餐不存在或已下架", result.getErrorMsg());
    }
    
    /**
     * 测试创建订单-不支持的订单类型
     */
    @Test
    void testCreateOrder_UnsupportedType() {
        Result result = orderService.createOrder(99, 1L, 1);
        assertFalse(result.getSuccess());
        assertEquals("暂不支持该订单类型", result.getErrorMsg());
    }
}
