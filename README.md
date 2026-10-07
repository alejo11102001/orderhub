# OrderHub

[![CI](https://github.com/alejo11102001/orderhub/actions/workflows/ci.yml/badge.svg)](https://github.com/alejo11102001/orderhub/actions/workflows/ci.yml)
[![Images](https://github.com/alejo11102001/orderhub/actions/workflows/images.yml/badge.svg)](https://github.com/alejo11102001/orderhub/actions/workflows/images.yml)

Plataforma de pedidos (mini e-commerce) construida como proyecto de aprendizaje
de extremo a extremo: aplicación, calidad, contenedores, CI/CD, observabilidad,
gestión de secretos, infraestructura como código y Kubernetes.

![Catálogo](docs/img/app-catalogo.png)

<details>
<summary>Más capturas</summary>

| | |
|---|---|
| ![Login](docs/img/app-login.png) | ![Registro](docs/img/app-registro.png) |
| ![Carrito](docs/img/app-carrito.png) | ![Mis pedidos](docs/img/app-pedidos.png) |
| ![Administración](docs/img/app-admin.png) | ![Catálogo en modo oscuro](docs/img/app-catalogo-oscuro.png) |

Vista móvil (390 px): ![Catálogo móvil](docs/img/app-catalogo-movil.png)

Dashboard de Grafana (aprovisionado desde `infra/grafana/dashboards`):

![Grafana](docs/img/grafana-dashboard.png)

</details>

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Security (JWT), JPA, Flyway |
| Frontend | Angular 22 (signals), CSS propio con design tokens, Vitest |
| Base de datos | PostgreSQL 16 |
| Calidad | JUnit, Mockito, Testcontainers, JaCoCo, SonarQube |
| CI/CD | GitHub Actions, GHCR, Trivy |
| Contenedores | Docker, Docker Compose, Kubernetes (kind) |
| Observabilidad | Micrometer, Prometheus, Grafana, Filebeat, Elasticsearch, Kibana |
| Secretos | HashiCorp Vault (Compose), Secret de Kubernetes (kind) |
| IaC | Terraform, Ansible |

## Arquitectura

```mermaid
flowchart LR
    U[Navegador] -->|HTTP| N[nginx del frontend]
    N -->|/api/*| B[Backend Spring Boot]
    B --> P[(PostgreSQL)]
    B -.->|secretos al arrancar| V[Vault]
    PR[Prometheus] -->|scrape /actuator/prometheus| B
    PR --> G[Grafana]
    B -->|logs JSON ECS| FB[Filebeat]
    FB --> ES[Elasticsearch] --> K[Kibana]
```

En Kubernetes un Ingress enruta `/api` al backend y el resto al frontend;
Vault, Prometheus, Grafana y ELK solo existen en Docker Compose.
Más detalle (módulos, flujo de un pedido, modelo de datos):
[docs/arquitectura.md](docs/arquitectura.md).

## Qué demuestra

- **Stock sin sobreventa**: descuento atómico con `UPDATE ... WHERE stock >= :qty`,
  verificado con un test de concurrencia (10 compradores, 1 unidad).
- **Transacciones**: un pedido con varias líneas es todo o nada.
- **Seguridad**: JWT, roles, aislamiento de pedidos entre usuarios (404 y no 403),
  CORS por entorno, CSRF desactivado con justificación documentada.
- **Pipeline**: tests, build, escaneo Trivy y publicación de imágenes con tag
  inmutable por commit.
- **Kubernetes**: probes de liveness/readiness, backend con 2 réplicas,
  rolling update y rollback ([runbook](docs/operacion.md#runbook-de-rollback)).
- **Arranque reproducible**: admin y datos de demo se crean por configuración,
  sin SQL manual.

## Primeros pasos (Docker Compose)

Requisitos: Docker Desktop. Los comandos son para PowerShell.

1. **Variables locales**

   ```powershell
   cd infra/docker
   Copy-Item .env.example .env     # edita los valores; .env está en .gitignore
   ```

2. **Base de datos y Vault**

   ```powershell
   docker compose up -d postgres vault
   ```

3. **Secretos en Vault** (modo dev: hay que repetirlo cada vez que Vault se
   reinicia). Sustituye los marcadores por los valores de tu `.env`:

   ```powershell
   docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=<VAULT_DEV_TOKEN> orderhub-vault `
     vault kv put secret/orderhub db.password=<DB_PASSWORD> jwt.secret=<JWT_SECRET>
   ```

4. **Backend y frontend**

   ```powershell
   docker compose up -d --build backend frontend
   ```

   Opcionalmente `docker compose up -d` levanta también Prometheus, Grafana,
   Elasticsearch, Kibana y Filebeat.

5. **Abrir la app**: http://localhost:4200

| Servicio | URL |
|---|---|
| App | http://localhost:4200 |
| API (directo) | http://localhost:8080 |
| Grafana | http://localhost:3000 (usuario `admin`, contraseña `GRAFANA_PASSWORD`) |
| Prometheus | http://localhost:9090 |
| Kibana | http://localhost:5601 |
| Vault | http://localhost:8200 |

### Admin inicial

El backend crea un usuario **ADMIN** al arrancar si `APP_ADMIN_EMAIL` y
`APP_ADMIN_PASSWORD` (mínimo 12 caracteres; si es más corta, el arranque
falla) están definidas. Es idempotente y no modifica a un usuario que ya
exista. Ver [ADR 0007](docs/adr/0007-seed-admin.md).

- **Compose**: defínelas en `infra/docker/.env` y reinicia el backend.
- **Vault** (perfil por defecto, si `APP_ADMIN_PASSWORD` no está definida).
  `patch` conserva las demás claves; `put` las reemplazaría todas:

  ```powershell
  docker exec -e VAULT_ADDR=http://127.0.0.1:8200 -e VAULT_TOKEN=<VAULT_DEV_TOKEN> orderhub-vault `
    vault kv patch secret/orderhub admin.password=<min-12-caracteres>
  ```

- **Kubernetes**: añádelas al Secret (ver [Kubernetes](#kubernetes-kind)).

Con ese usuario, el menú muestra **Administración** (crear, editar y eliminar
productos). Los usuarios que se registran desde la app son `CUSTOMER`.

### Datos de demostración

Desactivados por defecto. Con `APP_DEMO_DATA=true` el backend crea 12 productos
realistas (precios en COP, algunos con poco stock y uno agotado). Es
idempotente por nombre: reiniciar no duplica, pero un producto de demo que
borres se vuelve a crear. También se activa con el perfil `demo`
(`SPRING_PROFILES_ACTIVE=demo`).

- **Compose**: `APP_DEMO_DATA=true` en `infra/docker/.env`, luego
  `docker compose up -d backend`.
- **Kubernetes**: cambia el valor de `APP_DEMO_DATA` a `"true"` en
  `infra/k8s/20-backend.yaml` y ejecuta `kubectl apply -f infra/k8s/20-backend.yaml`.

## Variables de entorno

Todos los valores de ejemplo son ficticios.

| Variable | Dónde | Descripción | Ejemplo |
|---|---|---|---|
| `DB_PASSWORD` | `.env` / Secret K8s | Contraseña de PostgreSQL. En Compose también se carga en Vault como `db.password`. Debe coincidir con la del volumen ya inicializado. | `cambia_esto` |
| `JWT_SECRET` | `.env` (→ Vault) / Secret K8s | Clave HS256 para firmar JWT. Mínimo 32 caracteres. | `cambia_esto_minimo_32_caracteres` |
| `GRAFANA_PASSWORD` | `.env` | Contraseña del usuario `admin` de Grafana. | `cambia_esto` |
| `VAULT_DEV_TOKEN` | `.env` | Token raíz del Vault en modo dev; el backend lo recibe como `VAULT_TOKEN`. | `cambia_esto` |
| `APP_ADMIN_EMAIL` | `.env` / Secret K8s | Correo del admin inicial. Opcional. | `admin@example.com` |
| `APP_ADMIN_PASSWORD` | `.env` / Secret K8s | Contraseña del admin inicial (≥ 12 caracteres). Opcional; alternativa: `admin.password` en Vault. | `cambia_esto_min_12_caracteres` |
| `APP_DEMO_DATA` | `.env` / Deployment | `true` crea los productos de demo. Por defecto `false`. | `true` |
| `SPRING_DATASOURCE_URL` | backend | URL JDBC. Por defecto `jdbc:postgresql://localhost:5432/orderhub`. | `jdbc:postgresql://postgres:5432/orderhub` |
| `VAULT_URI` / `VAULT_TOKEN` | backend (Compose) | Dirección y token de Vault. | `http://vault:8200` |
| `SPRING_PROFILES_ACTIVE` | backend | `k8s` desactiva Vault y lee secretos de variables; `demo` activa los datos de demo. | `k8s` |
| `APP_CORS_ALLOWED_ORIGINS` | backend | Orígenes CORS permitidos (`app.cors.allowed-origins`). Por defecto `http://localhost:4200`; el perfil `k8s` usa `http://localhost:8081`. | `http://localhost:4200` |
| `APP_JWT_EXPIRATION_MINUTES` | backend | Vida del token (`app.jwt.expiration-minutes`). Por defecto 60. | `60` |
| `LOGGING_STRUCTURED_FORMAT_CONSOLE` | backend (Compose) | `ecs` emite logs JSON para Filebeat. | `ecs` |

La referencia de `.env` es [`infra/docker/.env.example`](infra/docker/.env.example).

## API: endpoints principales

Prefijo `/api`. Los errores usan `ProblemDetail` (RFC 9457).

| Método y ruta | Acceso | Descripción | Respuestas |
|---|---|---|---|
| `POST /auth/register` | Público | Crea un `CUSTOMER` (`email`, `password` 8–72). | 201; 400 validación; 409 correo existente |
| `POST /auth/login` | Público | Devuelve `{accessToken, tokenType, expiresInSeconds}`. | 200; 401 credenciales |
| `GET /products` | Público | Lista paginada (`page`, `size`, `sort`). | 200 |
| `GET /products/{id}` | Público | Un producto. | 200; 404 |
| `POST /products` | ADMIN | Crea un producto. | 201; 400; 403 |
| `PUT /products/{id}` | ADMIN | Actualiza un producto. | 200; 404 |
| `DELETE /products/{id}` | ADMIN | Elimina un producto. | 204; 404; 409 si tiene pedidos asociados |
| `POST /orders` | Autenticado | Crea un pedido `{items:[{productId, quantity}]}` y descuenta stock. | 201; 404 producto; 409 sin stock |
| `GET /orders` | Autenticado | Pedidos propios (un ADMIN ve todos), paginado. | 200 |
| `GET /orders/{id}` | Autenticado | Un pedido propio. | 200; 404 (también si es ajeno) |
| `POST /orders/{id}/pay` | Autenticado | `PENDING → PAID`. | 200; 409 estado inválido |
| `POST /orders/{id}/cancel` | Autenticado | `PENDING → CANCELLED` y devuelve stock. | 200; 409 estado inválido |

Fuera de `/api`: `GET /actuator/health/**` y `GET /actuator/prometheus`
(públicos, solo para entorno local).

## Tests

Backend (requiere Docker Desktop abierto: los `*IT` usan Testcontainers):

```powershell
cd backend
./mvnw test "-Dtest=*Test,*IT"
```

Frontend (Vitest):

```powershell
cd frontend
npx ng test --no-watch
npx ng build
```

CI ejecuta lo mismo en [`ci.yml`](.github/workflows/ci.yml).

## Kubernetes (kind)

```powershell
kind create cluster --name orderhub --config infra/k8s/kind-config.yaml
kubectl apply -f infra/k8s/00-namespace.yaml
kubectl create secret generic orderhub-secrets -n orderhub `
  --from-literal=DB_PASSWORD=<db> `
  --from-literal=JWT_SECRET=<jwt-min-32-caracteres> `
  --from-literal=APP_ADMIN_EMAIL=admin@example.com `
  --from-literal=APP_ADMIN_PASSWORD=<min-12-caracteres>
kubectl apply -f infra/k8s/
```

`APP_ADMIN_EMAIL` y `APP_ADMIN_PASSWORD` son opcionales (las referencias del
Deployment son `optional: true`). Para añadirlas a un Secret existente,
recréalo o aplícalas con `kubectl edit secret orderhub-secrets -n orderhub` y
reinicia el backend. El Ingress sirve la app en http://localhost:8081.

### Tags inmutables y rolling update

Cada commit en `main` publica `ghcr.io/alejo11102001/orderhub-{backend,frontend}:sha-<commit>`
(y `latest` solo por conveniencia). Los manifiestos **fijan** `sha-<commit>`
([ADR 0005](docs/adr/0005-tags-inmutables.md)), así que un despliegue o un
rollback siempre apunta a una imagen concreta.

Para desplegar: cambia el `image:` en `infra/k8s/20-backend.yaml` /
`30-frontend.yaml`, haz commit y `kubectl apply -f infra/k8s/`. El backend
(2 réplicas, con *readiness* y *liveness probes*) hace un *rolling update*:
Kubernetes no retira un pod viejo hasta que el nuevo responde
`/actuator/health/readiness`. Para volver atrás:
`kubectl -n orderhub rollout undo deployment/backend`. Pasos completos,
verificación y reconciliación con Git en [docs/operacion.md](docs/operacion.md).

## Documentación

- [Arquitectura](docs/arquitectura.md): módulos, flujo de un pedido, modelo de datos.
- [Operación](docs/operacion.md): métricas, logs, Vault, runbook de rollback.
- [Seguridad](docs/seguridad.md): modelo de amenazas, decisiones y límites.
- [ADRs](docs/adr): decisiones de diseño 0001–0008.

## Limitaciones conocidas

**Seguridad e infraestructura**

- Vault en modo dev con token raíz y datos en memoria (en producción: AppRole + almacenamiento persistente).
- Un Secret de Kubernetes solo está codificado en base64.
- Sin TLS (todo es HTTP) ni cabeceras de seguridad (CSP, HSTS) en nginx.
- Actuator/Prometheus, Elasticsearch y Kibana sin autenticación (solo entorno local).
- Sin rate limiting en el login ni bloqueo de cuentas; sin recuperación de contraseña.
- Sin refresco ni revocación de tokens; el frontend no cierra la sesión al expirar el token (las peticiones devuelven 401).
- `images.yml` publica imágenes en cada push a `main` sin esperar a que CI pase.
- SonarQube se ejecuta en local, no en CI.
- Kubernetes es excesivo para este tamaño; se usó para aprender. Prometheus, Grafana y ELK no están desplegados en kind.

**Producto**

- El pago es un cambio de estado: no hay pasarela de pago.
- Un pedido `PENDING` retiene su stock sin caducar.
- El carrito vive en memoria: se pierde al recargar la página.
- Un ADMIN ve todos los pedidos en «Mis pedidos».
- El stock mostrado en el carrito es el de cuando se cargó el catálogo; el 409 del servidor es la fuente de verdad.
- Cambiar `APP_ADMIN_PASSWORD` no actualiza a un admin que ya existe.
