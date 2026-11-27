package com.winlator.cmod.execution;

import java.util.Locale;

/**
 * Conservative runtime planner. An x86 PE is never assigned to Box64/FEXCore
 * alone: the selected Wine prefix must explicitly advertise WoW64 support.
 */
public final class RuntimeSelector {
    public enum RuntimeKind { WINE_X86_64, WINE_WOW64, WINE_ARM64EC, UNSUPPORTED }

    public static final class RuntimePlan {
        public final RuntimeKind runtime;
        public final String emulator;
        public final boolean supported;
        public final String reason;

        private RuntimePlan(RuntimeKind runtime, String emulator, boolean supported, String reason) {
            this.runtime = runtime;
            this.emulator = emulator;
            this.supported = supported;
            this.reason = reason;
        }
    }

    private RuntimeSelector() {}

    public static RuntimePlan select(PEArchitectureParser.Architecture architecture,
                                     String configuredEmulator, boolean wineWow64Available,
                                     boolean arm64EcAvailable) {
        String emulator = normalizeEmulator(configuredEmulator);
        if (architecture == null) architecture = PEArchitectureParser.Architecture.UNKNOWN;
        switch (architecture) {
            case X86_64:
                return new RuntimePlan(RuntimeKind.WINE_X86_64, emulator, true,
                        "Use 64-bit Wine and the container's configured ARM64 x86-64 translator.");
            case X86:
                if (!wineWow64Available) {
                    return new RuntimePlan(RuntimeKind.UNSUPPORTED, emulator, false,
                            "A 32-bit x86 executable requires a Wine WoW64-capable prefix; Box64/FEXCore alone is not sufficient.");
                }
                return new RuntimePlan(RuntimeKind.WINE_WOW64, emulator, true,
                        "Use Wine WoW64 with the configured translator and the prefix's 32-bit compatibility files.");
            case ARM64EC:
                if (!arm64EcAvailable) {
                    return new RuntimePlan(RuntimeKind.UNSUPPORTED, emulator, false,
                            "ARM64EC execution is unavailable in the selected Wine/runtime profile.");
                }
                return new RuntimePlan(RuntimeKind.WINE_ARM64EC, emulator, true,
                        "Use the existing ARM64EC Wine path.");
            case ARM64:
                return new RuntimePlan(RuntimeKind.UNSUPPORTED, emulator, false,
                        "A native ARM64 Windows PE is not interchangeable with an x86/x64 Wine application.");
            case UNKNOWN:
            default:
                return new RuntimePlan(RuntimeKind.UNSUPPORTED, emulator, false,
                        "The PE machine type is unknown; no runtime was selected.");
        }
    }

    private static String normalizeEmulator(String emulator) {
        if (emulator == null || emulator.trim().isEmpty()) return "FEXCore";
        String lower = emulator.trim().toLowerCase(Locale.ROOT);
        if (lower.contains("box64")) return "Box64";
        if (lower.contains("fex")) return "FEXCore";
        return emulator.trim();
    }
}
