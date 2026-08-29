#!/bin/bash
set -euo pipefail

# Script para executar o Quarkus em modo dev carregando variáveis do .env
# Uso: ./run-dev.sh

ENV_FILE="dev.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "Erro: Arquivo $ENV_FILE não encontrado!"
    exit 1
fi

echo "==> Carregando variáveis de ambiente do $ENV_FILE"

# Exporta todas as variáveis do .env
set -a
source "$ENV_FILE"
set +a

# Evita que o Git Bash/MSYS converta variáveis *PATH em caminhos Windows
export MSYS2_ENV_CONV_EXCL="SECURITY_FRONTEND_LOGIN_PATH;SECURITY_FRONTEND_PASSWORD_RESET_PATH;SECURITY_FRONTEND_EMAIL_CONFIRMATION_PATH;SECURITY_FRONTEND_WELCOME_PATH;SECURITY_FRONTEND_SELF_ENTER_PATH;SEEDGRID_SECURITY_FRONTEND_LOGIN_PATH;SEEDGRID_SECURITY_FRONTEND_PASSWORD_RESET_PATH;SEEDGRID_SECURITY_FRONTEND_EMAIL_CONFIRMATION_PATH;SEEDGRID_SECURITY_FRONTEND_WELCOME_PATH;SEEDGRID_SECURITY_FRONTEND_SELF_ENTER_PATH"

echo "==> Iniciando Quarkus em modo dev..."
mvn -U clean quarkus:dev -e
