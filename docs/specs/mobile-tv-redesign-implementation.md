# Rediseño móvil y TV

Referencias: `design-proposals/redesign-tools/app-redesign.html`,
`design-proposals/redesign-tools/tv-redesign.html` (propuesta estática 16:9) y
`design-proposals/redesign-tools/tv-prototype.html` (prototipo navegable con D-pad).

El diseño completo de la app (`app-redesign-static.html`) incluye la sección
`#tv` con las seis pantallas de televisor, las reglas de 10 pies y enlaces a cada
pantalla del prototipo. Es la vista única para revisar móvil y TV juntos; las
maquetas no conceden permisos ni sustituyen la validación en equipo real.

## Alcance aprobado

- Un proyecto Android con presentación adaptada al teléfono o televisor.
- Móvil conserva Inicio, Apps, Destino, Optimizar y Ajustes.
- Herramientas agrupa utilidades existentes; Ajustes contiene preferencias.
- TV utiliza navegación lateral, mando D-pad y foco visible.
- No se añaden métricas, preferencias ni recetas ficticias de las maquetas.
- Se preservan las implementaciones existentes de consola, archivos, debloater y perfiles.

## Aceptación

1. Destino conserva selección y conexión de equipos en móvil.
2. Cada herramienta abre una función real, sin callbacks vacíos.
3. Sin conexión o permisos se conservan estados de error/vacío; la UI no concede privilegios.
4. TV se abre desde el launcher y no requiere pantalla táctil.
5. La navegación principal de TV funciona con D-pad y muestra el foco.
6. Atrás cierra primero pantallas secundarias.
7. Compilación y pruebas JVM pasan. La usabilidad con mando requiere validación
   adicional en televisor/emulador; compilar no acredita esa validación.

## Gates

`./gradlew :app:testDebugUnitTest :app:assembleDebug`

`./gradlew ktlintCheck detekt`

Consolidar presentación no implica fusionar repositorios de responsabilidades distintas.

## Alineación de diseño v3 (móvil)

Las maquetas se alinearon con la implementación real en tres puntos que no coincidían:

- **Inicio / Explora tu equipo**: la sección es una lista agrupada de ocho categorías
  (Dispositivo, Pantalla, Sensores, Red, Sistema, CPU, Batería y Memoria), cada una con
  icono tonal, título, subtítulo y detalle. La tarjeta de estado del dispositivo
  (“Buen estado · 76/100”) va después de las métricas. Accesos rápidos conserva
  Optimizar, Herramientas, Perfiles de apps y Opciones de energía; Shizuku es una fila
  más dentro de “Del dispositivo”.
- **Tipografía única**: 22 sp título, 14 sp fila, 12 sp subtítulo, 10 sp etiqueta y
  19 sp cifra. Apps y Optimizar dejan de usar escalas intermedias; los subtítulos admiten
  dos líneas, como `DesignRow`/`PlanRow`.
- **Optimizar**: se añadió la pantalla que faltaba, con encabezado “Optimizar” y subtítulo
  “Perfil: … · N recetas”, intro, chips de perfil, conteo, tarjetas con checkbox y riesgo,
  y FAB de aplicación. La fila reutiliza `bodySmall` (12 sp) para el resumen, igual que
  Apps y el resto de listas; los riesgos usan los mismos colores que `RiskBadge`.
- **Nuevo dispositivo**: se sustituye el formulario de una sola IP por el asistente real
  de dos pasos: TV con código (IP, puerto, código de 6 dígitos y puerto opcional) y
  teléfono con QR; se conservan búsqueda en red, IP manual y los textos de ayuda. La
  cabecera muestra el paso (“PASO 1 DE 2” / “PASO 2 DE 2”).

Referencias actualizadas: `app-redesign-static.html` (lámina completa, incluida la
sección TV) y `app-redesign.html` (prototipo navegable). Las capturas
`app-redesign-static.png` y `app-redesign.png` se regeneraron con estos cambios.

## Evidencia de esta iteración

- `ktlintCheck detekt :app:testDebugUnitTest :app:assembleDebug`: PASS.
- Verificación visual en emulador de teléfono (dark y light) y en Android TV API 36:
  - Inicio: cabecera con destino, métricas en tarjetas, accesos y filas agrupadas.
  - Herramientas: buscador real, secciones Diagnóstico/Sistema/Aplicaciones y filas con
    icono tonal, tal como la maqueta.
  - Ajustes: solo preferencias, con filas agrupadas, interruptores y desplegables.
  - Destino: chips local/remoto, tarjeta del equipo activo y dispositivos conocidos.
  - Registro: cabecera, búsqueda, chips de nivel y estado vacío con el lenguaje nuevo.
  - Apps: cabecera, búsqueda, chips Todas/Usuario/Sistema y lista agrupada.
  - Debloater: contador en cabecera, buscador, chips de riesgo, lista agrupada con
    selección cuadrada, aviso ámbar y botón "Revisar N apps".
  - Archivos: ruta en cabecera, acciones de carpeta, buscador, lista agrupada con
    iconos tonales y nota de rutas protegidas.
  - Explorar tu equipo: iconos con color por categoría (verde/cian/ámbar/rojo).
  - Opciones de energía: pantalla propia con atrás, no una hoja; filas agrupadas y
    confirmación antes de reiniciar o apagar.
- Barra inferior con cinco destinos estables (Optimizar siempre visible); sin permiso,
  la pantalla de Optimizar explica que requiere Shizuku.
- Navegación inferior con píldora verde y respeto del área del sistema.
- TV conserva menú lateral, foco visible y reutiliza contenido real.

### Debloater en TV

El Debloater reutilizaba el layout móvil completo dentro del scaffold de TV: la cabecera,
la búsqueda, los chips, el aviso y la fila de acciones consumían el alto y dejaban la
`LazyColumn` sin espacio visible. Se añadió `TvDebloatContent` con:

- cabecera `TvScreenHeader`, aviso de launcher y conteo; sin flecha ni acciones
  “Volver”/“Volver a leer”: en TV se vuelve con el mando (Atrás) o cambiando de sección
  en la barra lateral;
- lista con todo el alto disponible, filas enfocables con anillo de 2 dp y foco inicial en
  la primera app;
- acciones inferiores de congelar, descongelar y desinstalar, más refrescar/deshacer en la
  cabecera;
- el mismo diálogo de confirmación, catálogo y estados vacío/error del móvil.

Validado en el emulador `sdk_google_atv64_arm64`: 28 apps visibles, selección con OK y
desplazamiento con D-pad. `ktlintCheck detekt :app:testDebugUnitTest :app:assembleDebug`
en verde.

### Navegación en TV

Las pantallas de TV no muestran flechas de volver ni botones de texto “Volver”: la barra
lateral y el botón Atrás del mando son la navegación. `ToolTopBar` usa `TvScreenHeader`
cuando `LocalTelevisionUi` está activo, así que Registro, Consola, Archivos, Ajustes del
sistema, Perfiles, Doze y Debloater dejan de heredar la cabecera móvil. También se
ocultaron las flechas en ADB local, Launcher de TV, Opciones de energía y el detalle del
dashboard. El diseño TV (`tv-prototype.html`) ya no dibuja botones de volver en las
pantallas secundarias.

## Cobertura pendiente

- Consola, Ajustes del sistema y Perfiles conservan su contenido interno; heredan la
  cabecera y la búsqueda compartidas, pero no se han rediseñado fila por fila.
- Falta validar en un teléfono físico y con un dispositivo remoto conectado.
- La reproducción no es píxel a píxel: el contenido depende del estado real del equipo.
