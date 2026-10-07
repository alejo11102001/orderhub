# 5. Imágenes con tag inmutable por commit

Estado: Aceptado

## Contexto
Con `:latest`, el mismo manifiesto arrancó dos pods con imágenes distintas
(distinto digest) porque se publicó una nueva entre ambos arranques.
Además, un rollback no devolvía necesariamente la versión anterior.

## Decisión
El pipeline publica cada imagen como `sha-<commit>` (y `latest` solo como
conveniencia). Los manifiestos de Kubernetes fijan `sha-<commit>` con
`imagePullPolicy: IfNotPresent`. Trivy escanea antes de publicar y bloquea
vulnerabilidades CRITICAL con parche disponible.

## Consecuencias
+ Despliegues reproducibles y rollbacks exactos.
+ Trazabilidad de qué commit corre en cada entorno.
- Cada despliegue exige actualizar el tag en el manifiesto (automatizable
  con GitOps o Kustomize).
- El workflow `images.yml` se dispara en cada push a `main` y no espera a que
  `ci.yml` termine: una imagen puede publicarse aunque los tests fallen
  (Trivy sí bloquea la publicación). Lo seguro es desplegar solo tags de
  commits con CI en verde.