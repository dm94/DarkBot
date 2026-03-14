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
- [x] Fase 1 Implementar bridge MCP consumiendo esta capa interna (bridge service JSON-RPC + executor read-only + socket localhost transport + arranque en runtime).
- [x] Fase 2 Añadir controles de seguridad, rate limit y métricas.
- [ ] Fase 3 Migrar a API pública de core y cerrar dependencias internas.

## Estado actual

Actualmente estamos en **inicio de Fase 3**.

## Qué sigue inmediatamente

1. Definir contrato público de registro de tools para plugins externos.
2. Exponer API versionada estable para transporte y autorización MCP.
3. Aislar dependencias internas (`InspectorContractImpl`) detrás de interfaces públicas.
4. Añadir pruebas de compatibilidad de versión para tools registradas por plugins.
