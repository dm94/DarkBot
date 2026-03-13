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
- [x] Fase 0.2 Definir esquemas formales de tools MCP (input/output).
- [x] Fase 0.2 Añadir serialización JSON estable para snapshots y schemas.
- [~] Fase 1 Implementar bridge MCP consumiendo esta capa interna (executor interno iniciado).
- [ ] Fase 2 Añadir controles de seguridad, rate limit y métricas.
- [ ] Fase 3 Migrar a API pública de core y cerrar dependencias internas.

## Estado actual

Actualmente estamos en **inicio de Fase 1**.

## Qué sigue inmediatamente

1. Implementar bridge MCP read-only que consuma `InspectorContract`.
2. Exponer tools MCP con validación de entrada y mensajes de error claros.
3. Añadir límites de profundidad/tamaño en respuestas para evitar payloads grandes.
4. Validar integración end-to-end con pruebas manuales de herramientas.
