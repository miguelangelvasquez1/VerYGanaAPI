#Requires -Version 5.1
<#
.SYNOPSIS
    Regenera el archivo .env local a partir de los secretos guardados en Infisical.

.DESCRIPTION
    El .env NO se edita a mano ni se commitea: es una copia local y desechable de
    lo que vive en Infisical. Este script lo reconstruye.

    Resuelve dos problemas de hacer `infisical export --format=dotenv > .env` directo:

      1. Codificacion. El operador `>` de PowerShell 5.1 escribe UTF-16; el loader de
         Spring (spring.config.import: file:.env[.properties]) no lo puede parsear y
         ninguna variable resuelve. Aca se escribe UTF-8 SIN BOM, saltos de linea \n.

      2. Comillas. Infisical envuelve cada valor en comillas ('valor'). El .env se
         carga como archivo .properties y properties NO interpreta comillas, asi que
         quedarian literales (IVA valdria "'0.19'" y revienta al parsear numero).
         Aca se quitan.

.PARAMETER Environment
    Slug del entorno en Infisical (dev | staging | prod). Si se omite, usa el
    defaultEnvironment de .infisical.json.

.PARAMETER OutFile
    Ruta del archivo a escribir. Por defecto .\.env en el directorio actual.

.EXAMPLE
    infisical login              # una sola vez
    .\scripts\sync-env.ps1       # regenera .\.env con el entorno por defecto

.EXAMPLE
    .\scripts\sync-env.ps1 -Environment prod
#>
[CmdletBinding()]
param(
    [string]$Environment,
    [string]$OutFile = (Join-Path (Get-Location) '.env')
)

$ErrorActionPreference = 'Stop'

if (-not (Get-Command infisical -ErrorAction SilentlyContinue)) {
    throw "No se encontro el CLI de Infisical. Instalacion: https://infisical.com/docs/cli/overview"
}

$exportArgs = @('export', '--format=dotenv')
if ($Environment) { $exportArgs += "--env=$Environment" }

$raw = & infisical @exportArgs
if ($LASTEXITCODE -ne 0) {
    throw "infisical export fallo (exit $LASTEXITCODE). Verifica: 'infisical login' hecho y acceso al proyecto."
}

# KEY='valor'  ->  KEY=valor     (tambien comillas dobles)
$lines = $raw |
    Where-Object { $_.Trim() -ne '' } |
    ForEach-Object { $_ -replace "^([A-Za-z_]\w*)=(['""])(.*)\2\s*$", '$1=$3' }

$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($OutFile, (($lines -join "`n").TrimEnd() + "`n"), $utf8NoBom)

$count = ($lines | Where-Object { $_ -match '^[A-Za-z_]\w*=' }).Count
Write-Host "OK  ->  $OutFile  ($count variables)" -ForegroundColor Green
