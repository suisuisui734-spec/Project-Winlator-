package com.winlator.cmod.maliforge.gx;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Conservative recovery decision model. It records failures and recommends recovery without applying driver changes. */
public final class GXRecoveryController {
    public enum Failure { INITIALIZATION, BACKEND, CONFIGURATION, UNKNOWN }
    public enum Mode { NORMAL, SAFE_MODE, RECOVERY_REQUIRED }
    public enum Action { RETRY_WITH_DIAGNOSTICS, RESTORE_LAST_KNOWN_GOOD, KEEP_CURRENT_BACKEND, DISABLE_EXPERIMENTAL, UNKNOWN }
    private Mode mode=Mode.NORMAL;
    private Failure lastFailure=Failure.UNKNOWN;
    private final Map<String,Boolean> experimental=new LinkedHashMap<>();
    private String lastError="";

    public synchronized void setExperimental(String id,boolean enabled){
        if(id==null || !id.matches("[A-Za-z0-9._-]{1,64}")) throw new IllegalArgumentException("Invalid feature identifier");
        experimental.put(id,enabled);
    }
    public synchronized Map<String,Boolean> experimentalFeatures(){return Collections.unmodifiableMap(new LinkedHashMap<>(experimental));}
    public synchronized Report recordFailure(Failure failure,String message,boolean hasKnownGood){
        lastFailure=failure==null?Failure.UNKNOWN:failure;
        lastError=sanitize(message);
        mode=lastFailure==Failure.CONFIGURATION?Mode.RECOVERY_REQUIRED:Mode.SAFE_MODE;
        if(lastFailure==Failure.BACKEND || lastFailure==Failure.INITIALIZATION){
            for(String id:experimental.keySet()) experimental.put(id,false);
        }
        Action restore=hasKnownGood?Action.RESTORE_LAST_KNOWN_GOOD:Action.RETRY_WITH_DIAGNOSTICS;
        return report(restore);
    }
    public synchronized Report enterSafeMode(){mode=Mode.SAFE_MODE;for(String id:experimental.keySet())experimental.put(id,false);return report(Action.DISABLE_EXPERIMENTAL);}
    public synchronized Report clearAfterRecovery(boolean validated){
        if(!validated)return report(Action.RETRY_WITH_DIAGNOSTICS);
        mode=Mode.NORMAL;lastFailure=Failure.UNKNOWN;lastError="";return report(Action.KEEP_CURRENT_BACKEND);
    }
    public synchronized Report current(){return report(Action.UNKNOWN);}
    private Report report(Action action){return new Report(mode,lastFailure,lastError,action,experimental);}
    private static String sanitize(String text){
        if(text==null)return "No error detail provided.";
        String cleaned=text.replaceAll("[\\r\\n\\t]+"," ").trim();
        return cleaned.length()>256?cleaned.substring(0,256):cleaned;
    }
    public static final class Report {
        public final Mode mode; public final Failure failure; public final String errorSummary; public final Action recommendedAction;
        public final Map<String,Boolean> experimentalFlags;
        private Report(Mode m,Failure f,String e,Action a,Map<String,Boolean> flags){
            mode=m;failure=f;errorSummary=e;recommendedAction=a;experimentalFlags=Collections.unmodifiableMap(new LinkedHashMap<>(flags));
        }
    }
}
