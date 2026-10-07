# OrderHub

Plataforma de pedidos (mini e-commerce) construida como proyecto de aprendizaje
de extremo a extremo: aplicación, calidad, contenedores, CI/CD, observabilidad,
gestión de secretos, infraestructura como código y Kubernetes.

![Catálogo](docs/img/app-catalogo.png)

## Stack

| Capa | Tecnología |
|---|---|
| Backend | Java 21, Spring Boot 4, Spring Security (JWT), JPA, Flyway |
| Frontend | Angular 22 (signals), Vitest |
| Base de datos | PostgreSQL 16 |
| Calidad | JUnit, Mockito, Testcontainers, JaCoCo, SonarQube |
| CI/CD | GitHub Actions, GHCR, Trivy |
| Contenedores | Docker, Docker Compose, Kubernetes (kind) |
| Observabilidad | Micrometer, Prometheus, Grafana, Filebeat, Elasticsearch, Kibana |
| Secretos | HashiCorp Vault |
| IaC | Terraform, Ansible |

## Arquitectura

## Arquitectura

```mermaid
flowchart LR
    U[Navegador] --> I[Ingress]
    I -->|/| F[Frontend nginx]
    I -->|/api| B[Backend Spring Boot]
    B --> P[(PostgreSQL)]
    B -.secretos.-> V[Vault]
    B -->|/actuator/prometheus| PR[Prometheus] --> G[Grafana]
    B -->|logs JSON ECS| FB[Filebeat] --> ES[Elasticsearch] --> K[Kibana]
```

## Qué demuestra

- **Stock sin sobreventa**: descuento atómico con `UPDATE ... WHERE stock >= :qty`,
  verificado con un test de concurrencia (10 hilos, 1 unidad).
- **Transacciones**: un pedido con varias líneas es todo o nada.
- **Seguridad**: JWT, roles, aislamiento de pedidos entre usuarios (404 y no 403),
  CORS por entorno, CSRF desactivado con justificación documentada.
- **Pipeline**: tests, build, escaneo Trivy y publicación de imágenes con tag
  inmutable por commit.
- **Kubernetes**: probes de liveness/readiness, rolling update, rollback y
  resiliencia verificada al borrar pods.

## Ejecución

### Docker Compose

    cd infra/docker
    cp .env.example .env      # completa los valores locales
    docker compose up -d --build

- App: http://localhost:4200
- Grafana: http://localhost:3000
- Kibana: http://localhost:5601

Vault está en modo dev: tras reiniciarlo hay que volver a cargar los secretos.

### Kubernetes (kind)

    kind create cluster --name orderhub --config infra/k8s/kind-config.yaml
    kubectl apply -f infra/k8s/00-namespace.yaml
    kubectl create secret generic orderhub-secrets -n orderhub \
      --from-literal=DB_PASSWORD=... --from-literal=JWT_SECRET=...
    kubectl apply -f infra/k8s/

App en http://localhost:8081.

### Admin inicial

El backend crea un usuario ADMIN al arrancar si `APP_ADMIN_EMAIL` y
`APP_ADMIN_PASSWORD` (mínimo 12 caracteres) están definidas. Es idempotente y
no toca un usuario existente. Ver [ADR 0007](docs/adr/0007-seed-admin.md).

- **Compose**: define ambas en `infra/docker/.env` (ver `.env.example`).
- **Kubernetes**: añádelas al Secret (ejemplo con valores ficticios):

      kubectl create secret generic orderhub-secrets -n orderhub `
        --from-literal=DB_PASSWORD=<db> --from-literal=JWT_SECRET=<jwt> `
        --from-literal=APP_ADMIN_EMAIL=admin@example.com `
        --from-literal=APP_ADMIN_PASSWORD=<min-12-caracteres>

- **Vault** (perfil por defecto, si `APP_ADMIN_PASSWORD` no está definida).
  `patch` conserva las demás claves; `put` las reemplazaría todas:

      docker exec -e VAULT_TOKEN=<token-dev> orderhub-vault vault kv patch secret/orderhub admin.password=<min-12-caracteres>

### Tests

    cd backend && ./mvnw verify
    cd frontend && npm test

## Decisiones de diseño

Ver [docs/adr](docs/adr).

## Limitaciones conocidas

- Vault en modo dev con token raíz (en producción: AppRole + almacenamiento persistente).
- Actuator/Prometheus y Elasticsearch sin autenticación (solo entorno local).
- SonarQube se ejecuta en local, no en CI.
- Kubernetes es excesivo para este tamaño; se usó para aprender.
- Sin rate limiting en el login.