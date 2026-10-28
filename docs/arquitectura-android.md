# Arquitectura Android — AdbOptimizer

Este documento es la referencia para trabajar en la app Android. Describe la estructura
actual, las reglas de dependencia y el contrato de presentación que deben seguir los
cambios nuevos.

## 1. Mapa de módulos

| Módulo | Responsabilidad |
|---|---|
| `:app` | Aplicación Android, composición raíz, features, navegación y DI manual. |
| `:core:model` | Contrato de dominio serializable compartido con el desktop: catálogo, apps, dispositivos, métricas y resultados. |
| `:core:shell` | Contrato `AdbShell`, errores y tipos base de ejecución. |
| `:core:adb` | Transporte ADB, descubrimiento, parsers, protocolo scrcpy y conexión. |
| `:core:optimization` | Motor de recetas, validación de catálogo y bitácora de cambios. |

Dirección de dependencias entre módulos:

```text
:app
 ├── :core:adb
 ├── :core:optimization
 └── :core:model

:core:adb
 ├── :core:shell
 └── :core:model

:core:optimization
 ├── :core:shell
 └── :core:model
```

Ningún módulo `:core:*` depende de `:app`.

## 2. Estructura de paquetes en `:app`

```text
com.optimizer.tools
├── MainActivity.kt              # Punto de entrada Android
├── AdbOptimizerApplication.kt   # Application y creación de AppContainer
├── app/                         # Composición y estado global de la aplicación
│   ├── MainViewModel.kt
│   ├── UiState.kt
│   ├── Navigation.kt            # Screen + módulos visibles por rol
│   ├── OptimizerApp.kt          # Scaffold, top bar, bottom bar y NavHost
│   ├── ActiveDestinationName.kt
│   └── navigation/
│       ├── AppNavGraph.kt
│       └── AppRoutes.kt
├── core/
│   └── ui/                      # UI realmente compartida
│       ├── Theme.kt
│       ├── Formatters.kt
│       ├── AppIcon.kt
│       ├── QrImage.kt
│       ├── UiText.kt            # Texto de UI resuelto en composición
│       └── video/               # Video compartido por mirror y remote
├── data/                        # Infraestructura compartida y AppContainer
├── domain/                      # Modelos, puertos y use cases compartidos
├── feature/
│   ├── apps/
│   ├── apps/
│   ├── connection/
│   ├── console/
│   ├── dashboard/
│   ├── debloat/
│   ├── doze/
│   ├── files/
│   ├── inspector/
│   ├── install/
│   ├── logcat/
│   ├── mirror/
│   ├── optimize/
│   ├── profiles/
│   ├── remote/
│   ├── settings/
│   ├── settingsdb/
│   ├── shizuku/
│   └── tvlauncher/
└── shizuku/                     # Servicio AIDL local y acceso a Shizuku
```

### Ownership

- `feature/<name>/` es dueño de la pantalla, el contenido visual, el ViewModel, su estado,
  sus effects y sus modelos auxiliares.
- `core/ui` contiene únicamente componentes reutilizados por dos o más features, o
  infraestructura visual sin semántica de negocio.
- `data` y `domain` son compartidos. Un repositorio específico de una feature puede
  moverse a `feature/<name>/data` cuando deje de ser compartido.
- `app` compone la aplicación. No debe contener reglas de negocio ni pantallas completas.
- Las funciones que solo cambian el estado de paquetes comparten `data/packages/PackageStateReader`
  en lugar de construir comandos `pm` propios; hoy lo usan `profiles` y `debloat`.
- La paridad con la app de referencia (qué se portó y qué falta) vive en
  [paridad con ADB Application Manager Pro](specs/parity-adb-app-manager.md).

## 3. Reglas de dependencia

Permitido:

```text
feature -> core.ui, domain, data, core.*
app     -> feature, core.ui, domain, data
data    -> domain, core.*
domain  -> core.*
```

Prohibido:

```text
feature -> app
feature -> otra feature
core.ui -> feature
data/domain -> feature, app o ui
```

Los componentes visuales de video compartidos por `feature/mirror` y `feature/remote`
viven en `core/ui/video`; ninguna feature importa código de otra. No agregar nuevas
dependencias entre features sin documentarlas aquí.

### Cuándo crear un módulo Gradle

No crear un módulo `:feature:*` solo porque existe una pantalla nueva. Un módulo se
justifica cuando la capacidad tiene ciclo de vida, release, ownership o dominio
independiente. Mientras eso no ocurra, el paquete dentro de `:app` es suficiente.

## 4. Anatomía de una feature

```text
feature/<name>/
├── <Name>Screen.kt      # Composable conectado: recolecta estado y conecta callbacks
├── <Name>Content.kt     # UI pura, sin ViewModel ni navegación, apta para Preview
├── <Name>ViewModel.kt   # Orquesta estado y efectos
├── <Name>State.kt       # Estado inmutable (si conviene separarlo)
├── <Name>Effect.kt      # Eventos de una sola ejecución
├── <Name>Environment.kt # Puertos/dependencias estrechas (si el ViewModel las necesita)
├── <Name>Engine.kt      # Frontera con un motor externo (si aplica)
├── <Name>Models.kt      # Modelos propios de la feature
└── <Name>Labels.kt      # Textos o formatos propios (si aplica)
```

Reglas:

- `Screen` recibe estado y lambdas; no ejecuta reglas de negocio.
- `Content` no debe depender de `NavController`, `Context`, Hilt ni ViewModel.
- El ViewModel no conoce `Context`, `Activity`, `NavController` ni APIs de red.
- Si el ViewModel necesita infraestructura, definir un puerto estrecho en la feature y
  implementarlo con un adaptador en `app/` (composición) o en `data/`. No pasarle el
  `AppContainer` completo.
- Cada feature conserva sus textos y modelos visuales; solo lo transversal va a `core/ui`.
- Los strings estáticos de UI van a `res/values/*_strings.xml`. No agregar literales
  visibles nuevos en Composables.
- Un ViewModel no recibe `Context` ni `Resources`. Cuando necesita producir texto visible
  usa `UiText.Resource(R.string...)` (estático) o `UiText.Plain(...)` (detalle dinámico);
  el Composable lo resuelve con `UiText.resolve()` o `resolve(context)` fuera de
  composición. Los mensajes operativos que provienen de `data/` se conservan como texto
  dinámico y se envuelven en `UiText.Plain` en la frontera del ViewModel.

## 5. Contrato MVI ligero

No se usa una clase base ni un reducer obligatorio. El contrato es:

- **State**: `data class` inmutable con todo lo necesario para renderizar.
- **Effect**: `sealed interface` para snackbar, navegación, permisos o acciones externas.
  En el código actual se implementa con el sufijo `Event` (por ejemplo `OptimizeEvent`).
- **Intent**: opcional. Se usa cuando la pantalla tiene muchas acciones y conviene un
  único `onIntent(...)`. Para una o dos acciones, los métodos públicos del ViewModel son
  válidos y más simples.

Ejemplo de referencia, `feature/optimize`:

```kotlin
data class OptimizeState(
    val running: Boolean = false,
    val plan: List<PlanItem> = emptyList(),
)

sealed interface OptimizeEvent {
    data class Fail(val key: String, val detail: UiText?) : OptimizeEvent
    data class Done(val key: String) : OptimizeEvent
}

class OptimizeViewModel(private val environment: OptimizeEnvironment) : ViewModel() {
    private val _state = MutableStateFlow(OptimizeState())
    val state: StateFlow<OptimizeState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<OptimizeEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<OptimizeEvent> = _events.asSharedFlow()
}
```

En Compose:

```kotlin
val state by viewModel.state.collectAsStateWithLifecycle()

LaunchedEffect(Unit) {
    viewModel.effects.collect { effect ->
        // snackbar, navegación o permiso
    }
}
```

No guardar callbacks, `Context`, `NavController` ni eventos de navegación pendientes en el
State. Para navegación de una sola vez, usar callbacks del host o un `Effect`.

## 6. Navegación

- `AppRoutes.kt` declara `AppDestination` como `sealed interface @Serializable`; cada
  destino es un `data object` o `data class` tipado (`DashboardDetailRoute(category)`).
- `AppNavGraph` usa `composable<Destino>` y `entry.toRoute<Destino>()`; no hay rutas por
  string ni `navArgument`.
- Una pantalla de detalle usa `popBackStack()` para volver; si el back stack está vacío,
  cae a `HOME`.
- `mainRoutes` define qué destinos muestran la barra inferior;
  `NavDestination.isPushDestination()` define las animaciones de empuje.
- `NavDestination.toScreen()` traduce el back stack al `Screen` que consume el host.
- `MainViewModel` expone `navigation: Flow<Screen>` respaldado por un `Channel`. El host
  recolecta esa intención y navega; el estado de UI no contiene la pantalla actual.
- El back stack de Navigation Compose es la única fuente de verdad de navegación. No
  agregar banderas de navegación a estados de feature ni sincronizar `UiState.screen`.

## 7. Inyección de dependencias

La decisión vigente es DI manual, documentada en `adr/ADR-008-di-manual.md`:

- `AppContainer` construye dependencias y vive en `AdbOptimizerApplication`.
- `MainActivity` compone adaptadores y crea los ViewModels con sus factories.
- No hay Hilt ni KSP en la app.

Regla para código nuevo: un ViewModel debe recibir dependencias estrechas (interfaces,
repositorios o use cases), no `AppContainer` completo. No agregar nuevas dependencias
globales mutables fuera de `AppContainer`.

Todos los ViewModels usan puertos estrechos; ninguno recibe `AppContainer`:

- Cada feature declara su contrato en `<Name>Environment.kt` (y puertos adicionales
  cuando aportan claridad, como `OptimizeEngine`).
- `app/AppContainer<Name>Environment.kt` traduce `AppContainer` a ese contrato. Es el
  único lugar que conoce ambos lados; `MainActivity` compone el adaptador y la factory.
- `MainViewModel` usa `MainEnvironment`; su adaptador vive en `app/`.
- Las features no importan `com.optimizer.tools.data`: los tipos compartidos viven en
  `core:model` o en `domain/model` (por ejemplo, `RemoteKey`).
- `DashboardEnvironment` expone un `Flow<Unit>` de ticks para que el refresco automático
  sea sustituible en pruebas sin bucles infinitos.
- `ConnectionEnvironment` abstrae reloj, logging, descubrimiento, pairing y sesiones;
  las pruebas no necesitan Android, ADB ni `SystemClock`.

Patrón de prueba: fakes propios, `runViewModelTest` de
`app/src/test/kotlin/com/optimizer/tools/testutil/ViewModelTest.kt` y verificación de
estado/efectos observables. `SettingsViewModelTest` es el ejemplo mínimo y
`ConnectionViewModelTest` el ejemplo de un puerto con varios colaboradores.

### Logging

- El contrato es `domain/logging/AppLogger`; no usar `android.util.Log` directamente en
  features ni en ViewModels.
- `AppContainer.logger` elige `LogcatAppLogger` solo si el proceso es depurable y
  `NoOpAppLogger` en el resto. La decisión vive en la composición, no en cada llamada.
- Los adaptadores de `app/` traducen eventos de infraestructura al logger; el logger nunca
  debe lanzar ni alterar el resultado funcional.

## 8. Datos y dominio

- `core:model` es el contrato de dominio compartido con el desktop, no un DTO de
  transporte.
- `domain/port` contiene interfaces cuando existe una frontera real (por ejemplo,
  `ApkInstaller` y `HostAppsSource`).
- `domain/usecase` contiene coordinación con valor de negocio; no crear un UseCase para
  una delegación trivial.
- `data` implementa el acceso a ADB, preferencias, archivos y repositorios.
- Los modelos de SDK, transporte o persistencia no deben llegar a Compose. Hoy no hay
  Room ni Retrofit; si se agregan, mapear en la frontera.

## 9. UI y sistema de diseño

- Tema único: `AdbOptimizerTheme` y `AppColors` en `core/ui/Theme.kt`.
- Componentes compartidos: `AppIcon`, `QrImage`, `Translate`, formatters.
- No crear un segundo sistema visual dentro de una feature.
- Reutilizar Material 3, tipografía y colores del tema.
- Mantener previews de `Content` con estados representativos.
- Evaluar `Loading`, `Error`, `Empty` y `Disabled` cuando la pantalla lo requiera.

## 10. Pruebas

- La ruta del test refleja la ruta del código de producción.
- Stack actual: JUnit 4 y `kotlinx-coroutines-test`. No se requiere MockK; usar fakes.
- Probar lógica pura: parsers, validadores, filtros, mappers y navegación.
- Para ViewModel: usar `runViewModelTest` (configura `Dispatchers.setMain` y restaura
  después de la limpieza de `runTest`) e inyectar fakes. Probar `estado/efecto`
  observable, no detalles privados. Referencias:
  `app/src/test/kotlin/.../testutil/ViewModelTest.kt`,
  `.../feature/settings/SettingsViewModelTest.kt` y
  `.../feature/connection/ConnectionViewModelTest.kt`.
- Nombres de test orientados a comportamiento:

```kotlin
@Test
fun `when the profile changes then the plan is rebuilt`() { ... }
```

## 11. Cómo agregar una feature

1. Verificar si la capacidad pertenece a una feature existente. Preferir extenderla.
2. Si es independiente, crear `feature/<name>/` con Screen, Content, ViewModel y modelos.
3. Definir State y, si hay eventos únicos, Effect.
4. Agregar la ruta en `AppRoutes` y el destino en `AppNavGraph`.
5. Registrar el ViewModel en `MainActivity` y su factory.
6. Reutilizar `core/ui` y el tema; agregar strings a recursos.
7. Agregar previews de los estados relevantes.
8. Agregar pruebas de lógica y de ViewModel cuando aplique.
9. Ejecutar compilación y tests.

## 12. Cómo eliminar una feature

1. Borrar `feature/<name>/`.
2. Quitar su ruta y destino de navegación.
3. Quitar el ViewModel y la factory de `MainActivity`.
4. Quitar dependencias exclusivas de `AppContainer`.
5. Quitar strings, drawables y tests exclusivos.
6. Compilar y ejecutar tests.

Si al eliminarla hay que tocar varias features o `core`, la frontera está mal y debe
revisarse antes de continuar.

## 13. Deuda técnica conocida

- `AppContainer` sigue siendo el grafo manual de infraestructura; los ViewModels ya no lo
  reciben, pero los adaptadores de `app/` deben mantenerse delgados.
- `data/` conserva mensajes operativos dinámicos (progreso de instalación, salida de ADB).
  No son etiquetas de UI y se presentan como `UiText.Plain`; si se convierten en textos
  estáticos, deben moverse a recursos.
- Los tests de ViewModel verifican recursos mediante `UiText.Resource`, sin depender de
  Android ni de `Resources`.

## 14. Verificación

```bash
./gradlew ktlintCheck
./gradlew detekt
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

La configuración vive en `config/detekt/detekt.yml` y `.editorconfig`. El CI ejecuta
`ktlintCheck`, `detekt`, los tests JVM y `assembleDebug` antes de publicar el APK de
depuración.

## 15. Referencias

- `adr/ADR-008-di-manual.md` — inyección de dependencias manual.
- `adr/ADR-014-estructura-por-feature.md` — decisión de esta estructura.
- `adr/ADR-016-launcher-wanboos.md` — launchers de sistema en Android TV.
- `../../AdbOptimizer-Desktop/docs/adr/ADR-012-sistema-visual.md` — sistema visual del
  escritorio (la app Android usa su propio tema Material; ver `design-proposals/`).
