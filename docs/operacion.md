# Operación

Guía práctica para operar OrderHub en Docker Compose y en Kubernetes (kind).
Los comandos son para **PowerShell** salvo que se indique otra cosa.

## Estado y salud

| Qué | Compose | Kubernetes (kind) |
|---|---|---|
| App | http://localhost:4200 | http://localhost:8081 |
| Salud (readiness) | http://localhost:8080/actuator/health/readiness | http://localhost:8081/actuator/health/readiness |
| Métricas Prometheus | http://localhost:8080/actuator/prometheus | no desplegado |

```powershell
docker compose -f infra/docker/docker-compose.yml ps
kubectl -n orderhub get pods
```

## Métricas (solo Compose)

El backend expone Micrometer en `/actuator/prometheus` con la etiqueta
`application="backend"`. Prometheus lo raspa cada 15 s
(`infra/prometheus/prometheus.yml`).

1. **Prometheus**: http://localhost:9090 → *Status → Targets*: el job
   `orderhub-backend` debe estar en `UP`.
2. **Grafana**: http://localhost:3000. Usuario `admin` y la contraseña de
   `GRAFANA_PASSWORD` (`.env`). El datasource Prometheus y el dashboard
   *Spring Boot 3.x Statistics* (carpeta *OrderHub*) se cargan por archivo
   desde `infra/grafana/provisioning` e `infra/grafana/dashboards`; no hay que
   importar nada a mano. El JSON está en el esquema v2 de dashboards, que
   exige Grafana 13 o superior (Compose usa `grafana/grafana:latest`; se
   verificó con la 13.2.3). Si fijas una versión anterior, el dashboard no
   cargará.
   Limitación verificada: en el panel *HTTP Statistics*, *Request Count* mostró
   datos, pero *Response Time* quedó vacío (el dashboard es una adaptación del
   de Spring Boot 2.1 y no se ajustaron sus consultas).
3. Consultas útiles en Prometheus:

   ```promql
   rate(http_server_requests_seconds_count{application="backend"}[1m])
   rate(http_server_requests_seconds_sum{application="backend"}[5m]) / rate(http_server_requests_seconds_count{application="backend"}[5m])
   jvm_memory_used_bytes{application="backend", area="heap"}
   hikaricp_connections_active{application="backend"}
   ```

   La segunda consulta es la latencia media: el backend no publica buckets de
   histograma, así que no hay percentiles con `histogram_quantile`.

## Logs

El backend escribe JSON en formato ECS por la salida estándar
(`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`).

```powershell
# Compose
docker logs -f orderhub-backend

# Kubernetes
kubectl -n orderhub logs deploy/backend --tail=100 -f
kubectl -n orderhub logs deploy/backend --previous     # contenedor anterior si reinició
```

**Kibana** (solo Compose): Filebeat lee los logs del contenedor
`orderhub-backend` y los envía a Elasticsearch en índices
`orderhub-logs-YYYY.MM.dd`.

1. Abre http://localhost:5601 → *Stack Management → Data Views → Create*.
2. Nombre y patrón: `orderhub-logs-*`; campo de tiempo `@timestamp`.
3. En *Discover* filtra, por ejemplo, `log.level : "ERROR"`.

Elasticsearch y Kibana consumen memoria (Elasticsearch fija 512 MB de heap);
levántalos solo si los necesitas:
`docker compose up -d elasticsearch kibana filebeat`.

## Arranque y orden de dependencias (Compose)

```powershell
cd infra/docker
docker compose up -d postgres vault          # 1. base y Vault
# 2. cargar secretos en Vault (ver siguiente sección)
docker compose up -d --build backend frontend
```

El backend lee `db.password` y `jwt.secret` de Vault **al arrancar**. Si
arranca antes de que existan, falla; basta con cargar los secretos y reiniciarlo
(`docker compose restart backend`).

## Qué hacer si Vault pierde los secretos

Vault corre en **modo dev**: guarda todo en memoria, así que se pierde cada vez
que el contenedor se reinicia o se recrea ([ADR 0004](adr/0004-vault-modo-dev.md)).

**Síntoma**: el backend no arranca o se reinicia en bucle con errores de
Vault o de placeholder sin resolver (`db.password` / `jwt.secret`).

**Recuperación**:

1. Confirma que Vault está arriba: `docker compose ps vault`.
2. Vuelve a cargar los secretos. Las variables salen de tu `.env` (no las
   pegues en chats, tickets ni el historial compartido):

   ```powershell
   docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=<VAULT_DEV_TOKEN> orderhub-vault `
     vault kv put secret/orderhub db.password=<DB_PASSWORD> jwt.secret=<JWT_SECRET>
   ```

   `kv put` reemplaza **todas** las claves de la ruta. Para la contraseña del
   admin usa `kv patch ... admin.password=<valor>` o inclúyela en el mismo `put`.
3. Reinicia el backend: `docker compose restart backend`.

**Ten en cuenta**:

- `DB_PASSWORD` debe ser la misma con la que se **inicializó** el volumen de
  Postgres (`pgdata`). Cambiarla en `.env` después no cambia la contraseña del
  usuario dentro de la base de datos.
- Si regeneras `JWT_SECRET`, todos los tokens emitidos dejan de ser válidos:
  los usuarios tendrán que volver a iniciar sesión. Genera uno de al menos 32
  caracteres, por ejemplo `openssl rand -base64 48`.
- El usuario admin ya creado vive en la base de datos y no se pierde con Vault.
- En Kubernetes no se usa Vault: los secretos están en el `Secret`
  `orderhub-secrets`. Si se borra, recréalo (README, sección Kubernetes) y
  reinicia los pods: `kubectl -n orderhub rollout restart deployment/backend`.

## Kubernetes: despliegue y rollback

Las imágenes llevan tag inmutable `sha-<commit>` ([ADR 0005](adr/0005-tags-inmutables.md)).

### Desplegar una versión nueva

1. Haz push a `main` y espera a que `ci.yml` **y** `images.yml` terminen en
   verde (`images.yml` no espera a CI).
2. Edita `image:` en `infra/k8s/20-backend.yaml` y/o `30-frontend.yaml` con el
   nuevo `sha-<commit>`, y haz commit.
3. Aplica y vigila el *rolling update*:

   ```powershell
   kubectl apply -f infra/k8s/
   kubectl -n orderhub rollout status deployment/backend
   kubectl -n orderhub rollout status deployment/frontend
   ```

   El backend tiene 2 réplicas y *readiness probe*: Kubernetes no envía tráfico
   a un pod nuevo hasta que `/actuator/health/readiness` responde.

### Runbook de rollback

**Cuándo**: tras un despliegue, `rollout status` no termina, los pods quedan en
`CrashLoopBackOff` / `0/1 Ready`, o la app responde con errores.

1. **Diagnóstico rápido**

   ```powershell
   kubectl -n orderhub get pods
   kubectl -n orderhub describe pod <pod>
   kubectl -n orderhub logs deploy/backend --tail=100
   ```

2. **Vuelve a la revisión anterior** (el *rolling update* no retira los pods
   viejos mientras los nuevos no estén *Ready*, así que a menudo el servicio
   sigue sirviendo con la versión buena):

   ```powershell
   kubectl -n orderhub rollout history deployment/backend
   kubectl -n orderhub rollout undo deployment/backend
   kubectl -n orderhub rollout status deployment/backend
   ```

   Para una revisión concreta: `rollout undo deployment/backend --to-revision=<N>`.
   Con tags inmutables el rollback devuelve **exactamente** la imagen previa.

3. **Verifica**:

   ```powershell
   kubectl -n orderhub get deployment backend -o jsonpath="{.spec.template.spec.containers[0].image}"
   curl.exe -s http://localhost:8081/actuator/health/readiness
   ```

4. **Reconcilia Git** (paso que suele olvidarse): el manifiesto aún apunta al
   tag defectuoso; el siguiente `kubectl apply -f infra/k8s/` lo volvería a
   desplegar. Revierte el commit que subió el tag (`git revert <commit>`).

**Migraciones de base de datos**: Flyway solo avanza. Un rollback de la imagen
**no** deshace migraciones ya aplicadas. Por eso las migraciones deben ser
compatibles hacia atrás (añadir columnas nulas o con valor por defecto, y
eliminar en un despliegue posterior). Con `ddl-auto=validate`, una versión
antigua puede negarse a arrancar si el esquema cambió de forma incompatible.

## Reconstruir imágenes en local

```powershell
# Compose (construye desde el código)
docker compose -f infra/docker/docker-compose.yml up -d --build backend frontend

# Imágenes con tag explícito para kind (sin publicar)
$sha = git rev-parse --short HEAD
docker build -t ghcr.io/alejo11102001/orderhub-backend:sha-$sha backend
docker build -t ghcr.io/alejo11102001/orderhub-frontend:sha-$sha frontend
kind load docker-image ghcr.io/alejo11102001/orderhub-backend:sha-$sha --name orderhub
kind load docker-image ghcr.io/alejo11102001/orderhub-frontend:sha-$sha --name orderhub
```

Los manifiestos usan `imagePullPolicy: IfNotPresent`, de modo que una imagen
cargada con `kind load` se usa sin descargar de GHCR.

## Datos de demostración

Ver README → *Primeros pasos*. Se activan con `APP_DEMO_DATA=true` y son
idempotentes (no duplican productos al reiniciar).
