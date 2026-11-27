package com.winlator.cmod.maliforge.gx;
import org.junit.Test;import java.util.*;import static org.junit.Assert.*;
public class GXConfigurationSafetyTest{
 @Test public void validatesAllowedKeysAndRanges(){
  GXConfigurationSafety s=new GXConfigurationSafety();Map<String,String> m=new HashMap<>();m.put("profile.targetFps","60");m.put("profile.renderScale","85");
  assertTrue(s.validate(m).valid);m.put("profile.renderScale","120");assertFalse(s.validate(m).valid);m.remove("profile.renderScale");m.put("container.driver","changed");assertFalse(s.validate(m).valid);
 }
 @Test public void backupAndRestoreAreImmutable(){
  GXConfigurationSafety s=new GXConfigurationSafety();Map<String,String> m=new HashMap<>();m.put("profile.mode","BALANCED");
  GXConfigurationSafety.Snapshot b=s.backup(m);m.put("profile.mode","PERFORMANCE");assertEquals("BALANCED",s.restore(b).values.get("profile.mode"));
  try{b.values.put("x","y");fail();}catch(UnsupportedOperationException expected){}
 }
 @Test public void diffReportsOnlyChangesAndRejectsInvalid(){
  GXConfigurationSafety s=new GXConfigurationSafety();Map<String,String>a=new HashMap<>(),b=new HashMap<>();a.put("profile.mode","BALANCED");b.put("profile.mode","ECO");b.put("framePacing.enabled","true");
  GXConfigurationSafety.Diff d=s.diff(a,b);assertTrue(d.valid);assertEquals(2,d.changes.size());
  b.put("bad.key","x");assertFalse(s.diff(a,b).valid);
 }
}
