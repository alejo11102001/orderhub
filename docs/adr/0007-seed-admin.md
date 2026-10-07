# 7. Seed del usuario administrador por configuración

Estado: Aceptado

## Contexto
`/api/auth/register` solo crea usuarios `CUSTOMER`, así que el primer ADMIN se
creaba a mano con SQL (`UPDATE users SET role = 'ADMIN' ...`). Eso no es
reproducible, no queda documentado y obliga a entrar a la base de datos en
cada entorno nuevo (Compose, kind, CI).

## Decisión
Un `ApplicationRunner` (`AdminSeeder`) crea el ADMIN al arrancar a partir de
`app.admin.email` y `app.admin.password`:

- Si falta alguna de las dos, no hace nada y solo registra un log informativo
  (nunca imprime la contraseña).
- Si la contraseña tiene menos de 12 caracteres, el arranque falla con un
  mensaje claro: un admin con clave débil es peor que no tener admin.
- Es idempotente: si el email ya existe no se modifica ni su hash ni su rol.
  Si dos réplicas arrancan a la vez, la restricción `UNIQUE(email)` resuelve la
  carrera y la perdedora lo registra y continúa.
- La contraseña se hashea con el `PasswordEncoder` (BCrypt) de la aplicación.

Los valores se inyectan por entorno: `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD`
desde `.env` en Compose y desde el Secret `orderhub-secrets` en Kubernetes
(claves opcionales). En el perfil por defecto, la contraseña también puede
venir de Vault (`secret/orderhub`, clave `admin.password`); la variable de
entorno tiene prioridad si no está vacía.

## Alternativas consideradas
- **SQL manual**: lo que había. No es reproducible ni auditable.
- **Migración Flyway con hash fijo**: descartada. Guardaría una credencial
  (aunque sea un hash) en el repositorio, igual para todos los entornos, y
  una migración aplicada no se puede editar para rotarla.
- **Endpoint de bootstrap**: añade superficie de ataque pública para un
  problema que solo ocurre una vez.

## Consecuencias
+ Entornos nuevos con admin sin tocar la base de datos.
+ La credencial vive en el gestor de secretos / Secret, no en el repo.
- Cambiar la contraseña en la configuración **no** actualiza un admin que ya
  existe (decisión deliberada para no pisar cambios); hay que hacerlo en la BD.
- La contraseña en `.env` o en un Secret de Kubernetes (base64) sigue siendo
  legible por quien tenga acceso a ese entorno.
