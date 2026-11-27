package com.winlator.cmod.inputcontrols;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class GameProfileManagerTest {
    @Test
    public void identityKeyIsCaseInsensitiveAndStable() {
        String first = GameProfileManager.preferenceKeyForIdentity("D:\\Games\\Example.exe");
        String second = GameProfileManager.preferenceKeyForIdentity("d:/games/example.EXE");
        assertNotNull(first);
        assertEquals(first, second);
    }

    @Test
    public void emptyIdentityHasNoKey() {
        assertEquals(null, GameProfileManager.preferenceKeyForIdentity("  "));
    }
}
