# MCP Implementation Status

## Objetivo

Añadir una capa MCP para acelerar desarrollo de plugins usando información del Object Inspector con arquitectura mantenible a largo plazo.

## Plan General

1. Fase 0: Contrato interno estable para inspección y base de integración.
2. Fase 1: Bridge/plugin para exponer herramientas MCP read-only.
3. Fase 2: Hardening, permisos, límites y observabilidad.
4. Fase 3: Integración en core con API pública versionada.

## Progreso por pasos

- [x] Fase 0.1 Definir contrato de inspección (`InspectorContract`).
- [x] Fase 0.1 Crear snapshots base (`InspectorRootSnapshot`, `InspectorObjectSnapshot`, `InspectorSlotSnapshot`).
- [x] Fase 0.1 Implementar servicio inicial (`InspectorContractImpl`) sobre `ObjectInspector`.
- [x] Fase 0.1 Exponer el servicio en `DarkBotPluginApiImpl`.
- [ ] Fase 0.2 Definir esquemas formales de tools MCP (input/output).
- [ ] Fase 0.2 Añadir serialización JSON estable para snapshots y schemas.
- [ ] Fase 1 Implementar bridge MCP consumiendo esta capa interna.
- [ ] Fase 2 Añadir controles de seguridad, rate limit y métricas.
- [ ] Fase 3 Migrar a API pública de core y cerrar dependencias internas.

## Estado actual

Actualmente estamos en **Fase 0.2**.

## Qué sigue inmediatamente

1. Añadir catálogo de tools MCP con schema explícito.
2. Añadir serializador JSON estable para salida de tools.
3. Exponer estas capacidades en el contenedor de Plugin API.
4. Ejecutar build y verificar diagnóstico limpio.
