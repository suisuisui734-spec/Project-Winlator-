package com.winlator.cmod.maliforge.gx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Read-only graphics integrity audit; missing optional components are not automatically corruption. */
public final class GXGraphicsIntegrity {
    public enum Presence { PRESENT, MISSING, INVALID, UNKNOWN }
    public enum Overall { HEALTHY, WARNINGS, INVALID, UNKNOWN }
    public enum Severity { INFO, WARNING, ERROR, UNKNOWN }
    public Report audit(List<Component> components, boolean configurationValid, boolean configurationKnown) {
        if (components == null || !configurationKnown) return new Report(Overall.UNKNOWN, Collections.<Finding>emptyList(),
                "Component inventory or configuration validity is unknown.");
        List<Finding> findings=new ArrayList<>();
        if(!configurationValid) findings.add(new Finding(Severity.ERROR,"CONFIGURATION","GX configuration validation failed."));
        for(Component c:components){
            if(c==null) {findings.add(new Finding(Severity.UNKNOWN,"UNKNOWN_COMPONENT","Null/unreported component entry."));continue;}
            switch(c.presence){
                case INVALID: findings.add(new Finding(Severity.ERROR,c.name,"Component reported invalid state: "+c.detail));break;
                case MISSING: findings.add(new Finding(Severity.WARNING,c.name,"Component is missing/unavailable; this is not proof of corruption."));break;
                case UNKNOWN: findings.add(new Finding(Severity.UNKNOWN,c.name,"Component status was not detected."));break;
                case PRESENT: if(!c.configurationConsistent) findings.add(new Finding(Severity.WARNING,c.name,"Present component has inconsistent configuration."));break;
                default: findings.add(new Finding(Severity.UNKNOWN,c.name,"Component state is unrecognized."));
            }
        }
        Overall overall=Overall.HEALTHY;
        for(Finding f:findings) if(f.severity==Severity.ERROR){overall=Overall.INVALID;break;}
        if(overall!=Overall.INVALID && findings.stream().anyMatch(f->f.severity==Severity.WARNING)) overall=Overall.WARNINGS;
        if(overall==Overall.HEALTHY && findings.stream().anyMatch(f->f.severity==Severity.UNKNOWN)) overall=Overall.UNKNOWN;
        String summary=overall==Overall.HEALTHY?"Reported graphics components and configuration are consistent."
                :overall==Overall.INVALID?"One or more reported components/configuration are invalid; review findings and recovery options."
                :overall==Overall.WARNINGS?"Warnings detected; optional components may be unavailable without being damaged."
                :"Some integrity facts are unknown; collect diagnostics before drawing conclusions.";
        return new Report(overall,findings,summary);
    }
    public static final class Component {
        public final String name; public final Presence presence; public final boolean configurationConsistent; public final String detail;
        public Component(String name,Presence presence,boolean consistent,String detail){
            this.name=name==null?"UNKNOWN":name;this.presence=presence==null?Presence.UNKNOWN:presence;
            configurationConsistent=consistent;this.detail=detail==null?"":detail;
        }
    }
    public static final class Finding {
        public final Severity severity; public final String component; public final String message;
        private Finding(Severity severity,String component,String message){this.severity=severity;this.component=component;this.message=message;}
    }
    public static final class Report {
        public final Overall overall; public final List<Finding> findings; public final String summary;
        private Report(Overall overall,List<Finding> findings,String summary){
            this.overall=overall;this.findings=Collections.unmodifiableList(new ArrayList<>(findings));this.summary=summary;
        }
    }
}
