package com.winlator.cmod.maliforge.gx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Evidence-only per-game advisor that composes existing GX compatibility, performance,
 * resolution and memory analyzers. It never detects games by guessing or applies settings.
 */
public final class GXGameAdvisor {
    public enum Confidence { LOW, MEDIUM, HIGH }
    public enum AdviceType { COMPATIBILITY, PERFORMANCE, RESOLUTION, MEMORY, GAME_DATABASE }
    public enum AdviceState { RECOMMENDATION, KEEP_CURRENT, NEEDS_MORE_DATA, UNSUPPORTED, INFORMATIONAL }

    private final GXCompatibilityAdvisor compatibilityAdvisor = new GXCompatibilityAdvisor();
    private final GXPerformanceAdvisor performanceAdvisor = new GXPerformanceAdvisor();
    private final GXMemoryIntelligence memoryIntelligence = new GXMemoryIntelligence();
    private final GXResolutionEngine resolutionEngine = new GXResolutionEngine();

    public Result advise(Request request) {
        if (request == null) {
            return new Result("", Confidence.LOW, false, Collections.singletonList(
                    new Advice(AdviceType.GAME_DATABASE, AdviceState.NEEDS_MORE_DATA, Confidence.LOW,
                            "No game context was supplied.",
                            "Provide a verified game identifier and measured runtime data; no settings were changed.")));
        }

        List<Advice> advice = new ArrayList<>();
        Confidence overall = Confidence.LOW;
        boolean identified = request.gameIdentityVerified && !request.gameId.trim().isEmpty();

        GXCompatibilityAdvisor.Result compatibility;
        if (request.compatibilityRequest == null || request.environment == null) {
            compatibility = null;
            advice.add(new Advice(AdviceType.COMPATIBILITY, AdviceState.NEEDS_MORE_DATA, Confidence.LOW,
                    "Compatibility cannot be evaluated yet.",
                    "The Windows graphics API, backend, or runtime capability snapshot is missing."));
        } else {
            GXCompatibilityAdvisor.Request original = request.compatibilityRequest;
            GXCompatibilityAdvisor.Request scoped = new GXCompatibilityAdvisor.Request(
                    identified ? request.gameId : "",
                    original.api, original.backend,
                    new java.util.LinkedHashSet<>(original.requiredExtensions));
            compatibility = compatibilityAdvisor.evaluate(scoped, request.environment,
                    identified ? request.knownIssues : Collections.<GXCompatibilityAdvisor.KnownIssue>emptyList());
            Confidence confidence = map(compatibility.confidence);
            overall = minimum(overall, confidence);
            AdviceState state;
            switch (compatibility.status) {
                case UNSUPPORTED:
                    state = AdviceState.UNSUPPORTED;
                    break;
                case BACKEND_INITIALIZED:
                case AVAILABLE_NOT_INITIALIZED:
                    state = AdviceState.INFORMATIONAL;
                    break;
                case BACKEND_FAILED:
                    state = AdviceState.RECOMMENDATION;
                    break;
                case UNKNOWN:
                default:
                    state = AdviceState.NEEDS_MORE_DATA;
                    break;
            }
            String detail = compatibility.summary;
            if (!compatibility.fallbackRecommendations.isEmpty()) {
                detail += " Suggested fallback: " + join(compatibility.fallbackRecommendations);
            }
            advice.add(new Advice(AdviceType.COMPATIBILITY, state, confidence,
                    "Compatibility status: " + compatibility.status.name(), detail));
            if (!identified) {
                advice.add(new Advice(AdviceType.GAME_DATABASE, AdviceState.NEEDS_MORE_DATA, Confidence.LOW,
                        "Game-specific issue matching is disabled.",
                        "The caller has not verified this game identity; no game-specific database entry was assumed."));
            } else if (compatibility.knownIssues.isEmpty()) {
                advice.add(new Advice(AdviceType.GAME_DATABASE, AdviceState.INFORMATIONAL, Confidence.LOW,
                        "No matching known-issue record was supplied.",
                        "This is not proof of compatibility; the database may be incomplete."));
            } else {
                List<String> evidence = new ArrayList<>();
                for (GXCompatibilityAdvisor.KnownIssue issue : request.knownIssues) {
                    if (issue != null && request.gameId.equalsIgnoreCase(issue.gameId)
                            && !issue.description.isEmpty()) {
                        evidence.add(issue.description + (issue.source.isEmpty() ? "" : " (source: " + issue.source + ")"));
                    }
                }
                advice.add(new Advice(AdviceType.GAME_DATABASE, AdviceState.INFORMATIONAL, Confidence.MEDIUM,
                        "Matching caller-supplied known issues: " + compatibility.knownIssues.size(),
                        join(evidence)));
            }
        }

        GXPerformanceAdvisor.Advice performance = performanceAdvisor.advise(request.performanceTelemetry);
        Confidence performanceConfidence = request.performanceTelemetry != null
                && request.performanceTelemetry.sampleCount >= 30
                && request.performanceTelemetry.workload != GXPerformanceAdvisor.Workload.UNKNOWN
                ? Confidence.MEDIUM : Confidence.LOW;
        overall = minimum(overall, performanceConfidence);
        AdviceState performanceState;
        switch (performance.recommendation) {
            case KEEP_CURRENT:
                performanceState = AdviceState.KEEP_CURRENT;
                break;
            case COLLECT_MORE_DATA:
                performanceState = AdviceState.NEEDS_MORE_DATA;
                break;
            default:
                performanceState = AdviceState.RECOMMENDATION;
                break;
        }
        String performanceDetail = performance.reason;
        if (!performance.notes.isEmpty()) performanceDetail += " " + join(performance.notes);
        advice.add(new Advice(AdviceType.PERFORMANCE, performanceState, performanceConfidence,
                "Performance advice: " + performance.recommendation.name()
                        + "; suggested profile: " + performance.profileSuggestion.name(),
                performanceDetail));

        GXMemoryIntelligence.SystemMemorySnapshot memory = request.memorySnapshot;
        String memoryDetail = memoryIntelligence.recommendation(memory);
        Confidence memoryConfidence = memory != null && memory.pressure != GXMemoryIntelligence.Pressure.UNKNOWN
                ? Confidence.MEDIUM : Confidence.LOW;
        overall = minimum(overall, memoryConfidence);
        AdviceState memoryState = memory == null || memory.pressure == GXMemoryIntelligence.Pressure.UNKNOWN
                ? AdviceState.NEEDS_MORE_DATA
                : memory.pressure == GXMemoryIntelligence.Pressure.HIGH
                    || memory.pressure == GXMemoryIntelligence.Pressure.CRITICAL
                    ? AdviceState.RECOMMENDATION : AdviceState.INFORMATIONAL;
        advice.add(new Advice(AdviceType.MEMORY, memoryState, memoryConfidence,
                "Memory pressure: " + (memory == null ? "UNKNOWN" : memory.pressure.name()),
                memoryDetail));

        GXPerformanceAdvisor.Telemetry telemetry = request.performanceTelemetry;
        GXResolutionEngine.Recommendation resolution;
        if (telemetry == null) {
            resolution = resolutionEngine.recommend(0, 0, 0, GXPerformanceAdvisor.Workload.UNKNOWN,
                    Double.NaN, request.currentRenderScalePercent);
        } else {
            resolution = resolutionEngine.recommend(telemetry.sampleCount, telemetry.p95FrameTimeMs,
                    telemetry.targetFps, telemetry.workload, telemetry.memoryPressure(),
                    request.currentRenderScalePercent);
        }
        AdviceState resolutionState;
        String resolutionDetail = resolution.reason;
        if (resolution.status == GXResolutionEngine.Status.RECOMMEND_SCALE) {
            GXResolutionEngine.Resolution candidate = resolutionEngine.scale(
                    request.sourceWidth, request.sourceHeight, resolution.suggestedScalePercent);
            GXResolutionEngine.Validation validation = resolutionEngine.validate(
                    candidate, request.displayWidth, request.displayHeight);
            if (validation.status == GXResolutionEngine.Status.KEEP_CURRENT) {
                resolutionDetail += " Candidate size: " + candidate.width + "x" + candidate.height
                        + " within display bounds. Test in a separate profile and keep rollback.";
                resolutionState = AdviceState.RECOMMENDATION;
            } else {
                resolutionDetail += " Candidate render size is not validated against the supplied display bounds: "
                        + validation.reason;
                resolutionState = AdviceState.NEEDS_MORE_DATA;
            }
        } else if (resolution.status == GXResolutionEngine.Status.KEEP_CURRENT) {
            resolutionState = AdviceState.KEEP_CURRENT;
        } else {
            resolutionState = AdviceState.NEEDS_MORE_DATA;
        }
        Confidence resolutionConfidence = resolution.status == GXResolutionEngine.Status.RECOMMEND_SCALE
                || resolution.status == GXResolutionEngine.Status.KEEP_CURRENT
                ? Confidence.MEDIUM : Confidence.LOW;
        overall = minimum(overall, resolutionConfidence);
        advice.add(new Advice(AdviceType.RESOLUTION, resolutionState, resolutionConfidence,
                "Resolution advice: " + resolution.status.name()
                        + "; proposed render scale: " + resolution.suggestedScalePercent + "%",
                resolutionDetail));

        if (!identified) {
            overall = Confidence.LOW;
        }
        return new Result(request.gameId, overall, identified, advice);
    }

    private static Confidence map(GXCompatibilityAdvisor.Confidence value) {
        if (value == GXCompatibilityAdvisor.Confidence.HIGH) return Confidence.HIGH;
        if (value == GXCompatibilityAdvisor.Confidence.MEDIUM) return Confidence.MEDIUM;
        return Confidence.LOW;
    }

    private static Confidence minimum(Confidence left, Confidence right) {
        return left.ordinal() <= right.ordinal() ? left : right;
    }

    private static String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) continue;
            if (result.length() > 0) result.append(" ");
            result.append(value.trim());
        }
        return result.toString();
    }

    public static final class Request {
        public final String gameId;
        public final boolean gameIdentityVerified;
        public final GXCompatibilityAdvisor.Request compatibilityRequest;
        public final GXCompatibilityAdvisor.Environment environment;
        public final List<GXCompatibilityAdvisor.KnownIssue> knownIssues;
        public final GXPerformanceAdvisor.Telemetry performanceTelemetry;
        public final GXMemoryIntelligence.SystemMemorySnapshot memorySnapshot;
        public final int sourceWidth;
        public final int sourceHeight;
        public final int displayWidth;
        public final int displayHeight;
        public final int currentRenderScalePercent;

        public Request(String gameId, boolean gameIdentityVerified,
                       GXCompatibilityAdvisor.Request compatibilityRequest,
                       GXCompatibilityAdvisor.Environment environment,
                       List<GXCompatibilityAdvisor.KnownIssue> knownIssues,
                       GXPerformanceAdvisor.Telemetry performanceTelemetry,
                       GXMemoryIntelligence.SystemMemorySnapshot memorySnapshot,
                       int sourceWidth, int sourceHeight, int displayWidth, int displayHeight,
                       int currentRenderScalePercent) {
            this.gameId = gameId == null ? "" : gameId.trim();
            this.gameIdentityVerified = gameIdentityVerified;
            this.compatibilityRequest = compatibilityRequest;
            this.environment = environment;
            this.knownIssues = knownIssues == null ? Collections.<GXCompatibilityAdvisor.KnownIssue>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(knownIssues));
            this.performanceTelemetry = performanceTelemetry;
            this.memorySnapshot = memorySnapshot;
            this.sourceWidth = sourceWidth;
            this.sourceHeight = sourceHeight;
            this.displayWidth = displayWidth;
            this.displayHeight = displayHeight;
            this.currentRenderScalePercent = currentRenderScalePercent;
        }
    }

    public static final class Advice {
        public final AdviceType type;
        public final AdviceState state;
        public final Confidence confidence;
        public final String title;
        public final String explanation;

        private Advice(AdviceType type, AdviceState state, Confidence confidence,
                       String title, String explanation) {
            this.type = type;
            this.state = state;
            this.confidence = confidence;
            this.title = title == null ? "" : title;
            this.explanation = explanation == null ? "" : explanation;
        }
    }

    public static final class Result {
        public final String gameId;
        public final Confidence confidence;
        public final boolean gameIdentityVerified;
        public final boolean settingsApplied;
        public final List<Advice> recommendations;

        private Result(String gameId, Confidence confidence, boolean identified, List<Advice> recommendations) {
            this.gameId = gameId == null ? "" : gameId;
            this.confidence = confidence;
            this.gameIdentityVerified = identified;
            this.settingsApplied = false;
            this.recommendations = Collections.unmodifiableList(new ArrayList<>(recommendations));
        }
    }
}
