package com.winlator.cmod.execution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Infers likely Windows graphics, input and VC runtime dependencies from PE import names. */
public final class DependencyDetector {
    public enum GraphicsApi {
        DIRECTDRAW, DIRECT3D8, DIRECT3D9, DIRECT3D10, DIRECT3D11, DIRECT3D12,
        OPENGL, VULKAN, UNKNOWN
    }

    public static final class Result {
        public final List<String> importedLibraries;
        public final Set<GraphicsApi> graphicsApis;
        public final boolean usesXInput, usesDirectInput, usesDirectSound, usesVisualCRuntime;

        private Result(List<String> imports, EnumSet<GraphicsApi> apis,
                       boolean xinput, boolean dinput, boolean dsound, boolean vcruntime) {
            importedLibraries = Collections.unmodifiableList(new ArrayList<>(imports));
            graphicsApis = Collections.unmodifiableSet(apis.clone());
            usesXInput = xinput;
            usesDirectInput = dinput;
            usesDirectSound = dsound;
            usesVisualCRuntime = vcruntime;
        }
    }

    private DependencyDetector() {}

    public static Result detect(List<String> importedLibraries) {
        List<String> imports = importedLibraries == null ? Collections.emptyList() : importedLibraries;
        EnumSet<GraphicsApi> apis = EnumSet.noneOf(GraphicsApi.class);
        boolean xinput = false, dinput = false, dsound = false, vcruntime = false;
        for (String library : imports) {
            if (library == null) continue;
            String name = library.trim().toLowerCase(Locale.ROOT);
            if (name.equals("ddraw.dll")) apis.add(GraphicsApi.DIRECTDRAW);
            else if (name.equals("d3d8.dll")) apis.add(GraphicsApi.DIRECT3D8);
            else if (name.equals("d3d9.dll")) apis.add(GraphicsApi.DIRECT3D9);
            else if (name.equals("d3d10.dll") || name.equals("d3d10core.dll")) apis.add(GraphicsApi.DIRECT3D10);
            else if (name.equals("d3d11.dll")) apis.add(GraphicsApi.DIRECT3D11);
            else if (name.equals("d3d12.dll")) apis.add(GraphicsApi.DIRECT3D12);
            else if (name.equals("opengl32.dll")) apis.add(GraphicsApi.OPENGL);
            else if (name.equals("vulkan-1.dll")) apis.add(GraphicsApi.VULKAN);
            if (name.startsWith("xinput")) xinput = true;
            if (name.startsWith("dinput")) dinput = true;
            if (name.equals("dsound.dll")) dsound = true;
            if (name.startsWith("vcruntime") || name.startsWith("msvcp")
                    || name.startsWith("msvcr") || name.startsWith("api-ms-win-crt")) vcruntime = true;
        }
        if (apis.isEmpty()) apis.add(GraphicsApi.UNKNOWN);
        return new Result(imports, apis, xinput, dinput, dsound, vcruntime);
    }
}
