package com.winlator.cmod;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.winlator.cmod.execution.DependencyDetector;
import com.winlator.cmod.maliforge.gx.GXBackendSelector;
import com.winlator.cmod.maliforge.gx.GXEngineReport;
import com.winlator.cmod.maliforge.gx.GXNativeRuntime;
import com.winlator.cmod.maliforge.gx.GXRuntimeDiagnostics;
import com.winlator.cmod.maliforge.gx.MaliForgeGXEngine;

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
            addSection(context, "GPU and Vulkan",
                    "GPU renderer: " + safe(report.getCapabilities().getRenderer()) + "\n"
                    + "Mali GPU detected: " + report.getCapabilities().isMali() + "\n"
                    + "Vulkan version: " + safe(report.getCapabilities().getVulkanVersion()) + "\n"
                    + "Vulkan extensions observed: " + report.getCapabilities().getExtensionCount() + "\n"
                    + "Capability probe: " + report.getCapabilities().getStatus());

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
            addSection(context, "Backend planning (advisory only)", selectionText
                    + "\n\nThis planner does not change container settings or switch your selected wrapper.");

            addSection(context, "MaliForge game renderer",
                    "NOT AVAILABLE — no MaliForge Vulkan ICD or game-renderer hook is installed.\n\n"
                    + "MaliForge GX therefore does not appear as a selectable Graphics Driver. "
                    + "Keep using the existing Wrapper / DXVK / VKD3D / WineD3D path that works for your game.");

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

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }
}
