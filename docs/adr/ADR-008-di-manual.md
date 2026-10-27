# ADR-008 — Inyección de dependencias manual en la v1

## Contexto
Hilt + KSP agrega tiempo de build, complejidad de setup y un punto de fallo en cada
actualización del plugin. La app tiene un grafo de dependencias trivial: catálogo, journal,
shell y motor.

## Decisión
`AppContainer` construye las dependencias a mano. `MainActivity` compone adaptadores
`AppContainer<Name>Environment` y crea los ViewModels con `ViewModelProvider.Factory`
explícitas. Los ViewModels solo conocen puertos estrechos, no el contenedor completo.

## Actualización
La migración de todos los ViewModels a puertos estrechos se completó en la fase 2. La DI
manual se mantiene; Hilt sigue pospuesto según ADR-014.

## Consecuencias
- (+) Build y tests más rápidos; cero magia en el arranque.
- (−) Si el grafo crece (multi-flujo, background work, múltiples conexiones), hay que
  migrar a Hilt. El criterio de migración: más de un ViewModel de feature o inyección
  en `Service`/`Worker`.
