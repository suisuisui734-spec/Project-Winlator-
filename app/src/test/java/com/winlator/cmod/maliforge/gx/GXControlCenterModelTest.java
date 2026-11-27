package com.winlator.cmod.maliforge.gx;
import org.junit.Test;import java.util.Collections;import static org.junit.Assert.*;
public class GXControlCenterModelTest{
 @Test public void unknownInputsStayUnknownRatherThanReady(){
  GXControlCenterModel.Snapshot s=new GXControlCenterModel().build("unknown","UNKNOWN","unknown","UNKNOWN","Balanced","UNKNOWN","UNKNOWN","UNKNOWN","UNKNOWN","AVAILABLE","NOT MEASURED","UNKNOWN",null);
  assertTrue(s.summary().contains("GPU: unknown [UNKNOWN]"));assertEquals(9,s.items.size());
 }
 @Test public void featuresAndItemsAreImmutableSnapshots(){
  GXControlCenterModel.Feature f=new GXControlCenterModel.Feature("layer","Vulkan Layer",GXControlCenterModel.State.EXPERIMENTAL,false,"opt-in");
  GXControlCenterModel.Snapshot s=new GXControlCenterModel().build("GPU","READY","backend","AVAILABLE","p","UNKNOWN","UNKNOWN","UNKNOWN","UNKNOWN","AVAILABLE","NOT MEASURED","UNKNOWN",Collections.singletonList(f));
  assertEquals(GXControlCenterModel.State.EXPERIMENTAL,s.features.get(0).state);
  try{s.features.clear();fail();}catch(UnsupportedOperationException expected){}
 }
}
