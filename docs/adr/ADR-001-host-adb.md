# ADR-001 — El celular y el escritorio actúan como host ADB

## Contexto
Android es normalmente el *target* de ADB, no el host. El producto necesita controlar
una TV u otro Android desde un celular sin PC.

## Decisión
Implementar un cliente ADB nativo (no un servidor ADB) en ambos clientes:

- Android: `dadb` sobre TCP (`DadbShell`), detrás de la interfaz `AdbShell`.
- Escritorio: `platform-tools` empaquetado, invocado con `spawn`.

## Consecuencias
- (+) No se necesita root ni instalar nada en el dispositivo objetivo.
- (+) Misma semántica que `adb shell`.
- (−) El usuario debe activar depuración por red en el objetivo.
- (−) No hay USB en Android (requiere USB host + transporte crudo): fuera de alcance.
