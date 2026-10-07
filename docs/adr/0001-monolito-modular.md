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
identificadores, no como entidades de otros módulos (sí hay claves foráneas
en la base de datos). Cada dominio vive en su paquete con su controlador,
servicio y repositorios.

Excepción real: `OrderService` consulta directamente `UserRepository` (para
resolver el id del usuario autenticado) y `ProductRepository` (para leer el
producto y descontar o devolver stock con las consultas atómicas del ADR 0002),
en lugar de pasar por servicios de `auth` y `catalog`. Se aceptó por
simplicidad y porque el descuento debe ocurrir en la misma transacción.

## Alternativas consideradas
- Microservicios desde el día 1: descartado por complejidad operativa.
- Monolito sin fronteras: descartado porque dificulta extraer módulos después.

## Consecuencias
+ Transacciones locales: crear un pedido y descontar stock es atómico.
+ Un solo despliegue, un solo pipeline, depuración simple.
- Se escala todo junto, no por módulo.
- Las fronteras se respetan por convención, no por el compilador (y la
  excepción anterior muestra que ya hay un acoplamiento entre `orders` y los
  repositorios de otros módulos).
- Camino de extracción de un módulo: mensajería (p. ej. RabbitMQ) y reemplazar
  llamadas directas y transacciones compartidas.