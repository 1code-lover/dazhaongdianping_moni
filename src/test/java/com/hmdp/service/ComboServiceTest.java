package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Combo;
import com.hmdp.mapper.ComboMapper;
import com.hmdp.service.impl.ComboServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 套餐服务测试
 */
@ExtendWith(MockitoExtension.class)
class ComboServiceTest {

    @Mock
    private ComboMapper comboMapper;
    
    @InjectMocks
    private ComboServiceImpl comboService;

    /**
     * 测试发布套餐成功
     */
    @Test
    void testAddCombo_Success() {
        Combo combo = new Combo();
        combo.setShopId(1L);
        combo.setTitle("测试套餐");
        combo.setPrice(10000L);
        combo.setStock(10);
        
        when(comboMapper.selectCount(any())).thenReturn(0);
        when(comboMapper.insert(any())).thenReturn(1);
        
        Result result = comboService.addCombo(combo);
        assertTrue(result.getSuccess());
    }
    
    /**
     * 测试发布套餐-标题重复
     */
    @Test
    void testAddCombo_TitleDuplicate() {
        Combo combo = new Combo();
        combo.setShopId(1L);
        combo.setTitle("已存在的套餐");
        combo.setPrice(10000L);
        combo.setStock(10);
        
        when(comboMapper.selectCount(any())).thenReturn(1);
        
        Result result = comboService.addCombo(combo);
        assertFalse(result.getSuccess());
        assertEquals("该商户下已存在同名套餐", result.getErrorMsg());
    }
    
    /**
     * 测试发布套餐-价格不合法
     */
    @Test
    void testAddCombo_InvalidPrice() {
        Combo combo = new Combo();
        combo.setShopId(1L);
        combo.setTitle("测试套餐");
        combo.setPrice(-100L);
        combo.setStock(10);
        
        Result result = comboService.addCombo(combo);
        assertFalse(result.getSuccess());
    }
    
    /**
     * 测试发布套餐-库存不合法
     */
    @Test
    void testAddCombo_InvalidStock() {
        Combo combo = new Combo();
        combo.setShopId(1L);
        combo.setTitle("测试套餐");
        combo.setPrice(10000L);
        combo.setStock(-1);
        
        Result result = comboService.addCombo(combo);
        assertFalse(result.getSuccess());
    }
}
