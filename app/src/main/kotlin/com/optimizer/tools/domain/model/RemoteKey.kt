package com.optimizer.tools.domain.model

/** Teclas del control remoto y su keyevent de Android. */
enum class RemoteKey(
    val keycode: String,
) {
    UP("KEYCODE_DPAD_UP"),
    DOWN("KEYCODE_DPAD_DOWN"),
    LEFT("KEYCODE_DPAD_LEFT"),
    RIGHT("KEYCODE_DPAD_RIGHT"),
    OK("KEYCODE_DPAD_CENTER"),
    BACK("KEYCODE_BACK"),
    HOME("KEYCODE_HOME"),
    MENU("KEYCODE_MENU"),
    VOLUME_UP("KEYCODE_VOLUME_UP"),
    VOLUME_DOWN("KEYCODE_VOLUME_DOWN"),
    PLAY_PAUSE("KEYCODE_MEDIA_PLAY_PAUSE"),
}
