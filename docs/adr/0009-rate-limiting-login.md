# 9. Rate limiting de login y registro en memoria

Estado: Aceptado

## Contexto
`POST /api/auth/login` permitía intentos ilimitados, lo que facilita la fuerza
bruta y el *credential stuffing*. Había que limitarlos sin añadir dependencias
pesadas ni infraestructura nueva (Redis, gateway).

## Decisión
Un limitador propio de ventana deslizante (`AttemptLimiter`), en memoria y
por instancia, aplicado en `AuthController`:

- **Qué cuenta**: solo los *fallos* (credenciales inválidas en `login`, correo
  ya existente en `register`). Los errores de validación (400) no cuentan.
- **Clave**: `acción + IP + email normalizado`. La IP sale de
  `request.getRemoteAddr()`; con `server.forward-headers-strategy=native`
  Tomcat usa `X-Forwarded-For` cuando viene de un proxy interno (nginx del
  frontend o ingress-nginx), de modo que se limita al cliente real y no al proxy.
- **Umbral**: `app.security.login-max-attempts` (por defecto 5) fallos dentro de
  `app.security.login-window-minutes` (por defecto 15 min).
- **Respuesta**: `429 Too Many Requests` con `ProblemDetail` y cabecera
  `Retry-After` (segundos hasta que caduque el fallo más antiguo).
- **No revela si el correo existe**: la clave y el contador se comportan igual
  para cuentas existentes e inexistentes.
- **Un login correcto reinicia el contador** de esa clave.
- **Memoria acotada**: los fallos vencidos se podan en cada operación y, si el
  mapa supera 10 000 claves, se purgan las caducadas.
- El frontend muestra un mensaje con el tiempo de espera (`Retry-After`).

## Alternativas consideradas
- **Bucket4j / Resilience4j / Caffeine**: más completas, pero añaden una
  dependencia (Caffeine no está en el classpath) para un mapa con expiración
  de ~80 líneas.
- **Redis**: límite compartido entre réplicas; añade un servicio que operar,
  desproporcionado para este proyecto.
- **`limit-req` en el Ingress / gateway**: limita por ritmo de peticiones, no
  por fallos de autenticación, y no distingue emails; es complementario.
- **Bloqueo de cuenta**: permite que un tercero bloquee a un usuario legítimo
  (denegación de servicio); por eso la clave incluye la IP.

## Consecuencias
+ Frena la fuerza bruta básica sin dependencias nuevas ni cambios de infraestructura.
+ Configurable por entorno y fácil de probar (reloj inyectable).
- **El límite es por instancia**: con varias réplicas (el backend tiene 2 en
  Kubernetes) el máximo efectivo es hasta `réplicas × límite`, y el contador
  se pierde al reiniciar un pod. Para un límite global haría falta Redis o
  un almacén compartido; `limit-req` en el Ingress sería una defensa adicional.
- Verificado en kind con 2 réplicas: el balanceo reparte los intentos, así que los 429 empiezan tras ~5 fallos **por réplica**
  (hacia el 9.º intento), no tras 5 en total.
- Un atacante con muchas IPs, o que rote emails desde una misma IP, no queda
  limitado (la clave incluye el email). Sigue siendo una defensa básica.
- Si el proxy no es de confianza o no envía `X-Forwarded-For`, todos los
  clientes compartirían la IP del proxy.
- `register` revela si un correo existe (409); el límite lo frena pero no lo evita.
