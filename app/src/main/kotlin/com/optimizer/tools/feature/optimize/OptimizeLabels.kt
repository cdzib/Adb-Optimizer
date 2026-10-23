package com.optimizer.tools.feature.optimize

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.optimizer.tools.R
import com.optimizer.tools.core.ui.Translate
import dev.adboptimizer.core.model.Impact

@Composable
internal fun reasonLabel(
    t: Translate,
    reason: String?,
): String =
    when (reason) {
        "requiresRoot" -> t("optimize.requiresRoot")
        "profile" -> stringResource(R.string.optimize_reason_profile)
        "minSdk" -> stringResource(R.string.optimize_reason_min_sdk)
        "maxSdk" -> stringResource(R.string.optimize_reason_max_sdk)
        "tvOnly" -> stringResource(R.string.optimize_reason_tv_only)
        "phoneOnly" -> stringResource(R.string.optimize_reason_phone_only)
        "manufacturer" -> stringResource(R.string.optimize_reason_manufacturer)
        else -> t("optimize.notSupported")
    }

internal val impactLabel: (Translate, Impact) -> String = { t, impact ->
    t("optimize.impact.${impact.name.lowercase()}")
}
