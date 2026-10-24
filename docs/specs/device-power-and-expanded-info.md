# Energía e información ampliada del dispositivo

Estado: READY

## Alcance acordado
Agregar reiniciar, apagar y reiniciar en recovery; completar datos visibles del destino
ADB además de los datos locales integrados anteriormente desde las capacidades de SysInfo.
Conservar Compose, DI manual, DashboardEnvironment y modelos compartidos actuales.

## Criterios
- P1: acciones fijas (`svc power reboot`, `svc power shutdown`, `svc power reboot recovery`),
  dirigidas al dispositivo seleccionado. Local requiere Shizuku; remoto una sesión ADB.
- P2: el selector muestra acción y dispositivo antes de ejecutarla; se captura su ID.
  Cambiar destino invalida la selección pendiente. Doble toque no duplica comandos.
- P3: salida no cero o error de transporte no se declara éxito ni se reintenta. Mostrar
  resultado de solicitud, no afirmar que el reinicio físico se completó. Una acción
  reconocida elimina únicamente la sesión remota afectada.
- D1: ADB alimenta hardware, sistema, capacidades GLES/Vulkan/USB, batería (nivel, salud,
  estado, voltaje, tecnología, carga restante y temperatura propia) y CPU por núcleo.
- D2: agregar pantalla (resolución/densidad y overrides ADB; modos/Hz locales), inventario
  de sensores (nombre/fabricante/tipo) e interfaces/IP de red para ambos destinos.
- D3: fallos de lecturas opcionales no borran las métricas disponibles. Propagar cancelación;
  ausente != cero. No mezclar temperatura de CPU con temperatura de batería.
- D4: todas las filas nuevas tienen recursos y previews; ningún Composable ejecuta shell.
  Al cambiar de dispositivo se descartan las métricas viejas.

## Validación
Tests JVM para comandos/error/sin reintento, estado de confirmación/doble envío/destino,
parsers con fixtures sintéticos y repositorio ADB con lecturas opcionales fallidas.
Compilar, tests app/core:adb, APK y comprobación visual si hay dispositivo disponible.
Las pruebas automatizadas de energía usan fakes, no reinicios/apagados físicos.

## Fuentes
`getprop`, `pm list features`, `uname -r`, `wm size`, `wm density`, inventario de
`dumpsys sensorservice`, `dumpsys battery`, sysfs CPU y `ip -o addr show up`.
Local: APIs Android públicas y NetworkInterface, sin permisos nuevos ni muestreo de sensores.
