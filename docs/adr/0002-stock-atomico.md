# 2. Descuento de stock atómico con SQL condicional

Estado: Aceptado

## Contexto
Dos compradores pueden pedir la última unidad a la vez. Leer el producto
(`findById`), comprobar el stock y guardar (`setStock`) tiene una condición
de carrera: ambos leen 1 y ambos descuentan.

## Decisión
Descontar con una sola sentencia:
`UPDATE products SET stock = stock - :qty WHERE id = :id AND stock >= :qty`.
Las filas afectadas indican el resultado: 0 significa stock insuficiente
(HTTP 409) y la transacción completa se revierte. La devolución al cancelar
usa `stock = stock + :qty`.

## Alternativas consideradas
- Bloqueo pesimista (`SELECT ... FOR UPDATE`): correcto, pero más contención.
- Bloqueo optimista (`@Version`): requiere reintentos ante conflicto.

## Consecuencias
+ La base serializa el acceso; no hay sobreventa ni stock negativo
  (además existe un `CHECK (stock >= 0)`).
+ Verificado con un test de concurrencia: 10 hilos, 1 unidad, 1 ganador.
- La lógica de stock vive en una consulta, no en la entidad.
- Se evitó `clearAutomatically` en la devolución: vaciaba el contexto de
  persistencia y el cambio de estado del pedido no se guardaba (bug real
  detectado y cubierto con un test de integración).