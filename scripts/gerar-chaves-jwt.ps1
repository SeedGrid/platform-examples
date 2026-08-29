# Gera o par de chaves RSA que o seedgrid-security usa para assinar o JWT.
#
# As chaves NAO sao versionadas neste repositorio de proposito: chave privada em
# repo publico e' um convite a copiar pra producao. Rode este script uma vez por
# exemplo, antes do primeiro `mvn quarkus:dev`.
#
#   .\scripts\gerar-chaves-jwt.ps1 seedgrid-security-example
#   .\scripts\gerar-chaves-jwt.ps1 -Todos
#
# Requer openssl no PATH (vem com o Git para Windows).
param(
    [Parameter(Position = 0)][string]$Exemplo,
    [switch]$Todos
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot

if (-not (Get-Command openssl -ErrorAction SilentlyContinue)) {
    Write-Host "openssl nao encontrado no PATH."
    Write-Host "Ele vem com o Git para Windows, normalmente em:"
    Write-Host '  C:\Program Files\Git\usr\bin\openssl.exe'
    exit 1
}

function New-ParDeChaves([string]$nome) {
    $projeto = Join-Path $raiz "examples\$nome"
    if (-not (Test-Path $projeto)) {
        Write-Host "  ! exemplo nao encontrado: $nome"
        return
    }
    $dir = Join-Path $projeto 'src\main\resources\keys'
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Path $dir -Force | Out-Null }

    & openssl genrsa -out "$dir\private.pem" 2048 2>$null
    & openssl rsa -in "$dir\private.pem" -pubout -out "$dir\public.pem" 2>$null
    Write-Host "  ok  $nome"
}

if (-not $Todos -and -not $Exemplo) {
    Write-Host "uso: .\scripts\gerar-chaves-jwt.ps1 <nome-do-exemplo> | -Todos"
    Write-Host ""
    Write-Host "exemplos disponiveis:"
    Get-ChildItem (Join-Path $raiz 'examples') -Directory | ForEach-Object { Write-Host "  $($_.Name)" }
    exit 1
}

Write-Host "Gerando chaves RSA 2048 para JWT:"
if ($Todos) {
    Get-ChildItem (Join-Path $raiz 'examples') -Directory | ForEach-Object { New-ParDeChaves $_.Name }
} else {
    New-ParDeChaves $Exemplo
}
Write-Host ""
Write-Host "Pronto. As chaves ficam em <exemplo>\src\main\resources\keys\ e sao"
Write-Host "ignoradas pelo git."
