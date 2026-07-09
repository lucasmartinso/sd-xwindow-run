#!/usr/bin/env bash
# Build and (optionally) push the sd-runner image.
# Usage: tools/build-image.sh [tag] [--push]
set -euo pipefail

REPO="${SD_IMAGE_REPO:-ghcr.io/tabiahealth/sd-runner}"
TAG="${1:-latest}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "Building ${REPO}:${TAG}"
docker build -f "${ROOT}/docker/Dockerfile" -t "${REPO}:${TAG}" "${ROOT}"

if [[ "${2:-}" == "--push" ]]; then
  echo "Pushing ${REPO}:${TAG}"
  docker push "${REPO}:${TAG}"
fi
echo "Done."
