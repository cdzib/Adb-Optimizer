# ADB local (127.0.0.1:5555)

Estado: implementado.

## Objetivo

Permitir que la app use la depuración USB del propio equipo sin PC: detectar
`Settings.Global.ADB_ENABLED`, guiar al usuario, conectar por `127.0.0.1:5555`,
autorizar la llave RSA y ejecutar comandos shell.

## Capas

- `feature/localadb/domain`: `LocalAdbRepository`, modelos, errores y casos de uso
  `CheckAdbEnabled`, `ConnectAdb`, `RunShellCommand`.
- `feature/localadb/data`: `DadbLocalAdbRepository` sobre dadb, llaves en
  `filesDir/local-adb/adbkey(.pub)` y `LocalAdbShell` como `AdbShell`.
- `feature/localadb/presentation`: `LocalAdbViewModel` (MVVM+MVI, estados
  `DEBUGGING_OFF`, `CONNECTING`, `WAITING_AUTHORIZATION`, `CONNECTED`, `ERROR`) y
  `LocalAdbScreen`.
- Composición en `AppContainerLocalAdbEnvironment` y `AppContainer.localAdb`.

## Integración con el equipo local

- La sesión siempre es del **equipo local**: nunca se registra como dispositivo
  remoto ni cambia `AppTarget`.
- `AppContainer.localShell` usa la sesión ADB local cuando está conectada y cae a
  Shizuku si no lo está. Afecta a Apps, Optimizar, Archivos, Perfiles, Debloater y
  Consola.
- `AppContainer.localPrivilegeReady()` = Shizuku autorizado **o** ADB local
  conectado. Se usa para habilitar acciones y para decidir qué opciones se ocultan:
  - Optimizar muestra una guía en lugar de recetas sin permisos.
  - Energía muestra una guía en lugar de acciones sin permisos.
  - Apps oculta las acciones de escritura hasta tener privilegio.
  - ADB local oculta comandos y salida hasta estar conectado.
- Al conectar, Inicio muestra “ADB local · Conectado” en lugar de la fila de
  Shizuku.

## Límites conocidos

- Una app no puede activar la depuración USB: solo se detecta y se abre Ajustes.
- adbd debe escuchar en TCP 5555 (`adb tcpip 5555` desde PC o depuración por red).
- La primera conexión exige aceptar el diálogo RSA (“Permitir siempre”); se espera
  hasta 60 s para que dé tiempo a aceptarlo.
- Tras autorizar, la app recuerda la llave y reconecta sola al abrir o volver
  (`onAppResumed`), sin volver a preguntar; también verifica la sesión y reconecta
  si adbd la cerró.
- Logcat, Doze y Ajustes del sistema siguen ligados a una sesión remota; su
  adaptación completa al shell local queda pendiente.
- dadb publica `junit-platform-native` en runtime; se excluye en Gradle para no
  empaquetar JUnit en el APK.
