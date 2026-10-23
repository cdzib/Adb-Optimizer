package com.optimizer.tools.core.ui.video

import com.optimizer.tools.R
import com.optimizer.tools.core.ui.UiText
import com.optimizer.tools.core.ui.uiText

/** Estado visual compartido por el espejo y el control remoto. */
data class RemoteVideoState(
    val status: UiText = uiText(R.string.video_status_connecting),
    val width: Int = 0,
    val height: Int = 0,
    val live: Boolean = false,
    val error: UiText? = null,
    val touchAllowed: Boolean = true,
    val controlHint: UiText = uiText(R.string.video_control_checking),
)
