#!/bin/bash

# ==============================================================================
# Deploy Spark Application from JFrog to AWS EKS
# ==============================================================================

set -e

NAMESPACE="${NAMESPACE:-spark-apps}"
APP_NAME="${APP_NAME:-spark-delta-s3-app}"

echo "=============================================="
echo "Deploying Spark Application"
echo "=============================================="

# Deploy the application
echo "Step 1: Deploying Spark application..."
kubectl apply -f k8s/spark-application.yaml

# Wait for the application to be submitted
echo "Step 2: Waiting for application to be submitted..."
sleep 10

# Check application status
echo "Step 3: Checking application status..."
kubectl get sparkapplication ${APP_NAME} -n ${NAMESPACE}

# Stream driver logs (optional)
echo ""
echo "To view driver logs, run:"
echo "kubectl logs -f ${APP_NAME}-driver -n ${NAMESPACE}"

echo ""
echo "To check application status:"
echo "kubectl describe sparkapplication ${APP_NAME} -n ${NAMESPACE}"

echo ""
echo "To delete the application:"
echo "kubectl delete sparkapplication ${APP_NAME} -n ${NAMESPACE}"
