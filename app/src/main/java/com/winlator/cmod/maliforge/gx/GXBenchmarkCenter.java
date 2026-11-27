package com.winlator.cmod.maliforge.gx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Benchmark statistics over caller-supplied measured frame intervals; does not fabricate hardware data. */
public final class GXBenchmarkCenter {
    public enum Source { GAME_RENDERER, SOFTWARE_TEST, SYNTHETIC, UNKNOWN }
    public Session analyze(String gameId,String deviceId,String workload,String profile,
                          Source source,List<Double> frameIntervalsMs,double targetFps){
        if(source==null || source==Source.UNKNOWN || frameIntervalsMs==null || frameIntervalsMs.size()<2
                || targetFps<=0 || Double.isNaN(targetFps)||Double.isInfinite(targetFps)) return Session.invalid("Insufficient or unidentified measurement source.");
        List<Double> values=new ArrayList<>();
        for(Double d:frameIntervalsMs)if(d!=null&&d>0&&!Double.isNaN(d)&&!Double.isInfinite(d))values.add(d);
        if(values.size()<2)return Session.invalid("Insufficient valid frame intervals.");
        Collections.sort(values);double sum=0;for(double v:values)sum+=v;
        double avg=sum/values.size(),min=values.get(0),max=values.get(values.size()-1),targetMs=1000.0/targetFps;
        int stutters=0;for(double v:values)if(v>targetMs*1.5)stutters++;
        return new Session(true,source,clean(gameId),clean(deviceId),clean(workload),clean(profile),values.size(),
                1000.0/avg,1000.0/max,1000.0/min,avg,min,max,p(values,.50),p(values,.95),p(values,.99),
                stutters,stutters/(double)values.size(),source==Source.SYNTHETIC?"Synthetic software/session data; not a hardware benchmark.":"Measured input intervals; source identity is caller supplied.");
    }
    public Comparison compare(Session before,Session after){
        if(before==null||after==null||!before.valid||!after.valid)return new Comparison(false,"Both sessions must be valid.");
        if(before.source==Source.SYNTHETIC||after.source==Source.SYNTHETIC)return new Comparison(false,"Synthetic sessions are excluded from performance comparisons.");
        if(before.source!=after.source||!before.game.equals(after.game)||!before.device.equals(after.device)||!before.workload.equals(after.workload))
            return new Comparison(false,"Comparison requires matching game, device, source and workload.");
        return new Comparison(true,"Matched sessions: average FPS delta="+(after.averageFps-before.averageFps)+", p95 frame-time delta="+(after.p95Ms-before.p95Ms)+" ms.");
    }
    private static double p(List<Double> sorted,double q){int index=(int)Math.ceil(q*sorted.size())-1;return sorted.get(Math.max(0,Math.min(sorted.size()-1,index)));}
    private static String clean(String s){return s==null?"UNKNOWN":s.trim().replaceAll("[\\r\\n]"," ");}
    public static final class Session{
        public final boolean valid;public final Source source;public final String game,device,workload,profile;public final int sampleCount,stutters;
        public final double averageFps,minFps,maxFps,averageMs,minMs,maxMs,p50Ms,p95Ms,p99Ms,stutterRate;public final String note;
        private Session(boolean valid,Source source,String game,String device,String workload,String profile,int n,double avgFps,double minFps,double maxFps,double avg,double min,double max,double p50,double p95,double p99,int stutters,double rate,String note){
            this.valid=valid;this.source=source;this.game=game;this.device=device;this.workload=workload;this.profile=profile;sampleCount=n;averageFps=avgFps;this.minFps=minFps;this.maxFps=maxFps;averageMs=avg;minMs=min;maxMs=max;p50Ms=p50;p95Ms=p95;p99Ms=p99;this.stutters=stutters;stutterRate=rate;this.note=note;
        }
        private static Session invalid(String reason){return new Session(false,Source.UNKNOWN,"","","","",0,0,0,0,0,0,0,0,0,0,0,reason);}
    }
    public static final class Comparison{public final boolean allowed;public final String summary;private Comparison(boolean a,String s){allowed=a;summary=s;}}
}
