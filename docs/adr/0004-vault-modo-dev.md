# 4. Vault en modo dev

Estado: Aceptado (solo para aprendizaje local)

## Contexto
Se quería sacar `db.password` y `jwt.secret` (y, opcionalmente,
`admin.password`, ver ADR 0007) del código y del `.env`, y
practicar el flujo de un gestor de secretos.

## Decisión
Vault en modo dev dentro de Docker Compose, con Spring Cloud Vault leyendo
`secret/orderhub`. Compose publica Vault solo en `127.0.0.1` y sigue necesitando el `.env` para arrancar Vault
(`VAULT_DEV_TOKEN`) y Postgres (`DB_PASSWORD`). En Kubernetes se usa un perfil `k8s` que desactiva Vault
y toma los secretos de un Secret de Kubernetes.

## Alternativas consideradas
- Variables de entorno directas: más simple, sin rotación ni auditoría.

## Consecuencias
+ El backend no conoce los secretos hasta arrancar.
- Modo dev: datos en memoria (se pierden al reiniciar), sin TLS y con token
  raíz fijo. No es válido en producción.
- Un Secret de Kubernetes solo está codificado en base64. Los pods no montan el
  token de ServiceAccount y una NetworkPolicy limita quién puede hablar con
  cada servicio, pero quien tenga acceso de lectura a los Secrets del namespace
  obtiene todos los valores.
- Producción requeriría: almacenamiento persistente, unseal, TLS, políticas
  de mínimo privilegio y AppRole o autenticación de Kubernetes.