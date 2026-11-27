package com.winlator.cmod.maliforge.gx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** UI-ready snapshot model; values must be supplied by real providers, unknowns stay unknown. */
public final class GXControlCenterModel {
 public enum State { READY, AVAILABLE, DISABLED, EXPERIMENTAL, UNSUPPORTED, UNKNOWN, ERROR }
 public Snapshot build(String gpuName,String gpuState,String backendName,String backendState,
   String profile,String performanceState,String framePacing,String memory,String shaderCache,
   String diagnostics,String benchmark,String gamepad,List<Feature> features){
  List<Item> items=new ArrayList<>();
  items.add(item("GPU",gpuName,gpuState));items.add(item("Backend",backendName,backendState));
  items.add(item("Profile",profile,performanceState));items.add(item("Frame pacing",framePacing,"UNKNOWN"));
  items.add(item("Memory",memory,"UNKNOWN"));items.add(item("Shader cache",shaderCache,"UNKNOWN"));
  items.add(item("Diagnostics",diagnostics,"AVAILABLE"));items.add(item("Benchmark",benchmark,"AVAILABLE"));
  items.add(item("Gamepad",gamepad,"UNKNOWN"));
  List<Feature> safe=features==null?Collections.<Feature>emptyList():new ArrayList<>(features);
  return new Snapshot(items,safe);
 }
 private static Item item(String label,String value,String state){return new Item(label,value,state==null?"UNKNOWN":state);}
 public static final class Feature{
  public final String id,label;public final State state;public final boolean enabled;public final String explanation;
  public Feature(String id,String label,State state,boolean enabled,String explanation){
   this.id=id==null?"":id;this.label=label==null?"":label;this.state=state==null?State.UNKNOWN:state;this.enabled=enabled;this.explanation=explanation==null?"":explanation;
  }
 }
 public static final class Item{
  public final String label,value,state;private Item(String l,String v,String s){label=l;value=v==null?"(unknown)":v;state=s;}
 }
 public static final class Snapshot{
  public final List<Item> items;public final List<Feature> features;
  private Snapshot(List<Item> i,List<Feature> f){items=Collections.unmodifiableList(new ArrayList<>(i));features=Collections.unmodifiableList(new ArrayList<>(f));}
  public String summary(){StringBuilder b=new StringBuilder("MaliForge GX Control Center\n");for(Item i:items)b.append(i.label).append(": ").append(i.value).append(" [").append(i.state).append("]\n");return b.toString();}
 }
}
