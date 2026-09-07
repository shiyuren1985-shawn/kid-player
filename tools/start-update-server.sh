#!/bin/bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
source "$PROJECT_DIR/tools/env.sh"
exec /usr/bin/python3 "$PROJECT_DIR/tools/update-server.py" --root "$(dirname "$PROJECT_DIR")/update-server/public"
