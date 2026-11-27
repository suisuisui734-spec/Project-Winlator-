package com.winlator.cmod.maliforge.gx;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Evaluates only explicit API/backend/capability facts. BACKEND_INITIALIZED is not a claim that
 * a particular Windows game is compatible or that its rendering path has been tested.
 */
public final class GXCompatibilityAdvisor {
    public enum WindowsApi { DIRECT3D_9, DIRECT3D_10, DIRECT3D_11, DIRECT3D_12, OPENGL, VULKAN, UNKNOWN }
    public enum Backend { DXVK, VKD3D, WINED3D, CURRENT, UNKNOWN }
    public enum Fact { YES, NO, UNKNOWN }
    public enum Initialization { INITIALIZED, NOT_INITIALIZED, FAILED, UNKNOWN }
    public enum Status { BACKEND_INITIALIZED, AVAILABLE_NOT_INITIALIZED, UNSUPPORTED, BACKEND_FAILED, UNKNOWN }
    public enum Confidence { LOW, MEDIUM, HIGH }

    public Result evaluate(Request request, Environment environment, List<KnownIssue> issueDatabase) {
        if (request == null || environment == null) {
            return result(Status.UNKNOWN, Confidence.LOW,
                    "Game/API request or runtime environment is unavailable.",
                    Collections.<String>emptyList(), Collections.<String>emptyList());
        }
        List<String> reasons = new ArrayList<>();
        List<String> fallback = new ArrayList<>();
        List<String> issues = findIssues(request.gameId, issueDatabase);

        if (request.api == WindowsApi.UNKNOWN || request.backend == Backend.UNKNOWN) {
            return result(Status.UNKNOWN, Confidence.LOW,
                    "The Windows graphics API or selected backend is unknown.", issues, fallback, reasons);
        }
        if (!backendMatchesApi(request.api, request.backend)) {
            reasons.add("The selected backend is not a known match for the requested Windows graphics API.");
            return result(Status.UNSUPPORTED, Confidence.HIGH,
                    reasons.get(0), issues, fallback, reasons);
        }

        Fact requiredRuntime = request.backend == Backend.WINED3D
                ? environment.openGlRuntime : environment.vulkanRuntime;
        if (requiredRuntime == Fact.NO) {
            reasons.add(request.backend == Backend.WINED3D
                    ? "OpenGL runtime capability is explicitly unavailable."
                    : "Vulkan runtime capability is explicitly unavailable.");
            fallback.add("Keep the existing driver configuration and inspect available backend alternatives; do not force an unavailable API.");
            return result(Status.UNSUPPORTED, Confidence.HIGH, reasons.get(0), issues, fallback, reasons);
        }
        if (requiredRuntime == Fact.UNKNOWN) {
            reasons.add("Required graphics API capability has not been detected.");
            return result(Status.UNKNOWN, Confidence.LOW, reasons.get(0), issues, fallback, reasons);
        }

        if (environment.backendInstalled == Fact.NO) {
            reasons.add("Selected backend files are not installed.");
            return result(Status.UNSUPPORTED, Confidence.HIGH, reasons.get(0), issues, fallback, reasons);
        }
        if (environment.backendInstalled == Fact.UNKNOWN) {
            reasons.add("Backend installation state is unknown.");
            return result(Status.UNKNOWN, Confidence.LOW, reasons.get(0), issues, fallback, reasons);
        }

        for (String extension : request.requiredExtensions) {
            Fact state = environment.extensionStates.get(extension);
            if (state == Fact.NO) {
                reasons.add("Required extension is explicitly unsupported: " + extension);
                return result(Status.UNSUPPORTED, Confidence.HIGH, reasons.get(0), issues, fallback, reasons);
            }
            if (state == null || state == Fact.UNKNOWN) {
                reasons.add("Required extension availability is unknown: " + extension);
                return result(Status.UNKNOWN, Confidence.LOW, reasons.get(0), issues, fallback, reasons);
            }
        }

        if (environment.initialization == Initialization.FAILED) {
            reasons.add("Backend initialization failed; a fallback may be considered after diagnostics.");
            fallback.add("Use the existing Winlator backend selector to choose a previously working option; preserve the current container settings.");
            return result(Status.BACKEND_FAILED, Confidence.HIGH, reasons.get(0), issues, fallback, reasons);
        }
        if (environment.initialization == Initialization.NOT_INITIALIZED) {
            reasons.add("Backend is installed and its required API capability is reported, but initialization has not been verified.");
            return result(Status.AVAILABLE_NOT_INITIALIZED, Confidence.MEDIUM,
                    reasons.get(0), issues, fallback, reasons);
        }
        if (environment.initialization == Initialization.UNKNOWN) {
            reasons.add("Backend initialization result is unknown.");
            return result(Status.UNKNOWN, Confidence.LOW, reasons.get(0), issues, fallback, reasons);
        }

        reasons.add("Backend initialization and the requested API's declared requirements are verified; game compatibility is not yet proven.");
        if (!issues.isEmpty()) reasons.add("A matching caller-supplied known-issue record exists for this game.");
        return result(Status.BACKEND_INITIALIZED, Confidence.MEDIUM,
                reasons.get(0), issues, fallback, reasons);
    }

    private static boolean backendMatchesApi(WindowsApi api, Backend backend) {
        switch (api) {
            case DIRECT3D_9:
            case DIRECT3D_10:
            case DIRECT3D_11:
                return backend == Backend.DXVK || backend == Backend.WINED3D;
            case DIRECT3D_12:
                return backend == Backend.VKD3D;
            case OPENGL:
                return backend == Backend.WINED3D;
            case VULKAN:
                return backend == Backend.CURRENT;
            case UNKNOWN:
            default:
                return false;
        }
    }

    private static List<String> findIssues(String gameId, List<KnownIssue> database) {
        if (gameId == null || gameId.trim().isEmpty() || database == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (KnownIssue issue : database) {
            if (issue != null && gameId.trim().equalsIgnoreCase(issue.gameId)) {
                result.add(issue.description);
            }
        }
        return result;
    }

    private static Result result(Status status, Confidence confidence, String reason,
                                 List<String> issues, List<String> fallback, List<String> details) {
        List<String> reasons = details == null ? Collections.<String>emptyList()
                : new ArrayList<>(details);
        if (reasons.isEmpty() && reason != null) reasons.add(reason);
        return new Result(status, confidence, reasons, issues, fallback, reason);
    }

    private static Result result(Status status, Confidence confidence, String reason,
                                 List<String> issues, List<String> fallback) {
        return result(status, confidence, reason, issues, fallback, Collections.<String>emptyList());
    }

    public static final class Request {
        public final String gameId;
        public final WindowsApi api;
        public final Backend backend;
        public final List<String> requiredExtensions;

        public Request(String gameId, WindowsApi api, Backend backend, Set<String> extensions) {
            this.gameId = gameId == null ? "" : gameId.trim();
            this.api = api == null ? WindowsApi.UNKNOWN : api;
            this.backend = backend == null ? Backend.UNKNOWN : backend;
            this.requiredExtensions = extensions == null
                    ? Collections.<String>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(extensions));
        }
    }

    public static final class Environment {
        public final Fact vulkanRuntime;
        public final Fact openGlRuntime;
        public final Fact backendInstalled;
        public final Initialization initialization;
        public final Map<String, Fact> extensionStates;

        public Environment(Fact vulkanRuntime, Fact openGlRuntime, Fact backendInstalled,
                           Initialization initialization, Map<String, Fact> extensionStates) {
            this.vulkanRuntime = vulkanRuntime == null ? Fact.UNKNOWN : vulkanRuntime;
            this.openGlRuntime = openGlRuntime == null ? Fact.UNKNOWN : openGlRuntime;
            this.backendInstalled = backendInstalled == null ? Fact.UNKNOWN : backendInstalled;
            this.initialization = initialization == null ? Initialization.UNKNOWN : initialization;
            this.extensionStates = extensionStates == null
                    ? Collections.<String, Fact>emptyMap()
                    : Collections.unmodifiableMap(extensionStates);
        }
    }

    /** No built-in game list is assumed. Records must come from a sourced/verified database. */
    public static final class KnownIssue {
        public final String gameId;
        public final String description;
        public final String source;

        public KnownIssue(String gameId, String description, String source) {
            this.gameId = gameId == null ? "" : gameId.trim();
            this.description = description == null ? "" : description.trim();
            this.source = source == null ? "" : source.trim();
        }
    }

    public static final class Result {
        public final Status status;
        public final Confidence confidence;
        public final List<String> reasons;
        public final List<String> knownIssues;
        public final List<String> fallbackRecommendations;
        public final String summary;

        private Result(Status status, Confidence confidence, List<String> reasons,
                       List<String> issues, List<String> fallback, String summary) {
            this.status = status;
            this.confidence = confidence;
            this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons));
            this.knownIssues = Collections.unmodifiableList(new ArrayList<>(issues));
            this.fallbackRecommendations = Collections.unmodifiableList(new ArrayList<>(fallback));
            this.summary = summary == null ? "" : summary;
        }
    }
}
