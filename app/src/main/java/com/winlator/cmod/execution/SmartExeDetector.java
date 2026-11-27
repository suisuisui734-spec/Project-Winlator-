package com.winlator.cmod.execution;

import java.io.File;
import java.io.IOException;

/** Read-only analysis of a host-accessible Windows PE executable. */
public final class SmartExeDetector {
    public static final class ExecutableProfile {
        public final File file;
        public final PEArchitectureParser.Result pe;
        public final DependencyDetector.Result dependencies;

        private ExecutableProfile(File file, PEArchitectureParser.Result pe,
                                  DependencyDetector.Result dependencies) {
            this.file = file;
            this.pe = pe;
            this.dependencies = dependencies;
        }

        public String getSummary() {
            return "PE " + pe.architecture + ", machine=0x" + Integer.toHexString(pe.machine)
                    + ", imports=" + pe.importedLibraries.size() + ", graphics=" + dependencies.graphicsApis;
        }
    }

    private SmartExeDetector() {}

    public static ExecutableProfile inspect(File executable) throws IOException {
        PEArchitectureParser.Result pe = PEArchitectureParser.parse(executable);
        if (pe.dynamicLibrary) throw new IOException("The selected PE file is a DLL, not a standalone executable");
        return new ExecutableProfile(executable, pe, DependencyDetector.detect(pe.importedLibraries));
    }
}
