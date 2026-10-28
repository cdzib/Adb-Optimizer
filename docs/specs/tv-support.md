# Soporte de televisiones (Android TV y Google TV)

Estado: plan de capacidades. Presentación nativa TV iniciada tras aprobación del
rediseño; las fases de detección remota y protección del launcher no se consideran
implementadas por añadir la interfaz. Ver `mobile-tv-redesign-implementation.md`.

## Evidencia

Medido sobre el emulador `sdk_google_atv64_arm64` (Android TV, SDK 36):

| Señal | Valor real |
|---|---|
| `getprop ro.build.characteristics` | `emulator` (no contiene `tv`) |
| `pm list features` | `android.software.leanback`, `android.software.leanback_only`, `android.hardware.type.television` |
| `dumpsys sensorservice` | `No Sensors on the device` |
| `dumpsys battery` | sin `level`; solo estado de alimentación |

La detección actual (`core/adb/.../AdbParsers.kt`, `parseDeviceInfo`) usa solo
`characteristics.contains("tv")`, así que **esta TV se clasifica como teléfono**. Las
consecuencias observables son: recetas de TV ocultas, botón de launchers de TV ausente y
espejo tratado como teléfono.

`readInfo` ya ejecuta `pm list features` (línea siguiente a `parseDeviceInfo`), por lo que la
señal correcta está disponible sin comandos nuevos. Es un defecto de uso de datos, no de
transporte.

Lo que ya existe y funciona: conexión clásica `ip:5555` (`connectManual`, `secure = false`),
vinculación TLS por QR y código, teclas DPAD/media en `RemoteKey`, filtro `appliesTo.isTv` en
`Compatibility`, pantalla de launcher de TV y degradación de métricas sin batería ni sensores.

## Alcance

Dentro:
- Controlar una TV Android/Google TV desde el teléfono (destino remoto).
- Detección fiable del tipo de equipo.
- Protección del launcher activo para no dejar la TV sin interfaz.

Fuera:
- LG webOS y Samsung Tizen: no son Android.
- TVs sin depuración USB/red disponible.
- Encendido de un televisor apagado: ADB no puede; solo `KEYCODE_WAKEUP` si el equipo sigue
  en red.
- Ejecutar esta app *en* la TV se trata por separado en la Fase 3 y en el rediseño aprobado.

## Contrato (Fase 1)

- AC1: un equipo es TV si `ro.build.characteristics` contiene `tv` **o** `pm list features`
  publica `android.software.leanback` o `android.hardware.type.television`.
- AC2: un teléfono real sigue clasificándose como teléfono (sin leanback ni characteristics).
- AC3: el emulador Android TV del proyecto se clasifica como TV con `characteristics=emulator`.
- AC4: la app bloquea `disable`, `uninstall` y `clear data` del paquete que resuelve
  `android.intent.category.HOME`, con un aviso que explica el riesgo.
- AC5: la receta `wanboos-launcher` conserva su excepción explícita para cambiar el Home; el
  bloqueo aplica a acciones manuales, no al catálogo.
- AC6: sin regresiones en teléfono: mismas recetas, mismas acciones y mismos tests.

## Plan por fases

### Fase 1 — Reconocer la TV y no romperla (bajo)

| Tarea | Archivos | Coste |
|---|---|---|
| Detección por características **o** features | `core/adb/.../AdbParsers.kt` (`parseDeviceInfo`/`readInfo`) | 2 h |
| Tests de detección (TV por characteristics, TV por leanback, teléfono) | `core/adb/src/test/.../AdbParsersTest.kt` | 2 h |
| Proteger el Home actual en acciones manuales | `domain/model/ManagedApp.kt` (`AppSafety`), `feature/apps` | 3 h |
| Aviso en la UI al intentar deshabilitar el Home | `apps_strings.xml`, `AppsScreens.kt` | 1 h |
| Verificación en emulador ATV y en teléfono | manual | 1 h |

Criterio de cierre: `:core:adb:test`, `:app:testDebugUnitTest`, `ktlintCheck`, `detekt` y
`assembleDebug` verdes; captura del dashboard mostrando la TV como TV y el botón de launchers.

### Fase 2 — Control de TV útil (medio)

| Tarea | Detalle | Coste |
|---|---|---|
| Teclado remoto | `input text` con citado y verificación de foco | 1 día |
| Despertar pantalla | `input keyevent KEYCODE_WAKEUP` y lectura de estado | 4 h |
| Recetas de TV | escalado 1080p, límite de refresco, tienda sin auto-actualización | 1–2 días |
| Validación física | Sony / Philips / TCL / Chromecast con Google TV | depende del hardware |

### Fase 3 — La app dentro de la TV (alto, opcional)

El rediseño aprobado incorpora `LEANBACK_LAUNCHER`, banner y una presentación lateral
adaptada al mando. La cobertura de foco de todas las herramientas y la validación física
siguen siendo gates separados: esta presentación no implica completar las fases 1 y 2,
ni disponer de permisos para modificar el televisor.

## Riesgos

| Riesgo | Mitigación |
|---|---|
| Deshabilitar el Home deja la TV sin interfaz | AC4 + aviso + prueba en emulador antes de publicar |
| Fabricantes que apagan ADB tras reiniciar | documentar y ofrecer reconexión por conocido (`KnownDevice`) |
| Variantes de `dumpsys` por OEM | fixtures por fabricante en `core:adb` antes de ampliar parsers |
| mDNS ausente en algunas TVs | la conexión manual `ip:5555` ya cubre ese caso |
| `pm list features` lento en equipos antiguos | se ejecuta una sola vez en `readInfo`, sin coste por pantalla |

## Decisiones abiertas

1. ¿Bloquear el Home solo al deshabilitar, o también al desinstalar y limpiar datos?
2. ¿Fase 2 entra en el alcance del producto o se limita a Fase 1?
3. ¿Hay un televisor físico para validar antes de cerrar la fase?
