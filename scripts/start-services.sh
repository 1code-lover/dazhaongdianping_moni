#!/usr/bin/env bash
set -euo pipefail

services=(mysql redis kafka)

for service in "${services[@]}"; do
  echo "Starting ${service}..."
  brew services start "${service}"
done

echo
echo "Service status:"
brew services list | grep -E '^(mysql|redis|kafka)\b'

echo
echo "Done. MySQL, Redis, and Kafka are starting or already running."
