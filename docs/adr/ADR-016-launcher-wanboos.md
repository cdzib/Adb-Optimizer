# ADR-016: Launchers para Android TV/Google TV y WanboOS de sistema

Estado: Aceptado

## Contexto

La función Launcher debe ser útil en Android TV y Google TV de distintos fabricantes,
no solo en Wanbo. La instalación normal de un APK es distinta de reemplazar un launcher
de sistema.

Wanbo T6 Max (`marconi`, Android 9, `userdebug/test-keys`) requiere instalar
`com.tv.launcher.custom` en `/system` y deshabilitar `com.google.android.tvlauncher`.
Este flujo no es equivalente a instalar una aplicación normal: depende de `adb root`,
`adb remount`, preservar el APK Home anterior y revertirlo ante un fallo.

## Decisión

- El catálogo compartido representa la operación con el tipo de paso
  `installSystemApk`, con rutas, permisos, propietario, contexto SELinux y paquetes a
  deshabilitar explícitos.
- Para Android TV y Google TV en general, Electron instala el APK con `adb install`,
  detecta las actividades `MAIN`/`HOME` disponibles y permite solicitar el cambio de
  Home. El sistema o fabricante puede rechazarlo; ese rechazo se informa sin intentar
  modificar `/system`.
- El cliente Electron es el único que puede orquestar comandos host ADB (`root`,
  `remount`, `push` y rollback), con preflight de `marconi`/`userdebug`, verificación
  del Home y restauración del APK anterior antes de reiniciar.
- Android conserva el flujo de `pm install` y la selección de Home para TVs compatibles. No intenta
  montar ni escribir `/system`: `dadb` corre como uid 2000, sin `adb root`.
- No se cambia ni se modifica la OTA. AVB/verity y el mecanismo OTA permanecen fuera de
  alcance.

## Consecuencias

- La receta requiere root y es de riesgo avanzado; los dispositivos no compatibles no
  deben ejecutar el paso.
- Cualquier cambio de partición de sistema debe mantener un rollback independiente del
  launcher que se está instalando.
- La app Android puede instalar el APK como usuario con `pm install`, pero no convertirlo
  en aplicación del sistema.
