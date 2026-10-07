# Kubernetes (kind)

Manifiestos de OrderHub para un clúster local con [kind](https://kind.sigs.k8s.io/).
Todos los comandos son para **PowerShell**, ejecutados desde la raíz del repositorio.

| Archivo | Qué crea |
|---|---|
| `00-namespace.yaml` | Namespace `orderhub` |
| `10-postgres.yaml` | PostgreSQL 16 (StatefulSet + volumen de 1 Gi) |
| `20-backend.yaml` | Backend Spring Boot (2 réplicas, perfil `k8s`) |
| `30-frontend.yaml` | Frontend (nginx sin privilegios, puerto 8080) |
| `40-ingress.yaml` | Ingress: `/api` y `/actuator/health` → backend, `/` → frontend |
| `50-networkpolicy.yaml` | NetworkPolicy de ingreso (ver «Seguridad») |
| `kind/kind-config.yaml` | Configuración del clúster (**fuera** de este directorio a propósito) |

`kubectl apply -f infra/k8s/` aplica solo los manifiestos de este directorio; la
configuración de kind vive en `kind/` para que no se intente aplicar como recurso.

## 1. Crear el clúster

```powershell
kind create cluster --name orderhub --config infra/k8s/kind/kind-config.yaml
```

La configuración mapea el puerto 80 del nodo al `8081` de tu máquina y etiqueta
el nodo como `ingress-ready`. Instala el controlador Ingress (descarga un
manifiesto de la web oficial de kind):

```powershell
kubectl apply -f https://kind.sigs.k8s.io/examples/ingress/deploy-ingress-nginx.yaml
kubectl -n ingress-nginx wait --for=condition=Ready pod -l app.kubernetes.io/component=controller --timeout=120s
```

## 2. Crear el Secret

Los valores de abajo son marcadores: **no los escribas en archivos versionados**.

```powershell
kubectl apply -f infra/k8s/00-namespace.yaml
kubectl create secret generic orderhub-secrets -n orderhub `
  --from-literal=DB_PASSWORD=<db> `
  --from-literal=JWT_SECRET=<jwt-min-32-caracteres> `
  --from-literal=APP_ADMIN_EMAIL=admin@example.com `
  --from-literal=APP_ADMIN_PASSWORD=<min-12-caracteres>
```

- `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` son opcionales: sin ellas no se crea admin
  ([ADR 0007](../../docs/adr/0007-seed-admin.md)). La contraseña debe tener ≥ 12 caracteres
  o el backend no arranca.
- **`DB_PASSWORD` debe coincidir con la contraseña con la que se inicializó el volumen de
  Postgres.** Postgres solo usa `POSTGRES_PASSWORD` la primera vez que crea el directorio de
  datos; si luego cambias el Secret, el backend fallará al autenticarse. Para empezar de cero
  borra el volumen: `kubectl -n orderhub delete pvc data-postgres-0` (con el StatefulSet detenido).
- Para cambiar un valor: `kubectl -n orderhub delete secret orderhub-secrets`, recrearlo y
  `kubectl -n orderhub rollout restart deployment/backend`.

## 3. Aplicar

```powershell
kubectl apply -f infra/k8s/
kubectl -n orderhub get pods -w
```

App en http://localhost:8081. Salud: http://localhost:8081/actuator/health/readiness.

> **Cambios de la hardening**: el frontend ahora escucha en 8080 y corre como usuario sin
> privilegios. Una imagen **anterior** (`sha-910191d` o más vieja) escucha en el puerto 80, por
> lo que **no funcionará** con estos manifiestos: actualiza los tags a un commit posterior
> (ver siguiente sección) antes de aplicar.

## 4. Desplegar una versión nueva (tags inmutables)

Cada commit de `main` publica `orderhub-{backend,frontend}:sha-<commit>`
([ADR 0005](../../docs/adr/0005-tags-inmutables.md)). Espera a que **CI e Images** estén en verde
para ese commit (`images.yml` no depende de `ci.yml`) y luego:

```powershell
./scripts/update-image-tags.ps1 -Sha <commit>      # sin -Sha usa el HEAD actual
git diff infra/k8s
git commit -am "feat(k8s): pin images to sha-<commit>"
kubectl apply -f infra/k8s/
kubectl -n orderhub rollout status deployment/backend
kubectl -n orderhub rollout status deployment/frontend
```

Con `maxUnavailable` 0 (valor por defecto con 2 réplicas) y *readiness probe*, Kubernetes no
retira un pod viejo hasta que el nuevo responde en `/actuator/health/readiness`.

## 5. Rollback

```powershell
kubectl -n orderhub rollout history deployment/backend
kubectl -n orderhub rollout undo deployment/backend          # o --to-revision=<N>
kubectl -n orderhub rollout status deployment/backend
git revert <commit-que-subio-el-tag>                         # reconcilia Git con el clúster
```

Detalle y diagnóstico en [docs/operacion.md](../../docs/operacion.md#runbook-de-rollback).
Flyway no revierte migraciones: el rollback de imagen no deshace cambios de esquema.

## 6. Datos de demostración

Edita el valor de `APP_DEMO_DATA` en `20-backend.yaml` a `"true"` y aplica:

```powershell
kubectl apply -f infra/k8s/20-backend.yaml
kubectl -n orderhub rollout status deployment/backend
```

Es idempotente (no duplica productos). Vuelve a `"false"` cuando no lo necesites.

## Seguridad de los manifiestos

- **Backend y frontend**: `runAsNonRoot`, `readOnlyRootFilesystem` (con `emptyDir` en `/tmp`),
  `allowPrivilegeEscalation: false`, `capabilities: drop ALL`, perfil `seccomp` por defecto y sin
  token de ServiceAccount montado.
- **Postgres**: su entrypoint arranca como root y baja al usuario `postgres`, así que no se
  fuerza `runAsNonRoot`; se eliminan todas las capabilities salvo
  `CHOWN, DAC_OVERRIDE, FOWNER, SETGID, SETUID` y el sistema de archivos es de solo lectura
  (con `emptyDir` para `/tmp` y `/var/run/postgresql`).
- **NetworkPolicy** (`50-networkpolicy.yaml`): se deniega todo el tráfico entrante y se permite
  ingress-nginx → frontend, ingress-nginx y frontend → backend, y backend → postgres. Solo se
  restringe el tráfico **entrante**. kindnet las aplica (se comprobó con un pod permitido y otro
  denegado); en otro clúster con un CNI sin soporte (p. ej. kindnet antiguo) serían decorativas.
- `/actuator/prometheus` no se enruta por el Ingress y en el perfil `k8s` ni siquiera está
  expuesto (solo `health`).

## Problemas conocidos

- **Descarga lenta de imágenes**: el primer `apply` puede tardar minutos si el nodo descarga de
  GHCR; `imagePullPolicy: IfNotPresent` evita repetirlo. Si el paquete de GHCR es privado, el
  nodo no podrá descargarlo (carga la imagen con `kind load`).
- **`kind load docker-image` en Docker Desktop**: con el *image store* de containerd habilitado
  puede fallar con errores del tipo `content digest ... not found`. Alternativa:

  ```powershell
  docker save -o orderhub-backend.tar ghcr.io/alejo11102001/orderhub-backend:sha-<commit>
  kind load image-archive orderhub-backend.tar --name orderhub
  ```

- **El backend tarda alrededor de un minuto en estar Ready** (arranque de Spring + Flyway; en la verificación con
  estos manifiestos todos los pods estaban Ready a los ~60 s); la *liveness probe* espera 40 s antes de empezar.
- El límite de intentos de login es **por réplica** ([ADR 0009](../../docs/adr/0009-rate-limiting-login.md)).
