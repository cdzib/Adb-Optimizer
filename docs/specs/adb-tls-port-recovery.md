# Recuperación de puerto ADB TLS

Estado: VERIFIED (compilación y regresiones JVM; reconexión física pendiente)

## Evidencia
ECONNREFUSED se produce en LibAdbShell.ensureConnected antes del primer comando.
AppContainer crea un shell de conexión diferida, pero su catch dice incorrectamente
«ADB connected». ConnectionViewModel conecta al puerto elegido/guardado sin recuperación.

## Contrato
- AC1: ante OFFLINE en TLS, redescubrir CONNECT durante un máximo de 5 segundos;
  aceptar solamente el mismo host, puerto válido y diferente al fallido.
- AC2: reintentar una sola vez con el puerto descubierto; si no hay cambio publicado,
  reintentar el original tras una pausa breve (servicio todavía iniciando).
- AC3: ADB clásico, PAIRING y UNAUTHORIZED no disparan recuperación automática.
- AC4: cancelar durante discovery impide nuevos intentos y el inicio del servicio.
- AC5: manual TLS, conocidos, QR/código y auto-reconexión comparten recuperación;
  conservar deadlines globales de 25/15 segundos. Retry usa el último puerto intentado.
- AC6: registrar conexión fallida sin afirmar que el transporte estaba conectado.

No cambiar credenciales, puertos de pairing, reglas de lectura de datos ni sesiones
establecidas. El nuevo transporte sólo se publica tras readInfo exitoso, como antes.
Pruebas con ConnectionEnvironment fake, tiempo virtual y emisiones controladas de mDNS.

## Resultado
- AC1/2: prueba roja inicial observada; recuperación implementada y pruebas verdes con
  cambio de puerto, exclusión de otros hosts/puertos inválidos y fallback acotado.
- AC3/4: regresiones verdes de ADB clásico, autorización, pairing y cancelación.
- AC5: auto-reconexión probada con puerto guardado obsoleto; Retry conserva el nuevo
  puerto. Código y QR llegan al mismo connectTo y mantienen sus flujos previos.
- AC6: AppContainer ahora registra «ADB connection or initial device info read failed».
- `:app:compileDebugKotlin :app:testDebugUnitTest :core:adb:testDebugUnitTest :app:assembleDebug`:
  ejecución correcta. Se añadieron cinco pruebas a ConnectionViewModelTest.

La recuperación no puede activar adbd en el destino. Si no publica un puerto nuevo y el
original sigue rechazando conexiones, se entrega el error tras el único reintento.
No se verificó físicamente el endpoint reportado por el usuario.
