package com.winlator.cmod.maliforge.gx;
import org.junit.Test;import static org.junit.Assert.*;
public class GXRecoveryControllerTest {
 @Test public void backendFailureDisablesExperimentalFlagsAndEntersSafeMode(){
  GXRecoveryController c=new GXRecoveryController();c.setExperimental("layer-poc",true);c.setExperimental("adaptive",true);
  GXRecoveryController.Report r=c.recordFailure(GXRecoveryController.Failure.BACKEND,"Backend init failed",true);
  assertEquals(GXRecoveryController.Mode.SAFE_MODE,r.mode);assertEquals(GXRecoveryController.Action.RESTORE_LAST_KNOWN_GOOD,r.recommendedAction);
  assertFalse(r.experimentalFlags.get("layer-poc"));assertFalse(r.experimentalFlags.get("adaptive"));
 }
 @Test public void configurationFailureRequiresRecovery(){
  GXRecoveryController c=new GXRecoveryController();assertEquals(GXRecoveryController.Mode.RECOVERY_REQUIRED,
   c.recordFailure(GXRecoveryController.Failure.CONFIGURATION,"bad config",false).mode);
 }
 @Test public void safeModeAndValidatedExit(){
  GXRecoveryController c=new GXRecoveryController();c.setExperimental("feature-x",true);
  assertEquals(GXRecoveryController.Mode.SAFE_MODE,c.enterSafeMode().mode);
  assertFalse(c.current().experimentalFlags.get("feature-x"));
  assertEquals(GXRecoveryController.Mode.SAFE_MODE,c.clearAfterRecovery(false).mode);
  assertEquals(GXRecoveryController.Mode.NORMAL,c.clearAfterRecovery(true).mode);
 }
 @Test public void errorSummaryIsBoundedAndSanitized(){
  GXRecoveryController c=new GXRecoveryController();String longText="x".repeat(400);
  assertEquals(256,c.recordFailure(GXRecoveryController.Failure.UNKNOWN,longText,false).errorSummary.length());
 }
}
