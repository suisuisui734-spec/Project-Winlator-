package com.winlator.cmod;

import android.content.Context;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.os.Process;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.cmod.execution.DependencyDetector;
import com.winlator.cmod.maliforge.gx.GXBackendSelector;
import com.winlator.cmod.maliforge.gx.GXEngineReport;
import com.winlator.cmod.maliforge.gx.GXGraphicsDriverAvailability;
import com.winlator.cmod.maliforge.gx.GXNativeRuntime;
import com.winlator.cmod.maliforge.gx.GXVulkanDeviceBootstrap;
import com.winlator.cmod.maliforge.gx.GXVulkanCommandTransferTest;
import com.winlator.cmod.maliforge.gx.GXVulkanImageClearTest;
import com.winlator.cmod.maliforge.gx.GXRuntimeDiagnostics;
import com.winlator.cmod.maliforge.gx.MaliForgeGXEngine;

import java.util.Locale;

/**
 * Read-only MaliForge GX status page.
 *
 * This is deliberately separate from the Graphics Driver selector: GX does not yet ship
 * a game renderer or Vulkan ICD, and this screen must never imply that it is selectable.
 */
public final class MaliForgeGXControlCenterFragment extends Fragment {
    private LinearLayout sections;
    private int surfaceColor;
    private int textColor;
    private int accentColor;

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (getActivity() instanceof AppCompatActivity) {
            AppCompatActivity activity = (AppCompatActivity) getActivity();
            if (activity.getSupportActionBar() != null) {
                activity.getSupportActionBar().setTitle("MaliForge GX Control Center");
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        surfaceColor = ThemeManager.getSurfaceColor(context);
        textColor = ThemeManager.getOnSurfaceTextColor(context);
        accentColor = ThemeManager.getAccentColor(context);

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(surfaceColor);

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int outerPadding = dp(context, 16);
        root.setPadding(outerPadding, outerPadding, outerPadding, outerPadding);

        TextView title = new TextView(context);
        title.setText("MaliForge GX");
        title.setTextColor(textColor);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        root.addView(title, matchWrap());

        TextView subtitle = new TextView(context);
        subtitle.setText("Experimental diagnostics and capability overview");
        subtitle.setTextColor(textColor);
        subtitle.setAlpha(0.78f);
        subtitle.setTextSize(14);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(context, 4);
        root.addView(subtitle, subtitleParams);

        Button refresh = new Button(context);
        refresh.setText("Refresh status");
        refresh.setOnClickListener(v -> refreshReport(context));
        LinearLayout.LayoutParams refreshParams = matchWrap();
        refreshParams.topMargin = dp(context, 12);
        root.addView(refresh, refreshParams);

        Button runVulkanCommandTest = new Button(context);
        runVulkanCommandTest.setText("Run Vulkan buffer-copy test");
        runVulkanCommandTest.setOnClickListener(v -> {
            runVulkanCommandTest.setEnabled(false);
            Toast.makeText(context, "Running Android host Vulkan command test...",
                    Toast.LENGTH_SHORT).show();
            Handler mainHandler = new Handler(Looper.getMainLooper());
            new Thread(() -> {
                GXVulkanCommandTransferTest.Result result = GXVulkanCommandTransferTest.run();
                mainHandler.post(() -> {
                    runVulkanCommandTest.setEnabled(true);
                    if (!isAdded() || sections == null) return;
                    Context activeContext = getContext();
                    if (activeContext == null) return;
                    addSection(activeContext, "Vulkan command-buffer transfer test",
                            result.toDiagnosticText()
                            + "\n\nThis test runs on the Android host Vulkan device only. "
                            + "It verifies a buffer-copy command, completion fence, and read-back bytes; "
                            + "it does not render or present a Wine game frame.");
                    Toast.makeText(activeContext,
                            result.ready ? "Vulkan command test passed"
                                    : "Vulkan command test not verified: " + result.status,
                            Toast.LENGTH_LONG).show();
                });
            }, "MaliForge-GX-Vulkan-Test").start();
        });
        LinearLayout.LayoutParams commandTestParams = matchWrap();
        commandTestParams.topMargin = dp(context, 4);
        root.addView(runVulkanCommandTest, commandTestParams);

        Button runVulkanImageTest = new Button(context);
        runVulkanImageTest.setText("Run Vulkan image clear/readback test");
        runVulkanImageTest.setOnClickListener(v -> {
            runVulkanImageTest.setEnabled(false);
            Toast.makeText(context, "Running Android host Vulkan image test...",
                    Toast.LENGTH_SHORT).show();
            Handler mainHandler = new Handler(Looper.getMainLooper());
            new Thread(() -> {
                GXVulkanImageClearTest.Result result = GXVulkanImageClearTest.run();
                mainHandler.post(() -> {
                    runVulkanImageTest.setEnabled(true);
                    if (!isAdded() || sections == null) return;
                    Context activeContext = getContext();
                    if (activeContext == null) return;
                    addSection(activeContext, "Vulkan offscreen image clear/read-back test",
                            result.toDiagnosticText()
                            + "\n\nThis test allocates and clears an Android host Vulkan image, "
                            + "then copies and verifies its pixels. It does not use a shader, graphics "
                            + "pipeline, surface, swapchain, or Wine game renderer.");
                    Toast.makeText(activeContext,
                            result.ready ? "Vulkan image test passed"
                                    : "Vulkan image test not verified: " + result.status,
                            Toast.LENGTH_LONG).show();
                });
            }, "MaliForge-GX-Vulkan-Image-Test").start();
        });
        LinearLayout.LayoutParams imageTestParams = matchWrap();
        imageTestParams.topMargin = dp(context, 4);
        root.addView(runVulkanImageTest, imageTestParams);

        Button runVulkanRenderPassTest = new Button(context);
        runVulkanRenderPassTest.setText("Run Vulkan render-pass attachment test");
        runVulkanRenderPassTest.setOnClickListener(v -> {
            runVulkanRenderPassTest.setEnabled(false);
            Toast.makeText(context, "Running Android host Vulkan render-pass test...",
                    Toast.LENGTH_SHORT).show();
            Handler mainHandler = new Handler(Looper.getMainLooper());
            new Thread(() -> {
                GXVulkanRenderPassTest.Result result = GXVulkanRenderPassTest.run();
                mainHandler.post(() -> {
                    runVulkanRenderPassTest.setEnabled(true);
                    if (!isAdded() || sections == null) return;
                    Context activeContext = getContext();
                    if (activeContext == null) return;
                    addSection(activeContext, "Vulkan offscreen render-pass attachment test",
                            result.toDiagnosticText()
                            + "\n\nThis Android-host test creates a color attachment, image view, render pass, "
                            + "and framebuffer, executes vkCmdClearAttachments on the graphics queue, then "
                            + "verifies copied pixels. It does not use shaders, a game surface, swapchain, "
                            + "Wine guest, or game renderer.");
                    Toast.makeText(activeContext,
                            result.ready ? "Vulkan render-pass test passed"
                                    : "Vulkan render-pass test not verified: " + result.status,
                            Toast.LENGTH_LONG).show();
                });
            }, "MaliForge-GX-RenderPass-Test").start();
        });
        LinearLayout.LayoutParams renderPassTestParams = matchWrap();
        renderPassTestParams.topMargin = dp(context, 4);
        root.addView(runVulkanRenderPassTest, renderPassTestParams);

        LinearLayout reportActions = new LinearLayout(context);
        reportActions.setOrientation(LinearLayout.HORIZONTAL);
        Button shareReport = new Button(context);
        shareReport.setText("Share diagnostics");
        shareReport.setOnClickListener(v -> shareDiagnosticReport(context));
        reportActions.addView(shareReport, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button copyReport = new Button(context);
        copyReport.setText("Copy report");
        copyReport.setOnClickListener(v -> copyDiagnosticReport(context));
        reportActions.addView(copyReport, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams reportActionsParams = matchWrap();
        reportActionsParams.topMargin = dp(context, 4);
        root.addView(reportActions, reportActionsParams);

        sections = new LinearLayout(context);
        sections.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams sectionsParams = matchWrap();
        sectionsParams.topMargin = dp(context, 8);
        root.addView(sections, sectionsParams);

        scrollView.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        refreshReport(context);
        return scrollView;
    }

    private void refreshReport(Context context) {
        if (sections == null) return;
        sections.removeAllViews();
        try {
            GXEngineReport report = MaliForgeGXEngine.inspect(
                    context, DependencyDetector.GraphicsApi.UNKNOWN);
            GXRuntimeDiagnostics diagnostics = report.getRuntimeDiagnostics();

            if (diagnostics == null) {
                addSection(context, "Runtime report", "Diagnostics are unavailable.");
                return;
            }

            GXNativeRuntime.Snapshot nativeRuntime = diagnostics.getNativeRuntime();
            addSection(context, "GPU and Vulkan (Android host)",
                    "Physical-device name: " + safe(report.getCapabilities().getRenderer()) + "\n"
                    + "Vulkan vendor ID: " + formatVendorId(report.getCapabilities().getVendorId()) + "\n"
                    + "Mali GPU detected: " + report.getCapabilities().isMali() + "\n"
                    + "Vulkan device API version: " + safe(report.getCapabilities().getVulkanVersion()) + "\n"
                    + "VK_KHR_swapchain: " + observed(report.getCapabilities().hasSwapchainExtension()) + "\n"
                    + "VK_KHR_synchronization2: " + observed(report.getCapabilities().hasSynchronization2Extension()) + "\n"
                    + "Device extensions observed: " + report.getCapabilities().getExtensionCount() + "\n"
                    + "Capability probe: " + report.getCapabilities().getStatus()
                    + "\n\nThese values come from the Android system Vulkan physical-device probe, not from a Wine game's renderer.");

            addSection(context, "Android device and process",
                    "Manufacturer / model: " + safe(Build.MANUFACTURER) + " / " + safe(Build.MODEL) + "\n"
                    + "Hardware identifier: " + safe(Build.HARDWARE) + "\n"
                    + "Android release: " + safe(Build.VERSION.RELEASE) + " (API " + Build.VERSION.SDK_INT + ")\n"
                    + "Application process: " + (Process.is64Bit() ? "64-bit" : "32-bit") + "\n"
                    + "System-supported ABIs: " + String.join(", ", Build.SUPPORTED_ABIS)
                    + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                            ? "\nSoC model reported by Android: " + safe(Build.SOC_MODEL) : ""));

            addSection(context, "Native ARM64 support bridge",
                    "Bridge state: " + nativeRuntime.getStateName() + "\n"
                    + "ABI: " + safe(nativeRuntime.abi) + "\n"
                    + "Android API: " + (nativeRuntime.androidApiLevel < 0
                            ? "Unavailable" : nativeRuntime.androidApiLevel) + "\n"
                    + "Error code: " + nativeRuntime.errorCode + "\n"
                    + "Graphics backend available: " + nativeRuntime.graphicsBackendAvailable + "\n\n"
                    + "A READY bridge only confirms ABI/API support checks. It does not mean a game renderer is loaded.");

            GXBackendSelector.Selection selection = report.getBackendSelection();
            String selectionText = selection == null
                    ? "Backend selection: unavailable"
                    : "Requested backend: " + safe(selection.requestedBackendId) + "\n"
                    + "Selected plan: " + safe(selection.selectedBackendId) + "\n"
                    + "Status: " + selection.status + "\n"
                    + "Fallback recommended: " + selection.fallbackRecommended + "\n"
                    + "Initialization verified: " + selection.initializationVerified + "\n"
                    + "Detail: " + safe(selection.detail);
            if (report.getBackendPlan() != null) {
                selectionText += "\nExisting backend recommendation: "
                        + report.getBackendPlan().getBackend()
                        + "\nBaseline validated: " + report.getBackendPlan().isBaselineValidated()
                        + "\nManual confirmation required: "
                        + report.getBackendPlan().requiresManualConfirmation();
            }
            addSection(context, "Native Vulkan host preflight",
                    nativeRuntime.hostVulkanProbe.toDiagnosticText()
                    + "\n\nThis query uses the Android host Vulkan loader. It does not create a game device, "
                    + "swapchain, Wine renderer, or Vulkan ICD.");

            GXVulkanDeviceBootstrap.Result deviceBootstrap = nativeRuntime.vulkanDeviceBootstrap;
            addSection(context, "Native Vulkan logical-device bootstrap",
                    deviceBootstrap.toDiagnosticText()
                    + "\n\nThis holds an Android host VkDevice and graphics queue for the app process. "
                    + "It is not a Wine guest device, does not create a game surface or swapchain, "
                    + "and does not render game frames.");

            addSection(context, "Backend planning (advisory only)", selectionText
                    + "\n\nThis planner does not change container settings or switch your selected wrapper.");

            GXGraphicsDriverAvailability gxDriver = GXGraphicsDriverAvailability.inspect(null);
            addSection(context, "MaliForge GX Graphics Driver lifecycle",
                    "Registry ID: " + gxDriver.getDriverId() + "\n"
                    + "Version: " + gxDriver.getVersion() + "\n"
                    + "Availability: " + gxDriver.getStatusLabel() + "\n"
                    + "Renderer package bundled: " + gxDriver.isPackageBundled() + "\n"
                    + "Runtime initializer present: " + gxDriver.hasRuntimeInitializer() + "\n"
                    + "Selected by container: no (not in the supported driver allow-list)\n"
                    + "Initialization verified: " + gxDriver.isInitializationVerified() + "\n"
                    + "Active renderer: no\n"
                    + "Detected Android Mali GPU: " + report.getCapabilities().isMali() + "\n"
                    + "Reason: " + gxDriver.getDetail()
                    + "\n\nAndroid host Vulkan detection does not prove that a Wine game renderer is installed or active.");

            addSection(context, "Diagnostic Vulkan layer",
                    "An opt-in pass-through diagnostics layer exists for Linux x86_64 Wine guest processes. "
                    + "It records Vulkan lifecycle information; it is not an Android Mali driver, Vulkan ICD, "
                    + "renderer, or FPS optimizer.");

            addSection(context, "Performance telemetry",
                    "Live game FPS, frame time, GPU load, thermal behavior, and gamepad event telemetry are "
                    + "not connected to a verified game-renderer provider. Values are therefore UNKNOWN / "
                    + "NOT MEASURED rather than guessed.");

            addSection(context, "Driver and data safety",
                    "This Control Center is read-only. It does not replace or modify Wrapper, Wrapper-1.1, "
                    + "Wrapper-v2, Wrapper-leegao, Wrapper-gamenative, DXVK, VKD3D, WineD3D, driver archives, "
                    + "container settings, game saves, or existing shader caches.");
        } catch (RuntimeException | LinkageError error) {
            addSection(context, "Diagnostics unavailable",
                    "MaliForge GX could not read the current status. Existing graphics-driver settings were not changed.\n\n"
                    + error.getClass().getSimpleName() + ": " + safe(error.getMessage()));
        }
    }

    private String composeDiagnosticReport() {
        StringBuilder report = new StringBuilder("MaliForge GX diagnostic report\n");
        report.append("Generated by Project Winlator Control Center\n")
                .append("Note: report contains device/system details; review before sharing.\n\n");
        if (sections != null) {
            for (int i = 0; i < sections.getChildCount(); i++) {
                View child = sections.getChildAt(i);
                if (child instanceof TextView) {
                    report.append(((TextView) child).getText()).append("\n\n");
                }
            }
        }
        return report.toString();
    }

    private void shareDiagnosticReport(Context context) {
        String report = composeDiagnosticReport();
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "Project Winlator diagnostics");
        intent.putExtra(Intent.EXTRA_TEXT, report);
        startActivity(Intent.createChooser(intent, "Share diagnostic report"));
    }

    private void copyDiagnosticReport(Context context) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(context, "Clipboard unavailable", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Winlator diagnostics", composeDiagnosticReport()));
        Toast.makeText(context, "Diagnostic report copied", Toast.LENGTH_SHORT).show();
    }

    private void addSection(Context context, String heading, String body) {
        TextView card = new TextView(context);
        card.setText(heading + "\n\n" + body);
        card.setTextColor(textColor);
        card.setTextSize(14);
        card.setLineSpacing(dp(context, 2), 1.0f);
        card.setPadding(dp(context, 14), dp(context, 14), dp(context, 14), dp(context, 14));
        card.setTextIsSelectable(true);

        GradientDrawable background = new GradientDrawable();
        background.setColor(adjustAlpha(surfaceColor, 0.96f));
        background.setCornerRadius(dp(context, 14));
        background.setStroke(dp(context, 1), adjustAlpha(accentColor, 0.38f));
        card.setBackground(background);

        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(context, 10);
        sections.addView(card, params);
    }

    private static int adjustAlpha(int color, float factor) {
        int alpha = Math.round(android.graphics.Color.alpha(color) * factor);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    private static String safe(String value) {
        return value == null || value.trim().isEmpty() ? "Unavailable" : value;
    }

    private static String observed(boolean available) {
        return available ? "Detected" : "Not detected";
    }

    private static String formatVendorId(int vendorId) {
        return vendorId == 0 ? "Unavailable" : String.format(Locale.ROOT, "0x%04X", vendorId);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
