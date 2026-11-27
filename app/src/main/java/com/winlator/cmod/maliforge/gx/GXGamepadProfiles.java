package com.winlator.cmod.maliforge.gx;
import java.util.Collections;import java.util.LinkedHashMap;import java.util.Map;
/** Per-container/game input mapping model; does not hook or replace Winlator input delivery. */
public final class GXGamepadProfiles {
 private final Map<String,Profile> profiles=new LinkedHashMap<>();
 public Validation save(int containerId,String gameId,Map<String,String> bindings){
  if(containerId<0||gameId==null||gameId.trim().isEmpty()||bindings==null)return new Validation(false,"Container/game/bindings required.");
  Map<String,String> copy=new LinkedHashMap<>();java.util.HashSet<String> used=new java.util.HashSet<>();
  for(Map.Entry<String,String> e:bindings.entrySet()){
   String input=e.getKey(),action=e.getValue();
   if(input==null||!input.matches("[A-Z0-9_]{1,32}")||action==null||!action.matches("[A-Z0-9_]{1,32}"))return new Validation(false,"Mapping names must be safe identifiers.");
   if(!used.add(input))return new Validation(false,"Duplicate physical input mapping.");
   copy.put(input,action);
  }
  profiles.put(key(containerId,gameId),new Profile(containerId,gameId.trim(),copy));return Validation.ok();
 }
 public Profile load(int c,String game){return c<0||game==null?null:profiles.get(key(c,game));}
 public Validation delete(int c,String game){if(c<0||game==null||game.trim().isEmpty())return new Validation(false,"Invalid identity.");profiles.remove(key(c,game));return Validation.ok();}
 private static String key(int c,String g){return c+"|"+g.trim().toLowerCase(java.util.Locale.ROOT);}
 public static final class Profile{
  public final int containerId;public final String gameId;public final Map<String,String> bindings;
  private Profile(int c,String g,Map<String,String> b){containerId=c;gameId=g;bindings=Collections.unmodifiableMap(new LinkedHashMap<>(b));}
 }
 public static final class Validation{public final boolean valid;public final String reason;private Validation(boolean v,String r){valid=v;reason=r;}private static Validation ok(){return new Validation(true,"");}}
}
