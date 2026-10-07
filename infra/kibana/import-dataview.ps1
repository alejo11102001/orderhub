<#
.SYNOPSIS
  Importa en Kibana el Data View "orderhub-logs" y la búsqueda guardada "errores del backend".

.DESCRIPTION
  Kibana no carga objetos guardados desde archivos al arrancar, así que se importan por su API.
  Es idempotente (overwrite=true). Requiere Kibana arriba (docker compose up -d kibana filebeat)
  y curl.exe (incluido en Windows 10/11).

.PARAMETER KibanaUrl
  URL de Kibana. Por defecto http://localhost:5601.
#>
[CmdletBinding()]
param([string]$KibanaUrl = 'http://localhost:5601')

$ErrorActionPreference = 'Stop'
$file = Join-Path $PSScriptRoot 'orderhub-logs.ndjson'

Write-Host "Esperando a Kibana en $KibanaUrl ..."
$ready = $false
for ($i = 0; $i -lt 60 -and -not $ready; $i++) {
    try {
        $status = Invoke-RestMethod -Uri "$KibanaUrl/api/status" -TimeoutSec 5
        $ready = $status.status.overall.level -eq 'available'
    } catch { }
    if (-not $ready) { Start-Sleep -Seconds 5 }
}
if (-not $ready) { throw "Kibana no esta disponible en $KibanaUrl" }

$out = curl.exe -s -X POST "$KibanaUrl/api/saved_objects/_import?overwrite=true" `
    -H "kbn-xsrf: true" --form "file=@$file"
$result = $out | ConvertFrom-Json
if (-not $result.success) { throw "La importacion fallo: $out" }
Write-Host "Importados $($result.successCount) objetos. Discover: $KibanaUrl/app/discover#/view/orderhub-errors"
