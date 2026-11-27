package com.winlator.cmod.maliforge.gx;
import java.util.Collections;import java.util.LinkedHashMap;import java.util.Map;import java.util.Set;
/** Validates, snapshots, diffs and restores GX-owned configuration maps; no Winlator settings mutation. */
public final class GXConfigurationSafety {
 private static final Set<String> ALLOWED=new java.util.HashSet<>(java.util.Arrays.asList("profile.mode","profile.targetFps","profile.renderScale","profile.backendPreference","framePacing.enabled","adaptive.enabled","shaderCache.enabled","layer.enabled"));
 public Validation validate(Map<String,String> values){
  if(values==null)return new Validation(false,"Configuration missing.");
  for(Map.Entry<String,String> e:values.entrySet()){
   if(!ALLOWED.contains(e.getKey()))return new Validation(false,"Unrecognized or non-GX key: "+e.getKey());
   if(e.getValue()==null||e.getValue().length()>128)return new Validation(false,"Value missing or too long.");
   if("profile.targetFps".equals(e.getKey()))try{int n=Integer.parseInt(e.getValue());if(n<0||n>240)return new Validation(false,"targetFps outside 0..240");}catch(NumberFormatException ex){return new Validation(false,"targetFps must be numeric.");}
   if("profile.renderScale".equals(e.getKey()))try{int n=Integer.parseInt(e.getValue());if(n<50||n>100)return new Validation(false,"renderScale outside 50..100");}catch(NumberFormatException ex){return new Validation(false,"renderScale must be numeric.");}
  }
  return Validation.ok();
 }
 public Snapshot backup(Map<String,String> values){Validation v=validate(values);if(!v.valid)return null;return new Snapshot(values);}
 public Snapshot restore(Snapshot backup){return backup==null?null:new Snapshot(backup.values);}
 public Diff diff(Map<String,String> before,Map<String,String> after){
  if(!validate(before).valid||!validate(after).valid)return new Diff(false,Collections.<Change>emptyList(),"Both configurations must pass validation.");
  java.util.Set<String> keys=new java.util.TreeSet<>();keys.addAll(before.keySet());keys.addAll(after.keySet());
  java.util.List<Change> changes=new java.util.ArrayList<>();
  for(String k:keys){String a=before.get(k),b=after.get(k);if(!java.util.Objects.equals(a,b))changes.add(new Change(k,a,b));}
  return new Diff(true,changes,"Validated GX-owned configuration difference.");
 }
 public static final class Validation{public final boolean valid;public final String reason;private Validation(boolean v,String r){valid=v;reason=r;}private static Validation ok(){return new Validation(true,"");}}
 public static final class Snapshot{public final Map<String,String> values;private Snapshot(Map<String,String> v){values=Collections.unmodifiableMap(new LinkedHashMap<>(v));}}
 public static final class Change{public final String key,before,after;private Change(String k,String a,String b){key=k;before=a;after=b;}}
 public static final class Diff{public final boolean valid;public final java.util.List<Change> changes;public final String reason;private Diff(boolean v,java.util.List<Change> c,String r){valid=v;changes=Collections.unmodifiableList(new java.util.ArrayList<>(c));reason=r;}}
}
