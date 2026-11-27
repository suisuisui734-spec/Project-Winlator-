package com.winlator.cmod.maliforge.gx;
import org.junit.Test;import java.util.Arrays;import static org.junit.Assert.*;
public class GXBenchmarkCenterTest{
 @Test public void calculatesStatisticsFromActualSamples(){
  GXBenchmarkCenter.Session s=new GXBenchmarkCenter().analyze("game","device","same workload","profile",GXBenchmarkCenter.Source.GAME_RENDERER,Arrays.asList(16.0,17.0,20.0,40.0),60);
  assertTrue(s.valid);assertEquals(4,s.sampleCount);assertEquals(43.01, s.averageFps,0.02);assertEquals(40,s.p95Ms,0.01);assertEquals(1,s.stutters);
 }
 @Test public void syntheticDataCannotBeComparedForPerformance(){
  GXBenchmarkCenter c=new GXBenchmarkCenter();GXBenchmarkCenter.Session a=c.analyze("g","d","w","p",GXBenchmarkCenter.Source.SYNTHETIC,Arrays.asList(10.0,10.0,10.0),60);
  assertFalse(c.compare(a,a).allowed);assertTrue(a.note.contains("Synthetic"));
 }
 @Test public void comparisonRequiresSameWorkloadDeviceAndSource(){
  GXBenchmarkCenter c=new GXBenchmarkCenter();GXBenchmarkCenter.Session a=c.analyze("g","d","w","p",GXBenchmarkCenter.Source.SOFTWARE_TEST,Arrays.asList(10.0,11.0,12.0),60);
  GXBenchmarkCenter.Session b=c.analyze("g","d","w","p2",GXBenchmarkCenter.Source.SOFTWARE_TEST,Arrays.asList(9.0,10.0,11.0),60);
  GXBenchmarkCenter.Session wrong=c.analyze("g","other","w","p2",GXBenchmarkCenter.Source.SOFTWARE_TEST,Arrays.asList(9.0,10.0,11.0),60);
  assertTrue(c.compare(a,b).allowed);assertFalse(c.compare(a,wrong).allowed);
 }
 @Test public void invalidOrInsufficientSamplesAreNotBenchmarks(){
  GXBenchmarkCenter c=new GXBenchmarkCenter();assertFalse(c.analyze("g","d","w","p",GXBenchmarkCenter.Source.UNKNOWN,Arrays.asList(10.0,12.0),60).valid);
  assertFalse(c.analyze("g","d","w","p",GXBenchmarkCenter.Source.GAME_RENDERER,Arrays.asList(-1.0),60).valid);
 }
}
