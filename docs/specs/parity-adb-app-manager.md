# Paridad con ADB Application Manager Pro 7.18.0 y port a AdbOptimizer

Estado: IMPLEMENTED (perfiles, debloater, archivos y consola; compilación, ktlint, detekt y
tests JVM verificados). Sin ejecución en un teléfono real: los comandos están cubiertos por
tests con un shell falso, no por observación de runtime.

## 1. Identidad del artefacto

| Dato | Valor |
|---|---|
| Archivo | `~/Downloads/ADB_Application_Manager_Pro-v7.18.0-universal.apk` |
| SHA-256 | `c528aae78d6b7229b574c634a6649b78e443761c6eb3181fe035fa0ac213e659` |
| Paquete | `com.bloatware.bingblop` |
| Etiqueta | ADB/Root Full System Toolbox |
| Versión | name `7.18.0-Pro`, code `866` (`assets/changelog.md`) |
| Compilación | SDK 34, `minSdk` declarado en el manifiesto decodificado |
| Firma/parcheo | `assets/app/morphe/patcher/version.properties` presente (APK parcheado con Morphe) |

## 2. Cómo se obtuvo la evidencia

- El servidor MCP de REA no estaba registrado en esta sesión, así que no se usaron sus
  herramientas. Se intentó el equivalente CLI (`npx -y rea-agents@6.7.0`) y quedó bloqueado
  por permisos del caché de npm (`EPERM` en `~/.npm/_cacache`), un problema del entorno, no
  del objetivo.
- Se usó el análisis estático local equivalente, conservando identidad y límites:
  `unzip` para el inventario del archivo, `apktool d` para manifiesto, recursos y smali, y
  lectura directa de los assets (la interfaz de la app es una WebView).
- Observación: la app es una WebView (`assets/index.html`, 3,5 MB) con un puente nativo
  (`MainActivity$AndroidBridge`, 322 métodos anotados `@JavascriptInterface`) y un binario
  `libadb.so` más `rish` para Shizuku. No hay Compose ni pantallas nativas: el producto vive
  en HTML/JS y el puente es la superficie real de funcionalidad.
- Inferencia: al no ejecutar la app, el comportamiento descrito en `guide.js` y en el
  changelog es la fuente de verdad de producto; los comandos concretos se deducen de los
  nombres del puente y de los textos (`pm`, `am`, `settings`, `cmd overlay`, etc.).
- Desconocido: diferencias de comportamiento por versión de Android/OEM, y cualquier
  decisión interna del puente que no esté reflejada en los textos de la interfaz.

Inventario de referencia: `assets/guide.js` declara 30 grupos de pestañas y 150 secciones
(«Application Manager», «Debloater», «File Manager», «Console», «Terminal», «Profiles and
drift watch», etc.). El catálogo completo se puede regenerar con:

```bash
python3 - <<'PY'
import json
s = open('assets/guide.js', encoding='utf-8').read()
data = json.loads(s[s.index('{"version"'):s.rindex('}') + 1])
for section in data['sections']:
    print(section['group'], '|', section['title'])
PY
```

## 3. Matriz de paridad

Leyenda: ✅ ya existía en AdbOptimizer, 🆕 portado en este cambio, 🗺️ pendiente (fuera de
este alcance), ❌ fuera de dominio (la app Android no es escritorio ni toolchain).

| Capacidad de la app de referencia | Estado en AdbOptimizer |
|---|---|
| Apps: congelar, habilitar, desinstalar para el usuario, limpiar datos/caché, forzar parada, extraer APK | ✅ |
| Inspector por app (permisos, AppOps, componentes, firma) | ✅ |
| Compilar AOT, bucket de standby, restringir segundo plano | ✅ |
| Registro del sistema (logcat) | ✅ |
| Ajustes global/secure/system con historial y deshacer | ✅ |
| Doze y energía | ✅ |
| Conexión inalámbrica/QR, emparejamiento, descubrimiento | ✅ |
| Espejo y control remoto (scrcpy) | ✅ |
| Perfiles de estado de apps + vigilancia de drift | 🆕 |
| Debloater con recomendaciones curadas y desinstalación reversible | 🆕 (catálogo UAD-NG descargado en runtime) |
| Explorador de archivos: navegar, crear, renombrar, borrar, ver texto, instalar APK del dispositivo | 🆕 |
| Consola de comandos con historial | 🆕 |
| Terminal interactiva (PTY con xterm.js) | 🗺️ |
| Transferencia de archivos (pull/push con protocolo sync) | 🗺️ |
| Backups de apps (APK + datos) y restauración | 🗺️ |
| Listas guardadas de apps y exportación CSV | 🗺️ |
| Actualizador de apps / tiendas (Aurora, Obtainium) | 🗺️ |
| Trackers (Exodus) por app y apagado de sus componentes | 🗺️ |
| Overlays/Monet con root y temas del sistema | 🗺️ (requiere root; fuera del modelo actual) |
| Fuentes personalizadas y emoji | ❌ fuera de dominio |
| Morphe Patcher, SD Maid, Termux, Quick Settings/widgets | ❌ fuera de dominio del cliente ADB |
| Dispositivos múltiples (Wear, TV, Bluetooth) | ✅ parcial (TV/launcher), 🗺️ Wear |

## 4. Lo portado en este cambio

### 4.1 Perfiles de estado + drift watch

Semántica tomada de `index.html` (`profilePlan`, `profileTarget`, `checkProfileDrift`,
`PLAN_ORDER = reinstall, unfreeze, unsuspend, freeze, suspend, uninstall`), adaptada al
modelo de estados del proyecto:

- Un perfil guarda, por app, `ENABLED`, `DISABLED` o `UNINSTALLED` (`profileTarget` de la
  referencia colapsa frozen/suspended; aquí no se portó «suspender»).
- `ProfilePlanner` es puro: orden `REINSTALL → ENABLE → DISABLE → UNINSTALL`, apps
  desaparecidas se omiten y los paquetes críticos (`AppSafety`) nunca se tocan.
- El plan se calcula al abrir y al aplicar; cada paso se confirma leyendo `pm list packages`
  de vuelta (`PackageReadBack`), igual que el motor de recetas del proyecto.
- Solo un perfil queda «en vigilancia»; si al abrir el plan no está vacío, la pantalla
  muestra el aviso de drift con el número de apps afectadas.
- Cada ejecución guarda los cambios con su estado anterior; «Deshacer» los revierte.

Archivos: `domain/profile/*`, `data/profiles/*`, `data/packages/PackageStateReader.kt`,
`feature/profiles/*`, `app/AppContainerProfilesEnvironment.kt`.

### 4.2 Debloater (estilo UAD-NG)

- El catálogo **no se empaqueta**: se descarga a pedido desde el repositorio de UAD-NG y se
  guarda en `filesDir/uad-lists.json` (la lista es GPL-3.0; descargarla evita redistribuirla
  con un proyecto MIT). Las entradas `Unsafe` y sin datos se descartan al leer.
- La pantalla cruza el catálogo con el estado real del destino y ofrece congelar,
  descongelar y desinstalar para el usuario, con confirmación, progreso, resultados y
  «Deshacer la última tanda».
- El mismo `PackageStateReader` de los perfiles ejecuta y confirma cada cambio.

Archivos: `data/debloat/*`, `feature/debloat/*`, `app/AppContainerDebloatEnvironment.kt`.

### 4.3 Explorador de archivos

- Lista con `ls -la` parseado (fechas ISO y estilo antiguo, enlaces simbólicos), navegación
  por migas, búsqueda en la carpeta, crear carpeta, renombrar, borrar con confirmación,
  vista de texto limitada a 128 KB y «instalar APK» con el `pm install` del propio teléfono.
- Las rutas se normalizan y se citan; nunca se borran `/`, `/sdcard`, `/system`, `/data`,
  `/vendor`, `/product`, `/apex`, `/proc` ni `/sys`.
- Limitación conocida: no hay subida/descarga de archivos; usa comandos POSIX y no el
  protocolo sync (pendiente en la matriz).

Archivos: `data/files/*`, `feature/files/*`, `app/AppContainerFilesEnvironment.kt`.

### 4.4 Consola

- Ejecuta comandos en el destino activo con tope de 60 s, muestra salida, código y duración,
  mantiene historial persistente (40 entradas) y ofrece comandos rápidos.
- Sustituye a la terminal interactiva de la referencia, que necesita PTY; la consola cubre
  el caso «ejecutar un comando y ver qué responde».

Archivos: `data/console/ConsoleRepository.kt`, `feature/console/*`,
`app/AppContainerConsoleEnvironment.kt`.

## 5. Verificación

- `./gradlew :app:ktlintCheck` ✅
- `./gradlew :app:detekt` ✅
- `./gradlew :app:testDebugUnitTest` ✅ (incluye `ProfilePlannerTest`,
  `ProfilesRepositoryTest`, `ProfilesViewModelTest`, `DebloatCatalogTest`,
  `DebloatViewModelTest`, `RemoteFileRepositoryTest`, `FilesViewModelTest`,
  `ConsoleViewModelTest`).
- `./gradlew :app:assembleDebug` ✅
- Sin prueba física: los comandos `pm disable-user`, `pm install-existing`,
  `pm uninstall --user 0`, `ls -la` y `pm install -r` siguen el formato que usa el resto del
  proyecto y están cubiertos por un shell falso; falta confirmarlos en un dispositivo real.

## 6. Riesgos y límites

- `pm list packages -u` incluye paquetes desinstalados con datos; algunos OEM lo filtran. Un
  perfil podría marcar como «missing» una app que el teléfono sí conoce.
- El estado «suspendido» de la referencia no se porta: el proyecto solo distingue
  habilitada/deshabilitada/desinstalada.
- El debloater depende de una descarga de 1,6 MB y de que el repositorio de UAD-NG mantenga
  la ruta y el formato; si cambian, la pantalla lo reporta como error de catálogo.
- La app de referencia usa `libadb.so` y Shizuku para hablar con el propio teléfono; este
  proyecto ya tiene ambos caminos (sesión ADB y Shizuku), por eso las funciones nuevas se
  apoyan en `PackageStateReader` en lugar de duplicar transporte.
