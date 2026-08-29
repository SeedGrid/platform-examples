#!/bin/bash
set -euo pipefail

# Script para executar o Quarkus em modo dev carregando variáveis do dev.env
# Uso: ./run-dev.sh

ENV_FILE="dev.env"

if [ ! -f "$ENV_FILE" ]; then
    echo "Erro: Arquivo $ENV_FILE não encontrado!"
    exit 1
fi

echo "==> Carregando variáveis de ambiente do $ENV_FILE"

set -a
source "$ENV_FILE"
set +a

echo "==> Iniciando Quarkus em modo dev..."
mvn -U clean quarkus:dev -e
