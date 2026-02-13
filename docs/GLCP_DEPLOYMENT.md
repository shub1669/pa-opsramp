# GLCP (GreenLake Cloud Platform) Deployment Guide

This guide provides step-by-step instructions for deploying the Spark application to HPE GreenLake Cloud Platform.

## Prerequisites

1. Active HPE GreenLake Cloud Platform account
2. Access to a GLCP organization
3. GLCP Kubernetes cluster (or ability to create one)
4. Docker installed locally
5. kubectl configured to access GLCP cluster
6. SBT and JDK for building the Spark application

## Step 1: Obtain GLCP API Credentials

1. Log in to [HPE GreenLake Cloud Platform](https://common.cloud.hpe.com/)
2. Navigate to **Manage** → **API Clients**
3. Click **Create API Client**
4. Provide a name (e.g., "Spark Pipeline CI/CD")
5. Save the **Client ID** and **Client Secret** securely

## Step 2: Configure Environment Variables

Create a file `glcp-env.sh` with your GLCP configuration:

```bash
#!/bin/bash

# GLCP Registry Configuration
export REGISTRY_TYPE="glcp"
export GLCP_REGION="us1"  # Options: us1, eu1, ap1
export GLCP_ORG="your-organization-id"
export GLCP_SPACE="default"

# GLCP API Credentials
export GLCP_CLIENT_ID="your-client-id"
export GLCP_CLIENT_SECRET="your-client-secret"

# Image Configuration
export IMAGE_NAME="pa-spark"
export IMAGE_TAG="1.0.0"  # Or use git commit hash

echo "GLCP environment configured:"
echo "  Registry: containers.${GLCP_REGION}.greenlakecloud.hpe.com"
echo "  Organization: ${GLCP_ORG}"
echo "  Image: ${IMAGE_NAME}:${IMAGE_TAG}"
```

Load the environment:
```bash
source glcp-env.sh
```

## Step 3: Build and Push to GLCP Registry

```bash
# Make sure you're in the repository root
cd /path/to/pa-opsramp

# Build and push
chmod +x scripts/build-and-push.sh
./scripts/build-and-push.sh
```

The script will:
1. Build all Scala modules with SBT
2. Create a Docker image
3. Authenticate with GLCP Container Registry
4. Push the image to GLCP

## Step 4: Setup GLCP Kubernetes Cluster

### Option A: Use Existing GLCP Kubernetes Cluster

1. Download the kubeconfig from GLCP:
   - Navigate to **Kubernetes** in GLCP console
   - Select your cluster
   - Download kubeconfig file

2. Configure kubectl:
   ```bash
   export KUBECONFIG=/path/to/glcp-kubeconfig.yaml
   kubectl config current-context
   kubectl get nodes
   ```

### Option B: Create New GLCP Kubernetes Cluster

1. In GLCP console, navigate to **Kubernetes**
2. Click **Create Cluster**
3. Configure cluster settings:
   - Name: spark-workload-cluster
   - Region: (match your GLCP_REGION)
   - Node pools: Configure based on workload
4. Wait for cluster provisioning
5. Download kubeconfig

## Step 5: Create Kubernetes Resources

```bash
# Create namespace
kubectl create namespace spark-apps

# Create image pull secret for GLCP registry
kubectl create secret docker-registry glcp-registry-secret \
  --docker-server=containers.${GLCP_REGION}.greenlakecloud.hpe.com \
  --docker-username=${GLCP_CLIENT_ID} \
  --docker-password=${GLCP_CLIENT_SECRET} \
  --namespace=spark-apps

# Create service account
kubectl create serviceaccount spark-service-account -n spark-apps
```

## Step 6: Install Spark Operator

If not already installed:

```bash
# Add Spark Operator Helm repository
helm repo add spark-operator https://googlecloudplatform.github.io/spark-on-k8s-operator
helm repo update

# Install Spark Operator
helm install spark-operator spark-operator/spark-operator \
  --namespace spark-operator \
  --create-namespace \
  --set webhook.enable=true
```

Verify installation:
```bash
kubectl get pods -n spark-operator
```

## Step 7: Configure Storage Access

### For Google Cloud Storage (GCS):

1. Create a GCP service account with Storage permissions
2. Download the JSON key file
3. Create Kubernetes secret:
   ```bash
   kubectl create secret generic gcs-credentials \
     --from-file=key.json=/path/to/service-account-key.json \
     -n spark-apps
   ```

4. Update `k8s/spark-application-glcp.yaml` to mount the secret

### For AWS S3:

Configure IRSA (IAM Roles for Service Accounts) or use AWS credentials as Kubernetes secrets.

## Step 8: Update and Deploy Spark Application

1. Edit `k8s/spark-application-glcp.yaml`:
   ```yaml
   # Update image path
   image: "containers.us1.greenlakecloud.hpe.com/YOUR_ORG_ID/default/pa-spark:1.0.0"
   
   # Update storage paths
   arguments:
     - "gs://your-bucket/input/"
     - "gs://your-bucket/output/"
     - "parquet"
   ```

2. Deploy the application:
   ```bash
   kubectl apply -f k8s/spark-application-glcp.yaml
   ```

## Step 9: Monitor Application

```bash
# Check application status
kubectl get sparkapplication -n spark-apps
kubectl describe sparkapplication spark-delta-glcp-app -n spark-apps

# View driver logs
kubectl logs -f spark-delta-glcp-app-driver -n spark-apps

# View executor logs (if running)
kubectl get pods -n spark-apps | grep executor
kubectl logs -f spark-delta-glcp-app-<executor-id> -n spark-apps

# Access Spark UI (optional)
kubectl port-forward spark-delta-glcp-app-driver 4040:4040 -n spark-apps
# Then open http://localhost:4040 in browser
```

## Troubleshooting

### Image Pull Errors

```bash
# Verify secret exists
kubectl get secret glcp-registry-secret -n spark-apps

# Check secret contents
kubectl get secret glcp-registry-secret -n spark-apps -o yaml

# Recreate secret if needed
kubectl delete secret glcp-registry-secret -n spark-apps
kubectl create secret docker-registry glcp-registry-secret \
  --docker-server=containers.${GLCP_REGION}.greenlakecloud.hpe.com \
  --docker-username=${GLCP_CLIENT_ID} \
  --docker-password=${GLCP_CLIENT_SECRET} \
  --namespace=spark-apps
```

### Storage Access Issues

For GCS:
```bash
# Verify GCS credentials secret
kubectl get secret gcs-credentials -n spark-apps

# Test GCS access from a pod
kubectl run -it --rm test-gcs --image=google/cloud-sdk:slim -n spark-apps -- bash
# In the pod: gsutil ls gs://your-bucket/
```

### Application Not Starting

```bash
# Check Spark Operator logs
kubectl logs -f deployment/spark-operator -n spark-operator

# Check events
kubectl get events -n spark-apps --sort-by='.lastTimestamp'

# Describe the SparkApplication
kubectl describe sparkapplication spark-delta-glcp-app -n spark-apps
```

## CI/CD Integration

For automated deployments, create a credentials file:

```json
{
  "client_id": "your-glcp-client-id",
  "client_secret": "your-glcp-client-secret"
}
```

Save as `glcp-credentials.json` and set:
```bash
export GLCP_CLIENT_CREDENTIALS="glcp-credentials.json"
```

The build script will automatically use this file if available.

## Clean Up

```bash
# Delete the application
kubectl delete sparkapplication spark-delta-glcp-app -n spark-apps

# Delete namespace (removes all resources)
kubectl delete namespace spark-apps

# Uninstall Spark Operator (if needed)
helm uninstall spark-operator -n spark-operator
kubectl delete namespace spark-operator
```

## Best Practices

1. **Use specific image tags** instead of `latest` for production
2. **Enable resource quotas** in the namespace to prevent resource exhaustion
3. **Set up monitoring** using GLCP's built-in monitoring or Prometheus
4. **Use secrets management** - never commit credentials to git
5. **Enable pod security policies** for production workloads
6. **Configure autoscaling** for executor pods based on workload
7. **Set up log aggregation** for easier debugging

## Additional Resources

- [HPE GreenLake Cloud Platform Documentation](https://support.hpe.com/hpesc/public/docDisplay?docId=a00120892en_us)
- [Spark on Kubernetes Operator Guide](https://github.com/GoogleCloudPlatform/spark-on-k8s-operator)
- [Delta Lake Documentation](https://docs.delta.io/)
