package com.hmdp.mail;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 邮件去重器测试。
 *
 * @author ethan
 * @date 2026-10-31
 */
class MailAlertDeduperTest {

    private final MailAlertDeduper deduper = new MailAlertDeduper();

    @Test
    void testTryRegisterFirstTimeReturnsTrue() {
        assertTrue(deduper.tryRegister("mail-001"));
    }

    @Test
    void testTryRegisterDuplicateKeyReturnsFalse() {
        deduper.tryRegister("mail-001");
        assertFalse(deduper.tryRegister("mail-001"));
    }

    @Test
    void testTryRegisterDifferentKeysBothPass() {
        assertTrue(deduper.tryRegister("mail-001"));
        assertTrue(deduper.tryRegister("mail-002"));
        assertEquals(2, deduper.size());
    }

    @Test
    void testTryRegisterNullKeyAlwaysPasses() {
        assertTrue(deduper.tryRegister(null));
        assertTrue(deduper.tryRegister(null));
    }

    @Test
    void testTryRegisterBlankKeyAlwaysPasses() {
        assertTrue(deduper.tryRegister("   "));
        assertTrue(deduper.tryRegister("   "));
    }

    @Test
    void testResetClearsSeenKeys() {
        deduper.tryRegister("mail-001");
        deduper.reset();
        assertEquals(0, deduper.size());
        assertTrue(deduper.tryRegister("mail-001"));
    }
}
