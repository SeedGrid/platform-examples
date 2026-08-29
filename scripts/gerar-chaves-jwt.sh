#!/usr/bin/env bash
# Gera o par de chaves RSA que o seedgrid-security usa para assinar o JWT.
#
# As chaves NAO sao versionadas neste repositorio de proposito: chave privada em
# repo publico e' um convite a copiar pra producao. Rode este script uma vez por
# exemplo, antes do primeiro `mvn quarkus:dev`.
#
#   ./scripts/gerar-chaves-jwt.sh seedgrid-security-example
#   ./scripts/gerar-chaves-jwt.sh --todos
#
# Requer openssl no PATH (Git Bash no Windows ja traz).
set -euo pipefail

RAIZ="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

gerar() {
    local exemplo="$1"
    local dir="$RAIZ/examples/$exemplo/src/main/resources/keys"

    if [ ! -d "$RAIZ/examples/$exemplo" ]; then
        echo "  ! exemplo nao encontrado: $exemplo" >&2
        return 1
    fi

    mkdir -p "$dir"
    openssl genrsa -out "$dir/private.pem" 2048 2>/dev/null
    openssl rsa -in "$dir/private.pem" -pubout -out "$dir/public.pem" 2>/dev/null
    echo "  ok  $exemplo"
}

if ! command -v openssl >/dev/null 2>&1; then
    echo "openssl nao encontrado no PATH." >&2
    echo "Windows: use o Git Bash, que ja vem com openssl." >&2
    exit 1
fi

if [ $# -eq 0 ]; then
    echo "uso: $0 <nome-do-exemplo> | --todos"
    echo ""
    echo "exemplos disponiveis:"
    ls "$RAIZ/examples" | sed 's/^/  /'
    exit 1
fi

echo "Gerando chaves RSA 2048 para JWT:"
if [ "$1" = "--todos" ]; then
    for d in "$RAIZ"/examples/*/; do
        gerar "$(basename "$d")" || true
    done
else
    gerar "$1"
fi
echo ""
echo "Pronto. As chaves ficam em <exemplo>/src/main/resources/keys/ e sao"
echo "ignoradas pelo git."
