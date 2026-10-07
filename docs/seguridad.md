# Seguridad

Documento breve de modelo de amenazas, decisiones y límites. OrderHub es un
proyecto de portafolio: **no está endurecido para producción** y aquí se dice
qué falta.

## Activos y actores

| Activo | Por qué importa |
|---|---|
| Credenciales (hash BCrypt) y JWT | Acceso a cuentas y pedidos |
| Pedidos y stock | Integridad del negocio (sobreventa, pedidos ajenos) |
| Secretos (`JWT_SECRET`, `DB_PASSWORD`, contraseña del admin) | Falsificar tokens, leer la base de datos |
| Catálogo | Solo el ADMIN debe poder alterarlo |

Actores: visitante anónimo, cliente autenticado (puede intentar ver pedidos
ajenos o escalar a ADMIN), administrador, y un atacante en la red local.

## Amenazas y mitigaciones

| Amenaza | Mitigación actual | Límite |
|---|---|---|
| Ver/operar pedidos de otro usuario | `loadAuthorized` compara dueño; responde 404 ([ADR 0003](adr/0003-404-en-pedidos-ajenos.md)) | — |
| Escalar privilegios a ADMIN | `register` fija siempre `CUSTOMER`; `/api/products/**` (escritura) exige `ROLE_ADMIN` en el servidor | El guard del frontend es solo UX |
| Falsificar un JWT | Firma HS256 con `JWT_SECRET` (≥ 32 caracteres), expiración configurable (60 min) | Clave simétrica compartida; sin rotación ni revocación |
| Fuerza bruta en login/registro | 5 fallos por IP+email en 15 min → 429 con `Retry-After` ([ADR 0009](adr/0009-rate-limiting-login.md)); respuesta 401 uniforme para email inexistente y contraseña errónea | Límite **por instancia** y en memoria (con 2 réplicas es mayor y se reinicia con el pod); no frena a un atacante que rote IPs; sin bloqueo de cuentas |
| Enumeración de correos | Login uniforme y límite idéntico exista o no la cuenta | `register` responde 409 si el correo existe (el límite lo frena, no lo evita) |
| Sobreventa / stock negativo | `UPDATE ... WHERE stock >= :qty` + `CHECK (stock >= 0)` ([ADR 0002](adr/0002-stock-atomico.md)) | — |
| SQL injection | Consultas JPA/JPQL parametrizadas | — |
| XSS / clickjacking / sniffing | Sin `innerHTML`; Angular escapa los bindings; nginx envía CSP (`script-src 'self'`, sin `unsafe-eval` ni scripts en línea), `X-Frame-Options: DENY`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy` | `style-src` necesita `'unsafe-inline'` (Angular inserta `<style>` en ejecución); sin HSTS porque no hay TLS |
| Robo del JWT por XSS | Se guarda en `sessionStorage` (se borra al cerrar la pestaña) | Legible por JavaScript; una cookie `HttpOnly` sería más robusta pero exigiría reactivar CSRF |
| CSRF | Desactivado a propósito: API stateless con `Authorization: Bearer` ([ADR 0006](adr/0006-csrf-desactivado.md)) | Deja de valer si se usan cookies de sesión |
| CORS abusivo | Orígenes permitidos por entorno (`app.cors.allowed-origins`) | En Compose/K8s el navegador usa el mismo origen vía nginx |
| Secretos en el repositorio | `.env` ignorado por git; `.env.example` con valores ficticios; Vault o `Secret` de Kubernetes ([ADR 0004](adr/0004-vault-modo-dev.md)) | Ver abajo |
| Admin con clave débil | El arranque falla si `APP_ADMIN_PASSWORD` tiene < 12 caracteres ([ADR 0007](adr/0007-seed-admin.md)) | Cambiarla en la configuración no actualiza un admin ya creado |
| Imágenes vulnerables / escalada en el clúster | Trivy (CRITICAL con parche) bloquea la publicación; backend (UID 10001) y frontend (nginx sin privilegios) corren sin root, con `readOnlyRootFilesystem`, sin capabilities y con seccomp por defecto | Trivy solo mira `latest` de la misma construcción; sin firma de imágenes; Postgres arranca como root y baja de privilegios (ver `infra/k8s/README.md`) |
| Movimiento lateral entre pods | NetworkPolicy de ingreso: solo ingress-nginx → frontend, ingress-nginx/frontend → backend y backend → postgres (verificado en kind) | Solo tráfico entrante; depende de que el CNI las aplique |
| Exposición de métricas | `/actuator/prometheus` no se enruta por nginx ni por el Ingress; en `k8s` solo se expone `health`; en Compose todos los puertos están en `127.0.0.1` | El endpoint sigue siendo público dentro de la red del backend |

## Decisiones de diseño relevantes

- **Contraseñas**: BCrypt (`BCryptPasswordEncoder`), máximo 72 caracteres
  (límite del algoritmo, validado en `RegisterRequest`).
- **Sin sesión de servidor**: la API es stateless; cada petición lleva su JWT.
- **El rol viaja en el token** (claim `role`). Si se cambia el rol de un
  usuario en la base de datos, el token anterior conserva el rol viejo hasta
  que expire.
- **Mensajes de error**: `ProblemDetail`; no se devuelven trazas.
- **Registros**: el seeder del admin nunca imprime la contraseña.

## Límites conocidos (lo que NO está cubierto)

- Vault en **modo dev**: en memoria, sin TLS, con token raíz fijo.
- Un `Secret` de Kubernetes solo está codificado en base64; quien pueda leerlo
  en el clúster obtiene `JWT_SECRET`, `DB_PASSWORD` y la contraseña del admin.
- Todo el tráfico es HTTP: **no hay TLS** (ni en Compose ni en el Ingress de kind).
- `/actuator/prometheus` y `/actuator/health/**` son públicos para quien alcance el backend; Elasticsearch,
  Kibana y Prometheus no tienen autenticación (los puertos de Compose están ligados a `127.0.0.1`). Solo válido en una máquina local.
- Rate limiting solo por instancia y en memoria; sin bloqueo de cuentas, sin 2FA ni recuperación de
  contraseña, sin verificación de correo.
- Sin refresco ni revocación de tokens; el frontend no cierra la sesión solo
  cuando el token expira (las peticiones empiezan a devolver 401).
- Sin auditoría de acciones del administrador.
- El pago es un cambio de estado: **no se procesan datos de tarjeta**, así que
  PCI-DSS no aplica, pero tampoco hay un flujo de pago real.
- Las dependencias se actualizan a mano; no hay escaneo de dependencias en CI
  (Dependabot/OWASP) más allá de Trivy sobre la imagen.
- El token de un usuario borrado de la base de datos produce un 500 al crear pedidos (no valida que el usuario siga existiendo).

## Si tuviera que llevarlo a producción (prioridad sugerida)

1. TLS en el Ingress (cert-manager) y HSTS.
2. Vault real (almacenamiento persistente, unseal, AppRole o autenticación de
   Kubernetes) o un gestor de secretos del proveedor.
3. Rate limiting distribuido (Redis o gateway) para `/api/auth/*`.
4. Autenticación en Prometheus, Elasticsearch y Kibana, y un puerto de gestión separado para Actuator.
5. Tokens de acceso cortos con refresco, o cookie `HttpOnly` + CSRF.
6. Despliegue por GitOps con tags/digests inmutables y CI como requisito previo
   a publicar imágenes.
