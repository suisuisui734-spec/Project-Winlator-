package com.winlator.cmod.maliforge.gx;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Diagnostic bundle formatter with conservative redaction and bounded output. */
public final class GXDiagnosticsEngine {
    private static final int MAX_FIELD=4096,MAX_TOTAL=32768;
    public Bundle create(Map<String,String> sections){
        Map<String,String> safe=new LinkedHashMap<>();
        if(sections!=null)for(Map.Entry<String,String> e:sections.entrySet()){
            String key=clean(e.getKey(),128);if(key.isEmpty())continue;
            safe.put(key,redact(e.getValue()));
        }
        return new Bundle(safe);
    }
    public static String redact(String input){
        if(input==null)return "(not available)";
        String s=input.replaceAll("[\\r\\n\\t]+"," ").trim();
        s=s.replaceAll("(?i)([A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,})","[REDACTED_EMAIL]");
        s=s.replaceAll("(?i)(token|password|passwd|secret|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+","$1=[REDACTED]");
        s=s.replaceAll("/data/(user|data)/[^/]+/[^\\s]+","[REDACTED_APP_PATH]");
        s=s.replaceAll("/storage/emulated/0/[^\\s]+","[REDACTED_USER_PATH]");
        s=s.replaceAll("/home/[^/]+/[^\\s]+","[REDACTED_HOME_PATH]");
        return s.length()>MAX_FIELD?s.substring(0,MAX_FIELD)+" [TRUNCATED]":s;
    }
    private static String clean(String s,int max){if(s==null)return "";s=s.replaceAll("[^A-Za-z0-9._ -]","_").trim();return s.length()>max?s.substring(0,max):s;}
    public static final class Bundle{
        public final Map<String,String> sections;
        private Bundle(Map<String,String> s){sections=Collections.unmodifiableMap(new LinkedHashMap<>(s));}
        public String exportText(){
            StringBuilder b=new StringBuilder("MALIFORGE GX DIAGNOSTICS\n");
            for(Map.Entry<String,String> e:sections.entrySet()){
                if(b.length()>MAX_TOTAL)break;
                b.append("\n## ").append(e.getKey()).append("\n").append(e.getValue()).append("\n");
            }
            if(b.length()>MAX_TOTAL)return b.substring(0,MAX_TOTAL)+"\n[TRUNCATED BUNDLE]";
            return b.toString();
        }
    }
}
