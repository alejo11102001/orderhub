# 3. 404 en lugar de 403 para pedidos ajenos

Estado: Aceptado

## Contexto
Un cliente que consulta `/api/orders/{id}` de otro usuario no debe poder
deducir qué identificadores existen.

## Decisión
Si el pedido no pertenece al usuario autenticado (y no es ADMIN), la API
responde 404, igual que si no existiera.

## Alternativas consideradas
- 403 Forbidden: revela que el recurso existe (enumeración de IDs).

## Consecuencias
+ Se evita la enumeración de pedidos.
- Quien depure verá un 404 aunque el pedido exista; el motivo se documenta
  en el código.