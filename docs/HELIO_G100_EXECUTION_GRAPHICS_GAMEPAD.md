# Helio G100 — execution, graphics and integrated gamepad

## Existing Android overlay retained

The display session already composes XServerView, TouchpadView and InputControlsView in one Android FrameLayout. InputControlsView is an Android-side transparent overlay; it is not drawn by Wine, the Windows game, DXVK or the guest Vulkan process. Existing .icp controls support multi-touch, editable positions and sizes, style/opacity settings, keyboard/gamepad bindings, and event injection through Winlator's input bridge.

This iteration extends the existing overlay instead of adding a second floating window. A profile switch, visibility loss or view detach now releases active pointer-owned controls, cancels pending redraw/timeout callbacks, and stops the mouse movement timer. A controller profile can be remembered per shortcut identity using app preferences, while existing explicit shortcut controlsProfile values remain the fallback for shortcuts without a saved preference.

## Windows executable inspection

PEArchitectureParser reads the DOS header, COFF machine, optional header, section table and a bounded number of import descriptors. It recognizes x86, x86_64, ARM64 and ARM64EC machine types. SmartExeDetector and DependencyDetector summarize imported DLLs that suggest DirectX, OpenGL, Vulkan, XInput, DirectInput and Visual C runtime requirements.

RuntimeSelector intentionally does not launch an executable. x86 requires an explicitly confirmed Wine WoW64-capable prefix; Box64 or FEXCore alone is not considered sufficient. ARM64EC likewise needs an explicitly confirmed compatible path. The analysis API expects a host-accessible File; it does not reinterpret DOS paths inside a Wine container. Automatic interception of every shortcut and file-manager launch path is not enabled in this iteration.

## Vulkan and Mali policy

GraphicsCapabilityDetector queries the active Android system renderer, Vulkan version and reported extension set through the existing GPUInformation JNI interface. DXVK recommendations require a conservative Vulkan 1.1 plus swapchain baseline. VKD3D recommendations require Vulkan 1.2 or newer, swapchain and synchronization2 support (direct extension or Vulkan 1.3). These are screening baselines, not proof of complete game compatibility.

When the active DX wrapper is a known DXVK/VKD3D choice and the detected Vulkan version fails the baseline, GraphicsFallbackManager selects the existing WineD3D fallback, or disables only the VKD3D component in a recognized compound wrapper string. An unknown Vulkan probe preserves the user's setting. No Turnip/Adreno-only package is selected on Mali and no driver is installed or replaced.

## Automated validation and limitations

New unit tests cover PE machine parsing, malformed PE input, imported API detection, conservative x86/WoW64 runtime selection, Vulkan baseline checks, unknown-probe fallback and stable per-shortcut profile identity. Existing native/Java input and surface code remains the underlying execution path.

A physical Helio G100 / Mali-G57 MC2 device is still required to validate actual driver extension exposure, input latency, overlay GPU cost, game compatibility, thermal behavior, FPS and frame pacing. CI build success must not be interpreted as a real-device benchmark.
