# ADR-014 — Estructura por feature dentro de `:app`

## Contexto

La app creció con una organización por capas (`data`, `domain`, `presentation`, `ui`).
Los ViewModels ya estaban agrupados por feature, pero las pantallas Compose vivían en un
directorio `ui/` plano con más de veinte archivos. Eso hacía difícil responder:

- qué archivos pertenecen a una misma capacidad,
- quién es dueño de un estado o componente,
- qué se puede eliminar sin afectar otras pantallas,
- y dónde agregar una feature nueva.

El repositorio es un solo módulo `:app` con cuatro módulos `:core:*`. No hay equipos ni
releases independientes por feature.

## Decisión

1. Organizar `:app` por feature dentro del paquete `com.optimizer.tools.feature.<name>`.
2. Mantener un solo módulo Gradle. No crear `:feature:*` todavía.
3. Mantener `data` y `domain` como capas compartidas; mover código a una feature solo
   cuando deje de ser transversal.
4. Extraer a `core/ui` únicamente componentes usados por dos o más features.
5. Conservar el MVI ligero existente: `StateFlow` para estado y `SharedFlow` para efectos.
   No introducir `BaseMviViewModel` ni Intents obligatorios.
6. Conservar la DI manual de `ADR-008`. No migrar a Hilt como parte de este cambio.
7. Mantener `app/` como composición: `OptimizerApp`, `AppNavGraph`, `AppRoutes` y estado
   global.

## Consecuencias

Positivas:

- Cada feature se puede localizar, revisar y eliminar en un solo paquete.
- Menos conflictos de merge en archivos de UI compartidos.
- Las pantallas nuevas tienen una ubicación predecible.
- No se agrega complejidad de Gradle ni de Hilt.

Negativas:

- Las fronteras no están impuestas por el compilador; dependen de revisión y de este
  documento.
- `feature/remote` todavía reutiliza componentes de `feature/mirror`.
- `data` y `domain` siguen siendo transversales; la extracción por feature queda como
  deuda posterior.
- `AppContainer` sigue existiendo como grafo manual; los adaptadores de `app/` son la
  frontera entre infraestructura y features.

## Actualización (fase 2)

La migración de ViewModels quedó completa:

- Ningún ViewModel recibe `AppContainer`.
- Cada feature declara sus puertos en `<Name>Environment.kt`; `MainViewModel` usa
  `MainEnvironment`.
- `AppContainer<Name>Environment.kt` en `app/` implementa esos contratos y es el único
  punto que conoce `AppContainer`.
- Las features no importan `com.optimizer.tools.data`; los tipos compartidos se movieron
  a `core:model` o `domain/model`.
- Los ViewModels tienen pruebas JVM con fakes y el helper `runViewModelTest`.
- La DI sigue siendo manual; no se introdujeron Hilt ni módulos Gradle nuevos.

El patrón de `Optimize` se replicó en Configuraciones, Shizuku, Inicio, Apps, Instalar,
DPC, Launchers de TV, Control remoto, Conexión, Espejo y la composición raíz.

## Alternativas consideradas

- **Módulos `:feature:*` ahora**: se descartó porque no hay releases ni ownership
  independientes y obligaría a construir un grafo de DI y APIs públicas sin beneficio
  inmediato.
- **Hilt ahora**: se descartó por el costo de build y porque `ADR-008` sigue vigente.
  Revisar cuando el grafo de dependencias o los equipos lo justifiquen.
- **MVI completo con clase base**: se descartó porque los flujos actuales no comparten
  suficientes transiciones como para justificar una abstracción común.

## Criterios para extraer un módulo

Extraer `:feature:<name>` solo si se cumple al menos uno:

- tiene ciclo de vida o release independiente,
- tiene ownership de equipo separado,
- puede eliminarse sin tocar otras features,
- su inclusión en `:app` genera un acoplamiento importante,
- o contiene suficiente lógica de presentación, dominio y datos para aislarse.

Una pantalla nueva, un endpoint o un modelo nuevo no justifican un módulo.
