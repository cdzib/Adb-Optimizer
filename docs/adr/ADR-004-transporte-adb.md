# ADR-004 — Transporte ADB: TLS con libadb (actualizado)

## Contexto
Android 11+ expone *Depuración inalámbrica* con emparejamiento TLS (SPAKE2) y puertos
aleatorios anunciados por mDNS. `dadb` solo implementa ADB clásico, así que el celular no
podía conectarse a esas TVs. El proyecto web de referencia (`back-adb-macropay-client`)
resuelve lo mismo con `adb pair` + mDNS.

## Decisión (v2)
El cliente Android usa **`com.github.MuntashirAkon:libadb-android:3.1.1`**, que implementa:

- `AdbConnection` con TLS para `adb-tls-connect`.
- `PairingConnectionCtx` (SPAKE2) para `adb pair`.
- `AdbMdns` sobre `NsdManager` para descubrir `_adb-tls-pairing` y `_adb-tls-connect`.

Se mantiene ADB clásico (5555) para TV Box y equipos con `adb tcpip`.

## Flujos soportados en el celular

| Modo | Uso | Protocolo |
|---|---|---|
| Red | dispositivo ya vinculado | mDNS + TLS |
| Código | TV: muestra IP, puerto y código de 6 dígitos | `pair` + TLS |
| QR | celular/tablet con cámara: escanea el QR de la app | `pair` con password temporal + TLS |
| IP manual | 5555 o puerto TLS escrito a mano | clásico o TLS |

El QR usa el mismo formato que el proyecto web: `WIFI:T:ADB;S:<nombre>;P:<password>;;`.
La app escucha `_adb-tls-pairing`, vincula con ese password y luego espera
`_adb-tls-connect` para abrir la sesión TLS.

## Consecuencias
- (+) Se elimina el bloqueo de Android 11+.
- (+) La identidad RSA se genera en el dispositivo y se reutiliza (SharedPreferences).
- (−) El APK crece (~28 MB debug) por BouncyCastle, Conscrypt y ZXing.
- (−) Un TV sin cámara **no** puede escanear el QR: para TV se usa el código de 6 dígitos.
- (−) `adb tcpip` deja el equipo en modo red hasta reiniciar (el escritorio permite volver
  con `adb usb`).
