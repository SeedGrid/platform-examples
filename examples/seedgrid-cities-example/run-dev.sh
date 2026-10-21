#!/usr/bin/env bash
# Sobe o exemplo em modo dev carregando o dev.env.
set -euo pipefail
cd "$(dirname "$0")"
set -a
source dev.env
set +a
exec mvn quarkus:dev
