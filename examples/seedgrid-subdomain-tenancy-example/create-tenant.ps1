# Cria um tenant novo: banco proprio + migrations + registro no master.
#
# Usa o container do infra/compose.yml deste repositorio. Se voce roda Docker
# em vez de Podman, troque "podman exec" por "docker exec" abaixo.
# Uso: .\create-tenant.ps1 -TenantName "novotenante"

param(
    [Parameter(Mandatory=$true)]
    [string]$TenantName
)

$dbName = "tenant_$TenantName"

Write-Host "🚀 Criando tenant: $TenantName" -ForegroundColor Cyan
Write-Host "📦 Banco de dados: $dbName" -ForegroundColor Cyan

# 1. Criar o banco de dados
Write-Host "`n1️⃣ Criando banco de dados..." -ForegroundColor Yellow
podman exec seedgrid-examples-postgres psql -U postgres -c "CREATE DATABASE $dbName;"

if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✅ Banco criado com sucesso!" -ForegroundColor Green
} else {
    Write-Host "   ❌ Erro ao criar banco!" -ForegroundColor Red
    exit 1
}

# 2. Executar migrations
Write-Host "`n2️⃣ Executando migrations..." -ForegroundColor Yellow
Get-Content src/main/resources/db/migration/tenant/V1__create_persons_table.sql | podman exec -i seedgrid-examples-postgres psql -U postgres -d $dbName

if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✅ Migrations executadas com sucesso!" -ForegroundColor Green
} else {
    Write-Host "   ❌ Erro ao executar migrations!" -ForegroundColor Red
    exit 1
}

# 3. Verificar tabelas criadas
Write-Host "`n3️⃣ Verificando tabelas criadas..." -ForegroundColor Yellow
podman exec seedgrid-examples-postgres psql -U postgres -d $dbName -c "\dt"

# 4. Inserir registro na tenant_config
Write-Host "`n4️⃣ Registrando tenant no master..." -ForegroundColor Yellow
$sql = "INSERT INTO tenant_config (tenant_id, db_name, status, created_at, updated_at, activated_at) VALUES ('$TenantName', '$dbName', 'ACTIVE', NOW(), NOW(), NOW());"
podman exec seedgrid-examples-postgres psql -U postgres -d crm_master -c $sql

if ($LASTEXITCODE -eq 0) {
    Write-Host "   ✅ Tenant registrado com sucesso!" -ForegroundColor Green
} else {
    Write-Host "   ⚠️  Aviso: Pode já existir um registro para este tenant" -ForegroundColor Yellow
}

Write-Host "`n✅ Tenant '$TenantName' criado com sucesso!" -ForegroundColor Green
Write-Host "`n📝 Para testar, use:" -ForegroundColor Cyan
Write-Host "   curl -H `"Host: $TenantName.seedgrid.com.br`" http://localhost:8080/api/persons" -ForegroundColor White

