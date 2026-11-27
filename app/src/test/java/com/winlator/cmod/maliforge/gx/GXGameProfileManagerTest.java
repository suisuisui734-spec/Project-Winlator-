package com.winlator.cmod.maliforge.gx;

import org.junit.Test;
import static org.junit.Assert.*;

public class GXGameProfileManagerTest {
    private GXGameProfile profile(int scale){return new GXGameProfile(GXGameProfile.Mode.CUSTOM,GXGameProfile.BackendPreference.KEEP_CURRENT,60,scale,true,true,true);}
    @Test public void gameAndContainerProfileVariantsStayIsolated(){
        GXGameProfileManager m=new GXGameProfileManager();
        assertTrue(m.save(1,"Game.exe","balanced",profile(85)).valid);
        assertTrue(m.save(1,"game.exe","compat",profile(70)).valid);
        assertTrue(m.save(2,"game.exe","balanced",profile(95)).valid);
        assertEquals(3,m.profileCount());
        assertEquals(85,m.load(1,"GAME.EXE","balanced").profile().getRenderScalePercent());
        assertEquals(70,m.load(1,"game.exe","compat").profile().getRenderScalePercent());
        assertEquals(95,m.load(2,"game.exe","balanced").profile().getRenderScalePercent());
    }
    @Test public void importExportChecksChecksumAndPayload(){
        GXGameProfileManager source=new GXGameProfileManager();source.save(4,"demo.exe","stable",profile(90));
        String text=source.exportProfile(4,"demo.exe","stable");
        GXGameProfileManager target=new GXGameProfileManager();
        assertNotNull(target.importProfile(text));
        assertEquals(90,target.load(4,"demo.exe","stable").profile().getRenderScalePercent());
        assertNull(target.importProfile(text+"tamper"));assertNull(target.importProfile("invalid"));
    }
    @Test public void lastKnownGoodSnapshotCanRestoreAfterEditing(){
        GXGameProfileManager m=new GXGameProfileManager();m.save(7,"demo","stable",profile(80));
        assertTrue(m.markLastKnownGood(7,"demo","stable").valid);m.save(7,"demo","stable",profile(55));
        assertEquals(55,m.load(7,"demo","stable").profile().getRenderScalePercent());
        assertEquals(80,m.restoreLastKnownGood(7,"demo").profile().getRenderScalePercent());
        assertTrue(m.delete(7,"demo","stable").valid);assertNull(m.load(7,"demo","stable"));
    }
    @Test public void rejectsInvalidIdentityAndMissingProfile(){
        GXGameProfileManager m=new GXGameProfileManager();
        assertFalse(m.save(-1,"game","profile",profile(90)).valid);
        assertFalse(m.save(0,"game","../bad",profile(90)).valid);
        assertFalse(m.save(0,"","profile",profile(90)).valid);
        assertNull(m.exportProfile(0,"game","missing"));assertNull(m.restoreLastKnownGood(0,"game"));
    }
}
