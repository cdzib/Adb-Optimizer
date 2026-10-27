# Selección del servicio de emparejamiento QR

Estado: VERIFIED (regresiones JVM y compilación; prueba física QR pendiente)

## Evidencia
El usuario confirma que el teléfono queda en «Vinculando» tras escanear.
ConnectionViewModel intenta todos los anuncios adb-tls-pairing con la contraseña del QR.
AOSP AdbDebuggingManager.PairingThread registra el serviceName recibido por MSG_PAIR_QR_CODE;
este nombre corresponde al campo S del QR, no a cualquier anuncio de pairing.
Referencia: https://github.com/aosp-mirror/platform_frameworks_base/blob/master/services/core/java/com/android/server/adb/AdbDebuggingManager.java
No hay log reciente del fallo disponible en el dispositivo USB conectado: se corrige
un defecto comprobado en el código, sin afirmar una causa física confirmada.

## Criterios
- AC1: ignorar anuncios cuyo nombre no coincida con el QR activo; no enviarles su contraseña.
- AC2: emparejar el anuncio correspondiente con el campo P del QR; sólo después de éxito
  descubrir CONNECT y conectar por TLS al mismo host, nunca al puerto de pairing.
- AC3: no conectar si falla pairing; conservar reintento con cooldown.
- AC4: un QR regenerado ignora anuncios del QR anterior; cancelar elimina la búsqueda.
- AC5: si el servicio del mismo QR cambia de puerto, reemplazar el endpoint obsoleto.

Mantener nombres exactos emitidos por Android NSD (sin matching por prefijo).
Pruebas de ViewModel con anuncios explícitos, captura del password generado y reloj virtual.
No se altera el protocolo SPAKE2 ni se presenta el escaneo como emparejamiento exitoso.

## Validación
- RED observado: la prueba de anuncios ajenos detectó que se enviaba la contraseña a
  `another-phone` antes de recibir el anuncio del QR activo.
- GREEN: filtrar por nombre exacto, host no vacío y puerto válido; actualizar el anuncio
  por nombre de servicio para abandonar puertos obsoletos.
- Tres regresiones: recorrido QR→pair→TLS completo (incluye exclusión de host ajeno),
  regeneración/cancelación y pairing fallido seguido de anuncio con puerto nuevo.
- Timeout registra si se encontró el servicio correcto, el último error y errores NSD,
  sin registrar el QR ni su contraseña.
- `:app:compileDebugKotlin :app:testDebugUnitTest :core:adb:testDebugUnitTest :app:assembleDebug`:
  correcto. APK actualizado: `app/build/outputs/apk/debug/app-debug.apk`.
- No se ha reproducido el escaneo físico entre dos teléfonos con este APK.
