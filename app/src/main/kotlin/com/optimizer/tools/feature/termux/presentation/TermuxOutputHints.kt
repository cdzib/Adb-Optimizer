package com.optimizer.tools.feature.termux.presentation

import com.optimizer.tools.R

/** Pistas accionables para los fallos más comunes de pkg/dpkg en Termux. */
internal enum class TermuxOutputHint(
    val messageRes: Int,
) {
    NO_SPACE(R.string.termux_hint_space),
    DPKG(R.string.termux_hint_dpkg),
    MIRROR(R.string.termux_hint_mirror),
    LOCK(R.string.termux_hint_lock),
    STORAGE(R.string.termux_hint_storage),
}

internal fun hintsFor(output: String): List<TermuxOutputHint> {
    val text = output.lowercase()
    return buildList {
        if ("no space left" in text || "insufficient storage" in text) add(TermuxOutputHint.NO_SPACE)
        if ("dpkg returned an error code" in text || "dpkg: error" in text) add(TermuxOutputHint.DPKG)
        if ("hash sum mismatch" in text || "failed to fetch" in text || "could not resolve" in text) {
            add(TermuxOutputHint.MIRROR)
        }
        if ("could not get lock" in text || "unable to lock" in text) add(TermuxOutputHint.LOCK)
        if ("termux-setup-storage" in text || ("permission denied" in text && "/storage" in text)) {
            add(TermuxOutputHint.STORAGE)
        }
    }
}
