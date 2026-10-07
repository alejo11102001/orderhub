# 6. CSRF desactivado

Estado: Aceptado

## Contexto
SonarQube marca como hotspot la desactivación de CSRF en Spring Security.

## Decisión
CSRF se desactiva porque la API es stateless: no hay sesión ni cookie de
autenticación. El JWT viaja en la cabecera `Authorization`, que el navegador
no adjunta automáticamente a peticiones de otros sitios. CORS limita los
orígenes permitidos por entorno.

## Consecuencias
+ Sin tokens CSRF que gestionar en el frontend.
- Deja de ser seguro si el JWT pasa a una cookie o se habilitan sesiones:
  habría que reactivar CSRF.
- El token en `sessionStorage` es legible por JavaScript: un XSS lo
  expondría. Se mitiga evitando `innerHTML` (Angular escapa los bindings por
  defecto). **Pendiente**: el nginx del frontend aún no envía cabeceras de
  seguridad (CSP, `X-Content-Type-Options`, etc.); ver `docs/seguridad.md`.