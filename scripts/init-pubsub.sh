#!/usr/bin/env bash
# Create Pub/Sub topic and subscription for PDF estimate processing.
# Run after: docker compose up -d pubsub
# Requires: gcloud CLI installed locally

set -e
export PUBSUB_EMULATOR_HOST=localhost:8085

echo "Creating topic estimate-events..."
gcloud pubsub topics create estimate-events --project=aether 2>/dev/null || true

echo "Creating subscription estimate-events-sub..."
gcloud pubsub subscriptions create estimate-events-sub \
  --topic=estimate-events \
  --project=aether \
  2>/dev/null || true

echo "Done. Topic and subscription are ready."
