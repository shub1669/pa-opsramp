#!/bin/bash

# ==============================================================================
# Build and Push Docker Image to GCP Artifact Registry or GLCP Container Registry
# ==============================================================================

set -e

# Registry Type: 'gcp' or 'glcp'
REGISTRY_TYPE="${REGISTRY_TYPE:-gcp}"

# Configuration - GCP Artifact Registry
GCP_REGION="${GCP_REGION:-us-central1}"
GCP_PROJECT="${GCP_PROJECT:-opsramp-registry}"
GCP_REPO="${GCP_REPO:-pa-charts}"

# Configuration - GLCP (GreenLake Cloud Platform) Container Registry
GLCP_REGION="${GLCP_REGION:-us1}"
GLCP_ORG="${GLCP_ORG:-}"  # Your GLCP organization ID
GLCP_SPACE="${GLCP_SPACE:-default}"  # GLCP space name

# Common configuration
IMAGE_NAME="${IMAGE_NAME:-pa-spark}"

# Use git commit hash as tag if not provided
GIT_COMMIT=$(git rev-parse --short HEAD 2>/dev/null || echo "unknown")
IMAGE_TAG="${IMAGE_TAG:-${GIT_COMMIT}}"

# Determine registry path based on type
if [ "${REGISTRY_TYPE}" = "glcp" ]; then
    if [ -z "${GLCP_ORG}" ]; then
        echo "ERROR: GLCP_ORG environment variable must be set when using GLCP registry"
        echo "Example: export GLCP_ORG='your-org-id'"
        exit 1
    fi
    # GLCP Container Registry format
    GLCP_REGISTRY="containers.${GLCP_REGION}.greenlakecloud.hpe.com"
    FULL_IMAGE_PATH="${GLCP_REGISTRY}/${GLCP_ORG}/${GLCP_SPACE}/${IMAGE_NAME}:${IMAGE_TAG}"
else
    # GCP Artifact Registry format
    GCP_REGISTRY="${GCP_REGION}-docker.pkg.dev"
    FULL_IMAGE_PATH="${GCP_REGISTRY}/${GCP_PROJECT}/${GCP_REPO}/${IMAGE_NAME}:${IMAGE_TAG}"
fi

# Service account key file path (optional, for CI/CD)
SA_KEY_FILE="${SA_KEY_FILE:-sa-key.json}"
# GLCP API client credentials file (optional, for CI/CD)
GLCP_CLIENT_CREDENTIALS="${GLCP_CLIENT_CREDENTIALS:-glcp-credentials.json}"

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

echo "Step 3: Tagging image for ${REGISTRY_TYPE^^} registry..."
docker tag ${IMAGE_NAME}:${IMAGE_TAG} ${FULL_IMAGE_PATH}

echo "Step 4: Authenticating with registry..."
if [ "${REGISTRY_TYPE}" = "glcp" ]; then
    echo "Authenticating with GLCP Container Registry..."
    
    # Check if GLCP credentials file exists for CI/CD authentication
    if [ -f "${GLCP_CLIENT_CREDENTIALS}" ]; then
        echo "Using GLCP client credentials file for authentication..."
        # Extract client ID and secret from credentials file
        # Check if jq is available for robust JSON parsing
        if command -v jq &> /dev/null; then
            GLCP_CLIENT_ID=$(jq -r '.client_id' ${GLCP_CLIENT_CREDENTIALS})
            GLCP_CLIENT_SECRET=$(jq -r '.client_secret' ${GLCP_CLIENT_CREDENTIALS})
        else
            # Fallback to grep if jq is not available
            echo "Warning: jq not found, using grep for JSON parsing (less robust)"
            GLCP_CLIENT_ID=$(grep -o '"client_id":"[^"]*' < ${GLCP_CLIENT_CREDENTIALS} | cut -d'"' -f4)
            GLCP_CLIENT_SECRET=$(grep -o '"client_secret":"[^"]*' < ${GLCP_CLIENT_CREDENTIALS} | cut -d'"' -f4)
        fi
        
        if [ -z "${GLCP_CLIENT_ID}" ] || [ -z "${GLCP_CLIENT_SECRET}" ]; then
            echo "ERROR: Could not extract client_id or client_secret from ${GLCP_CLIENT_CREDENTIALS}"
            exit 1
        fi
    else
        echo "Using environment variables for GLCP authentication..."
        if [ -z "${GLCP_CLIENT_ID}" ] || [ -z "${GLCP_CLIENT_SECRET}" ]; then
            echo "ERROR: GLCP_CLIENT_ID and GLCP_CLIENT_SECRET environment variables must be set"
            echo "Example:"
            echo "  export GLCP_CLIENT_ID='your-client-id'"
            echo "  export GLCP_CLIENT_SECRET='your-client-secret'"
            exit 1
        fi
    fi
    
    # Authenticate with GLCP and configure Docker
    # Use printf to safely handle special characters in the secret
    printf '%s\n' "${GLCP_CLIENT_SECRET}" | docker login ${GLCP_REGISTRY} -u ${GLCP_CLIENT_ID} --password-stdin
else
    echo "Authenticating with GCP Artifact Registry..."
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
fi

echo "Step 5: Pushing image to ${REGISTRY_TYPE^^} registry..."
docker push ${FULL_IMAGE_PATH}

echo "=============================================="
echo "SUCCESS: Image pushed to ${FULL_IMAGE_PATH}"
echo "=============================================="
echo ""
echo "To pull this image, run:"
echo "  docker pull ${FULL_IMAGE_PATH}"
echo ""
if [ "${REGISTRY_TYPE}" = "glcp" ]; then
    echo "To use this image in GLCP Kubernetes:"
    echo "  1. Create image pull secret:"
    echo "     kubectl create secret docker-registry glcp-registry-secret \\"
    echo "       --docker-server=${GLCP_REGISTRY} \\"
    echo "       --docker-username=\${GLCP_CLIENT_ID} \\"
    echo "       --docker-password=\${GLCP_CLIENT_SECRET}"
    echo "  2. Reference in your pod spec:"
    echo "     imagePullSecrets:"
    echo "       - name: glcp-registry-secret"
fi
echo "=============================================="
