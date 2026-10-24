# Integración de diagnóstico de SysInfo

Estado: IMPLEMENTED (compilación, APK y pruebas JVM verificados; lint global y validación visual pendientes)

## Evidencia y decisiones

Referencia: https://github.com/kl3jvi/sysinfo_app, rama `kotlin_app`, commit
`147d031850ad0fa582faec72354887776b9d5232`.
Se revisaron los nueve providers de CPU, batería, RAM, dispositivo, sistema,
GPU, almacenamiento, aplicaciones y red, y el fragmento de pantalla.
El proyecto destino usa Compose, DI manual, DashboardEnvironment y modelos compartidos
entre local y ADB. Baseline: compilación y tests de app correctos.

| Fuente | Aprovechamiento |
|---|---|
| DeviceDataProvider | Marca, producto, placa, hardware, ABIs, USB host |
| SystemInfoProvider | Parche, compilación, kernel, VM, banda base |
| GpuDataProvider | GLES y versión Vulkan con máscara correcta de 12 bits para patch |
| CpuDataProvider | Frecuencias actual/mínima/máxima por núcleo; SoC público desde API 31 |
| BatteryDataProvider | Salud, estado, alimentación, tecnología, voltaje y temperatura |
| RamDataProvider | Umbral de memoria baja y estado reportado por Android |
| StorageProvider | StatFs ya existe; conservar volumen de datos sin duplicar almacenamiento emulado |
| ApplicationDataProvider | Ya superado por LocalAppRepository y feature/apps |
| NetworkInfoProvider | No portar: endian IPv4 incorrecto, no calcula dirección de subred y escaneo masivo; ya hay descubrimiento ADB |
| ScreenFragment | Esqueleto sin implementación aprovechable |

El README afirma GPLv3 y MIT simultáneamente y no incluye LICENSE raíz.
Implementación original basada en capacidades públicas, sin copiar fuentes ni assets.
No incorporar Koin/Epoxy/XML, billing, workers de sondeo ni JNI/cpuinfo para estos datos.
Sensores, cámaras, SIM y Bluetooth anunciados en README no tienen providers equivalentes
en esta rama; no se presentan como funcionalidades extraídas.

## Contrato

AC1. Cada frecuencia es opcional: errores de acceso, texto inválido, cero y valores
negativos producen desconocido, sin perder los otros núcleos ni otras métricas.
AC2. Vulkan se decodifica según major/minor/patch; ausencia no equivale a versión cero.
AC3. Batería conserva decimales y signo de corriente, rechaza centinelas y porcentajes
inválidos; carga restante no se etiqueta como capacidad nominal.
AC4. Las cinco categorías actuales muestran los detalles locales con recursos traducibles,
sin permisos adicionales ni operaciones de I/O en Composables. Campos remotos ausentes
no se rellenan con datos del teléfono anfitrión.
AC5. Se conserva el refresco y navegación actuales; frecuencias no se muestran como uso
real de CPU. La cifra derivada de loadavg se identifica como estimación local.

## Implementación y validación

Modelos opcionales en core/model; lectores en data; presentación en dashboard.
Sin persistencia ni nuevas dependencias. Lecturas opcionales fallan individualmente.
Tests JVM para archivos inaccesibles y conversiones, suite dashboard y suite app.
Compilar app y APK; previews para batería disponible/ausente y CPU.
Validación física depende de dispositivo disponible: las restricciones sysfs son del OEM.

## Resultado de implementación

- `core/model/.../DeviceDiagnostics.kt`: hardware, frecuencias y batería con datos opcionales.
- `data/LocalHardwareReader.kt`: APIs públicas de Build, PackageManager y ActivityManager.
- `data/LocalBatteryReader.kt`: broadcast de batería y propiedades públicas del fuel gauge;
  no estima capacidad nominal mediante reflexión.
- `data/DeviceDiagnosticsReader.kt`: lectura independiente por archivo y núcleo en el
  dispatcher IO que ya usa LocalDeviceRepository.
- `data/DeviceDiagnosticsParser.kt`: conversiones numéricas sin Android, comprobables en JVM.
- `feature/dashboard/DeviceDiagnosticsContent.kt`: detalles en las cinco categorías actuales.
- `feature/dashboard/DeviceDiagnosticsPreviews.kt`: las cinco categorías en tema oscuro con
  datos y tema claro sin datos, ancho 360dp y fontScale 1.5.
- `res/values/diagnostics.xml`: textos y formatos localizables.

Los detalles nuevos se recopilan en el teléfono local. ADB conserva sus métricas actuales;
no se atribuyen datos del anfitrión al dispositivo remoto. Ampliar los collectors ADB
requiere contratos y fixtures específicos de getprop/dumpsys/sysfs del destino.

## Evidencia de validación (2026-10-22)

| Criterio | Evidencia |
|---|---|
| AC1 | DeviceDiagnosticsReaderTest: fallo de un núcleo aislado, IOException, SecurityException, texto inválido, overflow, cero y negativos |
| AC2 | DeviceDiagnosticsParserTest: Vulkan 1.3.274 y versión ausente |
| AC3 | DeviceDiagnosticsParserTest: corriente negativa, temperatura decimal, voltaje, escala inválida, nivel fuera de rango y overflow de porcentaje |
| AC4 | DashboardViewModelTest: cambio local→remoto elimina diagnósticos locales; compilación de recursos y previews |
| AC5 | Tests existentes de refresco y ajustes; etiqueta local «Carga estimada de CPU» |

- `:app:compileDebugKotlin`: correcto.
- `:app:testDebugUnitTest`: 140 tests, 0 fallos, 0 errores, 0 omitidos en el último reporte.
  El total incluye cambios concurrentes ajenos a esta integración. Se añadieron cinco tests aquí.
- `:core:adb:testDebugUnitTest`: correcto. `:core:model:testDebugUnitTest` y
  `:core:optimization:testDebugUnitTest` no tienen fuentes de pruebas (`NO-SOURCE`).
- `:app:assembleDebug`: correcto; APK en `app/build/outputs/apk/debug/app-debug.apk`.
- `:app:lintDebug`: bloqueado por dos errores fuera del cambio: indentación en
  `feature/mirror/MirrorViewModel.kt:116` y `QUERY_ALL_PACKAGES` en `AndroidManifest.xml:11`.
  Reporte global: 2 errores y 58 warnings. Sin hallazgos en los nuevos lectores/diagnóstico.
- APK instalado y comando de apertura ejecutado en Galaxy A35, Android 16. Pantalla
  bloqueada: no se confirmó visualmente la UI ni la disponibilidad real de sysfs.

No se atribuyen los fallos temporales de compilación en connection/mirror a estos cambios;
se observaron ediciones concurrentes y se conservaron sus archivos.
