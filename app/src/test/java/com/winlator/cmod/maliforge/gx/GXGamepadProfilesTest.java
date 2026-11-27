package com.winlator.cmod.maliforge.gx;
import org.junit.Test;import java.util.LinkedHashMap;import java.util.Map;import static org.junit.Assert.*;
public class GXGamepadProfilesTest{
 @Test public void mappingsArePerContainerAndGame(){
  GXGamepadProfiles p=new GXGamepadProfiles();Map<String,String> b=new LinkedHashMap<>();b.put("BTN_A","JUMP");b.put("BTN_B","ATTACK");
  assertTrue(p.save(1,"game.exe",b).valid);assertEquals("JUMP",p.load(1,"GAME.EXE").bindings.get("BTN_A"));assertNull(p.load(2,"game.exe"));
 }
 @Test public void invalidIdentifiersAndNullDataRejected(){
  GXGamepadProfiles p=new GXGamepadProfiles();Map<String,String> b=new LinkedHashMap<>();b.put("../unsafe","jump");
  assertFalse(p.save(0,"game",b).valid);assertFalse(p.save(-1,"game",new LinkedHashMap<>()).valid);
 }
 @Test public void snapshotsImmutableAndDeleteDoesNotAffectOtherProfiles(){
  GXGamepadProfiles p=new GXGamepadProfiles();Map<String,String> b=new LinkedHashMap<>();b.put("BTN_A","JUMP");p.save(1,"a",b);p.save(1,"b",b);
  try{p.load(1,"a").bindings.put("BTN_X","RUN");fail();}catch(UnsupportedOperationException expected){}
  p.delete(1,"a");assertNull(p.load(1,"a"));assertNotNull(p.load(1,"b"));
 }
}
