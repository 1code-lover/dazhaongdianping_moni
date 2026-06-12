package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.ShopApply;
import com.hmdp.mapper.ShopApplyMapper;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.impl.ShopApplyServiceImpl;
import com.hmdp.utils.UserHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShopApplyServiceTest {

    @Mock
    private ShopApplyMapper shopApplyMapper;
    
    @Mock
    private ShopMapper shopMapper;
    
    @Mock
    private ShopTypeMapper shopTypeMapper;
    
    @InjectMocks
    private ShopApplyServiceImpl shopApplyService;

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

    @Test
    void testSubmitApply_ShopNameEmpty() {
        ShopApply apply = new ShopApply();
        apply.setShopName("");
        
        Result result = shopApplyService.submitApply(apply);
        assertFalse(result.getSuccess());
        assertEquals("店铺名称不能为空", result.getErrorMsg());
    }
    
    @Test
    void testSubmitApply_ContactNameEmpty() {
        ShopApply apply = new ShopApply();
        apply.setShopName("测试餐厅");
        apply.setContactName("");
        
        Result result = shopApplyService.submitApply(apply);
        assertFalse(result.getSuccess());
        assertEquals("联系人姓名不能为空", result.getErrorMsg());
    }
    
    @Test
    void testAuditApply_ApplyNotFound() {
        when(shopApplyMapper.selectById(999L)).thenReturn(null);
        
        Result result = shopApplyService.auditApply(999L, 1, null);
        assertFalse(result.getSuccess());
        assertEquals("申请记录不存在", result.getErrorMsg());
    }
    
    @Test
    void testAuditApply_RejectWithoutReason() {
        ShopApply apply = new ShopApply();
        apply.setId(1L);
        apply.setStatus(0);
        
        when(shopApplyMapper.selectById(1L)).thenReturn(apply);
        
        Result result = shopApplyService.auditApply(1L, 2, null);
        assertFalse(result.getSuccess());
        assertEquals("拒绝原因不能为空", result.getErrorMsg());
    }
    
    @Test
    void testReapply_ApplyNotFound() {
        when(shopApplyMapper.selectById(999L)).thenReturn(null);
        
        ShopApply newApply = new ShopApply();
        Result result = shopApplyService.reapply(999L, newApply);
        assertFalse(result.getSuccess());
        assertEquals("申请记录不存在", result.getErrorMsg());
    }
}
