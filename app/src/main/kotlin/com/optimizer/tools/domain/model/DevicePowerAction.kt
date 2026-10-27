package com.optimizer.tools.domain.model

enum class DevicePowerAction {
    REBOOT,
    SHUTDOWN,
    RECOVERY,
    BOOTLOADER,

    /** Reinicia solo la interfaz del sistema y comprueba que el proceso cambio de PID. */
    RESTART_SYSTEM_UI,
}
