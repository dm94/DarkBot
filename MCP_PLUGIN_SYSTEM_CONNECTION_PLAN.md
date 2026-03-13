# MCP Plugin/System Connection Plan

## Objetivo

Definir una forma clara de conectar clientes MCP externos con DarkBot para exponer herramientas de inspección (Object Inspector) de forma segura, mantenible y escalable.

## Opciones de arquitectura

### Opción A: Plugin MCP externo

- Crear un plugin que registre tools MCP y use la API interna ya expuesta (`InspectorContract`, `InspectorMcpToolExecutor`).
- El plugin abre su propio transporte (socket o stdio) y enruta las peticiones MCP.
- Ventaja: despliegue rápido, iteración ágil sin tocar demasiado el core.
- Riesgo: compatibilidad más frágil entre versiones si cambia API interna.

### Opción B: Sistema MCP en core

- Integrar el bridge MCP directamente en el core de DarkBot.
- Exponer el transporte como servicio oficial y versionado.
- Ventaja: mayor estabilidad a largo plazo y contrato más estricto.
- Riesgo: ciclo de release más lento y mayor coste de mantenimiento en core.

### Opción C: Híbrida (recomendada)

- Mantener el runtime de transporte en core y dejar tools avanzadas en plugins.
- Core ofrece:
  - transporte seguro,
  - registro de tools,
  - políticas de permisos y rate limit.
- Plugins aportan herramientas por dominio (combat, map, stats, inspector extendido).
- Resultado: base robusta + extensibilidad alta.

## Diseño recomendado para conexión MCP

### 1) Transporte

- Mantener socket localhost como base inicial.
- Añadir soporte opcional de stdio para clientes MCP que no usen red local.
- Definir framing por línea JSON y respuesta por línea JSON.

### 2) Bridge MCP

- Soportar al menos:
  - `tools/list`
  - `tools/call`
- Validar request y devolver errores JSON-RPC coherentes.
- Mantener payload acotado para evitar sobrecarga.

### 3) Ejecución de tools

- Resolver tool por id con registro versionado.
- Validar argumentos por schema antes de ejecutar.
- Ejecutar siempre en modo read-only para inspector.

### 4) Seguridad

- Permisos por tool:
  - `read.safe` (default),
  - `read.privileged`,
  - `write.disabled` (bloqueado por defecto).
- Lista blanca de orígenes cliente.
- Límite de requests por cliente y ventana temporal.

### 5) Observabilidad

- Métricas mínimas:
  - total de requests,
  - errores por tool,
  - latencia p50/p95,
  - truncamientos de payload.
- Logs estructurados sin datos sensibles.

## Flujo de conexión (cliente MCP → DarkBot)

1. Cliente abre conexión con transporte MCP de DarkBot.
2. Cliente invoca `tools/list`.
3. DarkBot responde con schemas soportados.
4. Cliente invoca `tools/call` con argumentos.
5. Bridge valida, ejecuta tool y devuelve resultado serializado.

## Plan de implementación sugerido

### Paso 1: Contrato estable

- Mantener contrato de inspector versionado (`v1`).
- Congelar ids de tools actuales para compatibilidad.

### Paso 2: Transporte multi-modo

- Socket localhost estable.
- Stdio opcional activable por parámetro de arranque.

### Paso 3: Capa de seguridad

- Permisos por tool.
- Rate limit por IP/cliente.
- Rechazo explícito de operaciones no read-only.

### Paso 4: End-to-end y hardening

- Tests de integración con `tools/list` y `tools/call`.
- Tests de carga ligera para validar límites.
- Manejo de desconexiones abruptas y reinicios.

### Paso 5: API pública

- Publicar contrato MCP interno como API pública documentada.
- Definir política de versionado y deprecación.

## Recomendación final

La mejor estrategia para DarkBot es **híbrida**:

- núcleo MCP en core para estabilidad,
- extensión de tools vía plugins para velocidad de desarrollo.

Esto permite conectar clientes MCP externos de forma confiable hoy, sin bloquear evolución futura del ecosistema de plugins.
