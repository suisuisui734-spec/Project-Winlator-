package com.winlator.cmod.maliforge.gx;
import org.junit.Test;import java.util.LinkedHashMap;import java.util.Map;import static org.junit.Assert.*;
public class GXDiagnosticsEngineTest {
 @Test public void redactsEmailAndSensitiveAssignments(){
  String s=GXDiagnosticsEngine.redact("email test@example.com password=abc123 token:xyz");
  assertFalse(s.contains("test@example.com"));assertFalse(s.contains("abc123"));assertFalse(s.contains("xyz"));
 }
 @Test public void removesUserPathsAndBoundsField(){
  String s=GXDiagnosticsEngine.redact("/home/alice/game /storage/emulated/0/private/file");
  assertFalse(s.contains("alice"));assertFalse(s.contains("/private/file"));
  assertTrue(GXDiagnosticsEngine.redact("x".repeat(5000)).length()<4200);
 }
 @Test public void exportHasSectionLabelsAndImmutableMap(){
  Map<String,String> m=new LinkedHashMap<>();m.put("GPU","Mali unknown capabilities");m.put("Backend","not initialized");
  GXDiagnosticsEngine.Bundle b=new GXDiagnosticsEngine().create(m);
  assertTrue(b.exportText().contains("## GPU"));assertTrue(b.exportText().contains("## Backend"));
  try{b.sections.put("x","y");fail("must be immutable");}catch(UnsupportedOperationException expected){}
 }
 @Test public void nullDataIsExplicitlyUnavailable(){
  assertEquals("(not available)",GXDiagnosticsEngine.redact(null));
 }
}
