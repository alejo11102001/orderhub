<#
.SYNOPSIS
  Actualiza el tag inmutable sha-<commit> de las imágenes en los manifiestos de Kubernetes.

.DESCRIPTION
  Reemplaza el tag de orderhub-backend en infra/k8s/20-backend.yaml y el de
  orderhub-frontend en infra/k8s/30-frontend.yaml. No hace commit ni aplica nada.

.PARAMETER Sha
  Hash del commit (7 a 40 caracteres hexadecimales). Acepta también el formato "sha-<hash>".
  Si se omite, usa el commit actual (git rev-parse --short HEAD).

.PARAMETER Only
  Actualiza solo "backend" o "frontend" (por defecto, ambos).

.EXAMPLE
  ./scripts/update-image-tags.ps1 -Sha 74157cb

.EXAMPLE
  ./scripts/update-image-tags.ps1 -Only frontend
#>
[CmdletBinding()]
param(
    [string]$Sha,
    [ValidateSet('backend', 'frontend', 'all')]
    [string]$Only = 'all'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot

if (-not $Sha) {
    $Sha = (git -C $root rev-parse --short HEAD).Trim()
}
$Sha = $Sha -replace '^sha-', ''
if ($Sha -notmatch '^[0-9a-f]{7,40}$') {
    throw "Sha invalido: '$Sha'. Se esperan de 7 a 40 caracteres hexadecimales en minuscula."
}
$tag = "sha-$Sha"

$targets = @(
    @{ Name = 'backend';  File = Join-Path $root 'infra/k8s/20-backend.yaml';  Image = 'orderhub-backend' },
    @{ Name = 'frontend'; File = Join-Path $root 'infra/k8s/30-frontend.yaml'; Image = 'orderhub-frontend' }
)

foreach ($t in $targets) {
    if ($Only -ne 'all' -and $Only -ne $t.Name) { continue }
    if (-not (Test-Path $t.File)) { throw "No existe $($t.File)" }

    $text = [System.IO.File]::ReadAllText($t.File)
    $pattern = "(?m)(image:\s*\S*$($t.Image)):sha-[0-9a-f]+"
    if ($text -notmatch $pattern) { throw "No se encontro la imagen $($t.Image) con tag sha-* en $($t.File)" }

    $old = [regex]::Match($text, "$($t.Image):(sha-[0-9a-f]+)").Groups[1].Value
    $new = [regex]::Replace($text, $pattern, "`${1}:$tag")
    [System.IO.File]::WriteAllText($t.File, $new)   # conserva el fin de línea del archivo
    Write-Host ("{0,-9} {1} -> {2}" -f $t.Name, $old, $tag)
}

Write-Host "`nSiguiente paso: git diff infra/k8s; git commit; kubectl apply -f infra/k8s/"
