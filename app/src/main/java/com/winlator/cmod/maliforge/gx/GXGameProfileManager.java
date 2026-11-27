package com.winlator.cmod.maliforge.gx;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Additive per-container/game profile catalog with validation, import/export and recovery snapshots. */
public final class GXGameProfileManager {
    public static final int FORMAT_VERSION = 1;
    private final Map<String, Entry> profiles = new LinkedHashMap<>();
    private final Map<String, String> lastKnownGood = new LinkedHashMap<>();

    public synchronized Validation save(int containerId, String gameId, String profileId, GXGameProfile profile) {
        Validation v = validateIdentity(containerId, gameId, profileId);
        if (!v.valid || profile == null) return new Validation(false, v.reason.isEmpty() ? "Profile payload required." : v.reason);
        profiles.put(key(containerId, gameId, profileId), new Entry(containerId, normalizeGame(gameId), profileId.trim(), profile.toStorageValue()));
        return Validation.ok();
    }
    public synchronized Entry load(int containerId, String gameId, String profileId) {
        return validateIdentity(containerId, gameId, profileId).valid ? profiles.get(key(containerId, gameId, profileId)) : null;
    }
    public synchronized Validation delete(int containerId, String gameId, String profileId) {
        Validation v=validateIdentity(containerId, gameId, profileId); if (!v.valid) return v;
        profiles.remove(key(containerId, gameId, profileId)); return Validation.ok();
    }
    public synchronized Map<String, Entry> listForGame(int containerId, String gameId) {
        if (containerId<0 || gameId==null || gameId.trim().isEmpty()) return Collections.emptyMap();
        Map<String,Entry> result=new LinkedHashMap<>();
        String normalized=normalizeGame(gameId);
        for (Entry e:profiles.values()) if (e.containerId==containerId && e.gameId.equals(normalized)) result.put(e.profileId,e);
        return Collections.unmodifiableMap(result);
    }
    public synchronized Validation markLastKnownGood(int containerId, String gameId, String profileId) {
        Entry e=load(containerId,gameId,profileId); if(e==null) return new Validation(false,"Profile missing; backup unchanged.");
        lastKnownGood.put(gameKey(containerId,gameId),e.export()); return Validation.ok();
    }
    public synchronized Entry restoreLastKnownGood(int containerId,String gameId) {
        String backup=lastKnownGood.get(gameKey(containerId,gameId)); return backup==null?null:importProfile(backup);
    }
    public synchronized String exportProfile(int containerId,String gameId,String profileId) {
        Entry e=load(containerId,gameId,profileId); return e==null?null:e.export();
    }
    public synchronized Entry importProfile(String encoded) {
        if(encoded==null || encoded.length()>32768) return null;
        String[] f=encoded.split("\\|",-1); if(f.length!=7 || !"GXGP".equals(f[0])) return null;
        try {
            if(Integer.parseInt(f[1])!=FORMAT_VERSION) return null;
            int c=Integer.parseInt(f[2]);
            String game=new String(Base64.getDecoder().decode(f[3]),StandardCharsets.UTF_8);
            String id=new String(Base64.getDecoder().decode(f[4]),StandardCharsets.UTF_8);
            String payload=new String(Base64.getDecoder().decode(f[5]),StandardCharsets.UTF_8);
            String signed=String.join("|",f[0],f[1],f[2],f[3],f[4],f[5]);
            if(!checksum(signed).equals(f[6]) || !validateIdentity(c,game,id).valid || GXGameProfile.fromStorageValue(payload)==null) return null;
            save(c,game,id,GXGameProfile.fromStorageValue(payload)); return load(c,game,id);
        } catch(IllegalArgumentException ex) { return null; }
    }
    public synchronized int profileCount(){return profiles.size();}
    private static Validation validateIdentity(int c,String game,String id) {
        if(c<0) return new Validation(false,"Container ID must be non-negative.");
        if(game==null || game.trim().isEmpty() || game.length()>512) return new Validation(false,"Game ID is invalid.");
        if(id==null || !id.matches("[A-Za-z0-9._-]{1,64}") || id.equals(".") || id.equals("..")) return new Validation(false,"Profile ID must be 1–64 safe ASCII characters.");
        return Validation.ok();
    }
    private static String normalizeGame(String s){return s.trim().toLowerCase(Locale.ROOT);}
    private static String gameKey(int c,String g){return c+"|"+normalizeGame(g);}
    private static String key(int c,String g,String p){return gameKey(c,g)+"|"+p.trim();}
    private static String checksum(String s){
        try{byte[] d=MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));StringBuilder b=new StringBuilder();for(byte x:d)b.append(String.format(Locale.ROOT,"%02x",x&255));return b.toString();}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException("SHA-256 unavailable",e);}
    }
    public static final class Validation {
        public final boolean valid; public final String reason;
        private Validation(boolean v,String r){valid=v;reason=r;}
        private static Validation ok(){return new Validation(true,"");}
    }
    public static final class Entry {
        public final int containerId; public final String gameId,profileId,profileStorage;
        private Entry(int c,String g,String p,String storage){containerId=c;gameId=g;profileId=p;profileStorage=storage;}
        public GXGameProfile profile(){return GXGameProfile.fromStorageValue(profileStorage);}
        private String export(){
            String[] f={"GXGP",String.valueOf(FORMAT_VERSION),String.valueOf(containerId),
                Base64.getEncoder().encodeToString(gameId.getBytes(StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString(profileId.getBytes(StandardCharsets.UTF_8)),
                Base64.getEncoder().encodeToString(profileStorage.getBytes(StandardCharsets.UTF_8))};
            String signed=String.join("|",f);return signed+"|"+checksum(signed);
        }
    }
}
