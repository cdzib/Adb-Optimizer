package com.optimizer.tools.feature.termux.presentation

import com.optimizer.tools.R

/**
 * allow-external-apps solo puede activarlo el usuario dentro de Termux: el archivo vive en
 * su directorio privado y ni ADB ni Shizuku pueden escribirlo sin root.
 */
const val TERMUX_EXTERNAL_APPS_COMMAND =
    "mkdir -p ~/.termux && grep -q '^allow-external-apps=true' ~/.termux/termux.properties 2>/dev/null || " +
        "echo 'allow-external-apps=true' >> ~/.termux/termux.properties; termux-reload-settings"

/** Recetas listas para ejecutar en Termux desde la pantalla de integración. */
enum class TermuxPreset(
    val titleRes: Int,
    val command: String,
) {
    TOOLS(
        R.string.termux_preset_tools,
        // Instala uno por uno para saber cuál falla sin cortar los demás.
        "pkg update -y >/dev/null 2>&1 || true; fail=0; " +
            "for p in termux-api jq python ffmpeg; do echo \"==> \$p\"; " +
            "pkg install -y \"\$p\" || fail=1; done; " +
            "if [ \$fail -ne 0 ]; then echo 'Algún paquete falló: revisa el detalle arriba'; fi; exit \$fail",
    ),
    REPAIR(
        R.string.termux_preset_repair,
        "pkg update -y || true; apt --fix-broken install -y; dpkg --configure -a; pkg install -f -y",
    ),
    STORAGE(
        R.string.termux_preset_storage,
        // Termux pide el permiso de almacenamiento y crea ~/storage (necesario para /sdcard).
        "termux-setup-storage && ls -d ~/storage/shared",
    ),
    SSH(
        R.string.termux_preset_ssh,
        "pkg install -y openssh && sshd && echo 'sshd escuchando en el puerto 8022'",
    ),
    BACKUP(
        R.string.termux_preset_backup,
        "if [ ! -d ~/storage/shared ]; then " +
            "echo 'Falta termux-setup-storage: ejecuta la receta Preparar almacenamiento'; exit 2; fi; " +
            "mkdir -p ~/storage/shared/Backup && " +
            "tar czf ~/storage/shared/Backup/respaldo-\$(date +%Y%m%d-%H%M).tgz " +
            "~/storage/shared/DCIM && echo respaldo listo",
    ),
}
