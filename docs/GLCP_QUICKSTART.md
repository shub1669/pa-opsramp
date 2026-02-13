# Quick Start: Push to GLCP

This is a quick reference guide for pushing Docker images to HPE GreenLake Cloud Platform (GLCP).

## One-Time Setup

1. **Get GLCP credentials** from https://common.cloud.hpe.com/
   - Navigate to: Manage → API Clients → Create API Client
   - Save your Client ID and Client Secret

2. **Set environment variables**:
   ```bash
   export REGISTRY_TYPE="glcp"
   export GLCP_REGION="us1"              # us1, eu1, or ap1
   export GLCP_ORG="your-org-id"         # Your GLCP organization ID
   export GLCP_CLIENT_ID="your-id"       # From step 1
   export GLCP_CLIENT_SECRET="your-secret"  # From step 1
   ```

## Build and Push

```bash
# From repository root
./scripts/build-and-push.sh
```

That's it! The script will:
- Build the Scala application with SBT
- Create the Docker image
- Authenticate with GLCP
- Push to: `containers.${GLCP_REGION}.greenlakecloud.hpe.com/${GLCP_ORG}/default/pa-spark`

## Deploy to GLCP Kubernetes

```bash
# 1. Configure kubectl for GLCP
export KUBECONFIG=/path/to/glcp-kubeconfig.yaml

# 2. Create namespace and secrets
kubectl create namespace spark-apps
kubectl create secret docker-registry glcp-registry-secret \
  --docker-server=containers.${GLCP_REGION}.greenlakecloud.hpe.com \
  --docker-username=${GLCP_CLIENT_ID} \
  --docker-password=${GLCP_CLIENT_SECRET} \
  -n spark-apps

# 3. Edit k8s/spark-application-glcp.yaml with your image path and settings

# 4. Deploy
kubectl apply -f k8s/spark-application-glcp.yaml
```

## Troubleshooting

**Authentication failed?**
- Verify your Client ID and Secret are correct
- Check that GLCP_ORG is your actual organization ID

**Image not found?**
- Ensure the image was pushed successfully (check script output)
- Verify the image path in your YAML matches the pushed image

**Pod can't pull image?**
- Verify the secret was created: `kubectl get secret glcp-registry-secret -n spark-apps`
- Check the secret is referenced in your SparkApplication YAML

## More Information

- Full deployment guide: [docs/GLCP_DEPLOYMENT.md](GLCP_DEPLOYMENT.md)
- Main README: [../README.md](../README.md)
