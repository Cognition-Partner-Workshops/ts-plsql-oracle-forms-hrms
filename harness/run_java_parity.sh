#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
container="hrms-java-parity-$$"
port=55432
docker run --name "$container" --rm -d \
  -p "127.0.0.1:$port:5432" \
  -e POSTGRES_DB=hrms -e POSTGRES_USER=hrms -e POSTGRES_PASSWORD=fixture_only \
  postgres:16.8-alpine
trap 'docker rm -fv "$container" >/dev/null 2>&1 || true' EXIT
ready=false
for ((attempt=0; attempt<60; attempt++)); do
  if docker exec "$container" pg_isready -U hrms -d hrms >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 1
done
if [ "$ready" != true ]; then
  docker logs "$container" >&2
  echo 'PostgreSQL did not become ready' >&2
  exit 1
fi
(cd java-service && ./mvnw -B -DskipTests package)
java -jar java-service/target/java-service-0.0.1-SNAPSHOT.jar \
  --spring.profiles.active=parity \
  "--spring.datasource.url=jdbc:postgresql://127.0.0.1:$port/hrms" \
  --spring.datasource.username=hrms --spring.datasource.password=fixture_only \
  "--parity.golden-dir=$PWD/harness/golden" "--parity.work-dir=$PWD/harness/work/java"
