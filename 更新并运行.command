#!/bin/bash
set -e
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
bash "$PROJECT_DIR/tools/run.sh" --build
