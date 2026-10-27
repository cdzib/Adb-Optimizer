# Fase 0 — Spike de validación (4 días)

Objetivo: decidir con datos, no con supuestos, antes de invertir en Fase 2/3.

## A. Emparejamiento TLS (implementado, falta validar en hardware)

Ya se integro `libadb-android` (SPAKE2 + TLS) con `NsdManager` y QR. Lo que falta es
validarlo en dispositivos reales:

| # | Prueba | Criterio de éxito |
|---|---|---|
| A1 | Vincular con codigo contra una TV Android 11+ | shell funcional y métricas correctas |
| A2 | Vincular con QR contra un celular/tablet | pairing + conexión TLS automática |
| A3 | PC con `adb pair` como referencia cruzada | mismo resultado que la app |
| A4 | ¿Sobrevive la vinculación al reinicio del dispositivo? | sí/no (documentar) |
| A5 | Cuántos TVs del parque aceptan 5555 | % para decidir si el modo clásico se mantiene visible |

**Salida:** ADR-004 confirmado con evidencia real y ajustes de UI si algún fabricante
se comporta distinto.

## B. Parque real de TVs

Probar en al menos: Android TV genérico (Amlogic), Chromecast/Google TV, Hisense,
Xiaomi/Redmi TV, Nvidia Shield, TV Box con Android 9.

Por dispositivo registrar:
- puerto ADB disponible y si sobrevive reinicios,
- `dumpsys meminfo`, `thermalservice`, `cpuinfo` (formatos distintos),
- si `settings put global` realmente persiste (Xiaomi es el sospechoso habitual),
- si `pm disable-user --user 0` afecta el launcher o el arranque.

**Salida:** `docs/evidence/<modelo>/` con capturas crudas y notas.

## C. Escritorio

- Verificar `adb` empaquetado en macOS (arm64 y x64), Windows y Linux.
- Confirmar que `adb mdns services` funciona para descubrir TVs.
- Tamaño final del instalador.

## D. Congelar el contrato

Reemplazar los fixtures `synthetic: true` por capturas reales y agregar recetas nuevas
solo si pasan `verify` en al menos 3 dispositivos distintos.

## Criterio de salida de Fase 0

1. Sabemos exactamente qué dispositivos se conectan y cómo.
2. Existe evidencia de que cada receta del catálogo funciona o está marcada como
   `optional`.
3. La decisión TLS está escrita y aceptada.
