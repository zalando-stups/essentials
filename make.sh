#!/usr/bin/env bash
set -euo pipefail
IFS=$'\t\n'

DB_IMAGE=postgres:9.5
DB_CONTAINER=essentials-db
DB_PORT=5432

cmd_db() {
    docker rm -fv $DB_CONTAINER ||  true
    docker run -dt --name $DB_CONTAINER \
        -p $DB_PORT:5432 \
        $DB_IMAGE
}

# Shortcuts

# Run multiple commands without args
cmd_mm() {
    for cmd in "$@"; do
        cmd_$cmd
    done
}
if [[ $# -eq 0 ]]; then
    echo Please provide a subcommand
    exit 1
fi

SUBCOMMAND=$1
shift

# Enable verbose mode
set -x
# Run the subcommand
cmd_${SUBCOMMAND} $@
