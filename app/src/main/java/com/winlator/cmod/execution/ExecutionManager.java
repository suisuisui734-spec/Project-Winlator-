package com.winlator.cmod.execution;

import java.io.File;
import java.io.IOException;

/** Coordinates PE analysis and conservative runtime planning without changing container settings. */
public final class ExecutionManager {
    public static final class Analysis {
        public final SmartExeDetector.ExecutableProfile executable;
        public final RuntimeSelector.RuntimePlan runtime;

        private Analysis(SmartExeDetector.ExecutableProfile executable, RuntimeSelector.RuntimePlan runtime) {
            this.executable = executable;
            this.runtime = runtime;
        }

        public String getSummary() {
            return executable.getSummary() + "\nRuntime: " + runtime.runtime
                    + " (" + runtime.emulator + ")\n" + runtime.reason;
        }
    }

    private ExecutionManager() {}

    public static Analysis analyze(File executable, String configuredEmulator,
                                   boolean wineWow64Available, boolean arm64EcAvailable) throws IOException {
        SmartExeDetector.ExecutableProfile profile = SmartExeDetector.inspect(executable);
        RuntimeSelector.RuntimePlan plan = RuntimeSelector.select(
                profile.pe.architecture, configuredEmulator, wineWow64Available, arm64EcAvailable);
        return new Analysis(profile, plan);
    }
}
