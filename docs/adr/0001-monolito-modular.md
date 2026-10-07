# 1. Monolito modular

Estado: Aceptado

## Contexto
OrderHub tiene tres dominios (auth, catalog, orders), un solo equipo y una
base de datos. Separar en microservicios desde el inicio añadiría red,
consistencia eventual, despliegues múltiples y trazabilidad distribuida
sin un problema real que lo justifique.

## Decisión
Un único artefacto desplegable con un paquete por dominio. Los módulos no
comparten relaciones JPA: `orders` guarda `userId` y `productId` como
identificadores, no como entidades de otros módulos. Cada módulo expone su
servicio y mantiene sus repositorios internos.

## Alternativas consideradas
- Microservicios desde el día 1: descartado por complejidad operativa.
- Monolito sin fronteras: descartado porque dificulta extraer módulos después.

## Consecuencias
+ Transacciones locales: crear un pedido y descontar stock es atómico.
+ Un solo despliegue, un solo pipeline, depuración simple.
- Se escala todo junto, no por módulo.
- Las fronteras se respetan por convención, no por el compilador.
- Extraer un módulo exigirá mensajería (p. ej. RabbitMQ) y reemplazar
  llamadas directas y transacciones compartidas.