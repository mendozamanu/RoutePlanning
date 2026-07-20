#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/../.." && pwd)
env_file="$script_dir/.env"
compose_file="$script_dir/docker-compose.yml"
graph_file="$repository_root/infra/otp/data/graph.obj"

if [ ! -f "$env_file" ]; then
    echo "Missing $env_file. Copy .env.example to .env and configure it." >&2
    exit 1
fi

if [ ! -s "$graph_file" ]; then
    echo "Missing OTP graph: $graph_file" >&2
    echo "Build it locally and upload it before deploying." >&2
    exit 1
fi

docker compose --env-file "$env_file" -f "$compose_file" up -d --build
docker compose --env-file "$env_file" -f "$compose_file" ps
