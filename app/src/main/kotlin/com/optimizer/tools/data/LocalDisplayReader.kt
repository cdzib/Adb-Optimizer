package com.optimizer.tools.data

import android.content.Context
import android.os.Build
import android.util.DisplayMetrics
import android.view.WindowManager
import dev.adboptimizer.core.model.DeviceDisplayInfo
import dev.adboptimizer.core.model.DisplayModeInfo

/** Panel facts of the phone running the app, including size/density overrides. */
internal class LocalDisplayReader(
    private val context: Context,
) {
    fun readInfo(): DeviceDisplayInfo? =
        runCatching {
            val display =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    context.display
                } else {
                    @Suppress("DEPRECATION")
                    (context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay
                } ?: return null

            @Suppress("DEPRECATION")
            val metrics =
                DisplayMetrics().also { display.getRealMetrics(it) }

            @Suppress("DEPRECATION")
            val mode = display.mode
            val supportedModes =
                display.supportedModes
                    .map {
                        DisplayModeInfo(
                            widthPx = it.physicalWidth,
                            heightPx = it.physicalHeight,
                            refreshRateHz = it.refreshRate,
                        )
                    }.distinct()
                    .sortedWith(
                        compareByDescending<DisplayModeInfo> { it.widthPx }
                            .thenByDescending { it.heightPx }
                            .thenByDescending { it.refreshRateHz },
                    )
            DeviceDisplayInfo(
                physicalWidthPx = mode?.physicalWidth?.takeIf { it > 0 },
                physicalHeightPx = mode?.physicalHeight?.takeIf { it > 0 },
                physicalDensityDpi = DisplayMetrics.DENSITY_DEVICE_STABLE.takeIf { it > 0 },
                activeWidthPx = metrics.widthPixels.takeIf { it > 0 },
                activeHeightPx = metrics.heightPixels.takeIf { it > 0 },
                activeDensityDpi =
                    context.resources.displayMetrics.densityDpi
                        .takeIf { it > 0 },
                refreshRateHz = display.refreshRate.takeIf { it > 0f },
                modes = supportedModes,
            )
        }.getOrNull()
}
