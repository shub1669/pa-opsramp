#!/bin/bash

# ==============================================================================
# Build and Push Docker Image to GCP Artifact Registry
# ==============================================================================

set -e

# Configuration - GCP Artifact Registry
GCP_REGION="${GCP_REGION:-us-central1}"
GCP_PROJECT="${GCP_PROJECT:-opsramp-registry}"
GCP_REPO="${GCP_REPO:-pa-charts}"
IMAGE_NAME="${IMAGE_NAME:-pa-spark}"

# Use git commit hash as tag if not provided
GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
IMAGE_TAG="${IMAGE_TAG:-${GIT_COMMIT}}"

# Full GCP Artifact Registry path
GCP_REGISTRY="${GCP_REGION}-docker.pkg.dev"
FULL_IMAGE_PATH="${GCP_REGISTRY}/${GCP_PROJECT}/${GCP_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"

# Service account key file path (optional, for CI/CD)
SA_KEY_FILE="${SA_KEY_FILE:-sa-key.json}"

echo "=============================================="
echo "Building Spark Delta Lake Application"
echo "=============================================="

# Build the Scala application (all modules)
echo "Step 1: Building all Scala modules with sbt..."
sbt clean assembly

# Verify the job jar exists
if [ ! -f "jobs/s3-to-delta/target/scala-2.12/s3-to-delta-assembly.jar" ]; then
    echo "ERROR: s3-to-delta-assembly.jar not found!"
    exit 1
fi

echo "Step 2: Building Docker image..."
docker build -t ${IMAGE_NAME}:${IMAGE_TAG} .

echo "Step 3: Tagging image for GCP Artifact Registry..."
docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${FULL_IMAGE_PATH}

echo "Step 4: Authenticating with GCP Artifact Registry..."
# Check if service account key file exists for CI/CD authentication
if [ -f "${SA_KEY_FILE}" ]; then
    echo "Using service account key file for authentication..."
    gcloud auth activate-service-account --key-file="${SA_KEY_FILE}"
    gcloud auth configure-docker ${GCP_REGISTRY} --quiet
else
    echo "No service account key file found. Using existing gcloud credentials..."
    echo "Make sure you have run: gcloud auth configure-docker ${GCP_REGISTRY}"
    gcloud auth configure-docker ${GCP_REGISTRY} --quiet
fi

echo "Step 5: Pushing image to GCP Artifact Registry..."
docker push ${FULL_IMAGE_PATH}

echo "=============================================="
echo "SUCCESS: Image pushed to ${FULL_IMAGE_PATH}"
echo "=============================================="
echo ""
echo "To pull this image, run:"
echo "  docker pull ${FULL_IMAGE_PATH}"
echo "=============================================="
