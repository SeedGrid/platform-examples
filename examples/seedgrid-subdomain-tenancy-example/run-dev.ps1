# Carrega o dev.env e sobe a aplicacao em modo dev.
#
#   .\run-dev.ps1
#
# Antes de rodar pela primeira vez, veja o README: e' preciso subir a
# infraestrutura, gerar as chaves JWT e criar o banco master.
$ErrorActionPreference = 'Stop'

$envFile = Join-Path $PSScriptRoot 'dev.env'
if (-not (Test-Path $envFile)) {
    Write-Host "dev.env nao encontrado em $PSScriptRoot"
    exit 1
}

Write-Host "==> Carregando variaveis de $envFile"
Get-Content $envFile | ForEach-Object {
    $linha = $_.Trim()
    if ($linha -eq '' -or $linha.StartsWith('#')) { return }

    $i = $linha.IndexOf('=')
    if ($i -lt 1) { return }

    $nome = $linha.Substring(0, $i).Trim()
    $valor = $linha.Substring($i + 1).Trim()

    # tira aspas em volta do valor, quando houver
    if ($valor.Length -ge 2) {
        if (($valor.StartsWith('"') -and $valor.EndsWith('"')) -or
            ($valor.StartsWith("'") -and $valor.EndsWith("'"))) {
            $valor = $valor.Substring(1, $valor.Length - 2)
        }
    }

    Set-Item -Path "Env:$nome" -Value $valor
}

Write-Host "==> Subindo o Quarkus em modo dev (porta $env:HTTP_PORT)"
Set-Location $PSScriptRoot
mvn quarkus:dev
