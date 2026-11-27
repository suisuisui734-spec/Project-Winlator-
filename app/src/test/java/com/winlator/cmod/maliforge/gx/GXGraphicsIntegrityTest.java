package com.winlator.cmod.maliforge.gx;
import org.junit.Test; import java.util.Arrays; import static org.junit.Assert.*;
public class GXGraphicsIntegrityTest {
 @Test public void optionalMissingComponentIsWarningNotCorruption(){
  GXGraphicsIntegrity.Report r=new GXGraphicsIntegrity().audit(Arrays.asList(
   new GXGraphicsIntegrity.Component("DXVK",GXGraphicsIntegrity.Presence.PRESENT,true,""),
   new GXGraphicsIntegrity.Component("Experimental layer",GXGraphicsIntegrity.Presence.MISSING,true,"")),
   true,true);
  assertEquals(GXGraphicsIntegrity.Overall.WARNINGS,r.overall);
 }
 @Test public void invalidConfigurationProducesInvalidReport(){
  GXGraphicsIntegrity.Report r=new GXGraphicsIntegrity().audit(Arrays.asList(
   new GXGraphicsIntegrity.Component("Driver",GXGraphicsIntegrity.Presence.PRESENT,true,"")),false,true);
  assertEquals(GXGraphicsIntegrity.Overall.INVALID,r.overall);
 }
 @Test public void unknownInventoryDoesNotClaimHealthy(){
  assertEquals(GXGraphicsIntegrity.Overall.UNKNOWN,new GXGraphicsIntegrity().audit(null,true,true).overall);
  assertEquals(GXGraphicsIntegrity.Overall.UNKNOWN,new GXGraphicsIntegrity().audit(Arrays.asList(
   new GXGraphicsIntegrity.Component("GPU",GXGraphicsIntegrity.Presence.UNKNOWN,true,"")),true,true).overall);
 }
 @Test public void inconsistentPresentComponentCreatesWarning(){
  assertEquals(GXGraphicsIntegrity.Overall.WARNINGS,new GXGraphicsIntegrity().audit(Arrays.asList(
   new GXGraphicsIntegrity.Component("Backend",GXGraphicsIntegrity.Presence.PRESENT,false,"")),true,true).overall);
 }
}
