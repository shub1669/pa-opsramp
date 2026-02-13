# PA OpsRamp - Spark Jobs

A modular Scala-based Apache Spark application repository that runs Spark jobs on Kubernetes clusters with Docker images hosted on cloud container registries (GCP Artifact Registry or HPE GreenLake Cloud Platform).

## Supported Platforms

- **Container Registries**: GCP Artifact Registry, GLCP (GreenLake Cloud Platform) Container Registry
- **Kubernetes**: AWS EKS, GKE, GLCP Kubernetes, or any Kubernetes cluster
- **Storage**: AWS S3, Google Cloud Storage (GCS)

📖 **For detailed GLCP deployment instructions, see [docs/GLCP_DEPLOYMENT.md](docs/GLCP_DEPLOYMENT.md)**

## Project Structure

```
pa-opsramp/
├── build.sbt                              # Root SBT build configuration (multi-module)
├── Dockerfile                             # Docker image definition
├── project/
│   ├── build.properties                  # SBT version
│   └── plugins.sbt                       # SBT plugins (assembly)
├── jobs/                                 # Modular Spark jobs directory
│   └── s3-to-delta/                      # S3 to Delta Lake job module
│       └── src/
│           └── main/
│               └── scala/
│                   └── com/opsramp/spark/
│                       └── S3ToDeltaLakeApp.scala
├── k8s/
│   ├── spark-application.yaml            # Kubernetes SparkApplication manifest (GCP/AWS)
│   └── spark-application-glcp.yaml       # Kubernetes SparkApplication manifest (GLCP)
├── scripts/
│   ├── build-and-push.sh                 # Build and push to container registry
│   └── deploy-spark-app.sh               # Deploy Spark app to Kubernetes
└── README.md
```

## Prerequisites

- JDK 11 or later
- SBT 1.9.x
- Docker
- AWS CLI v2
- kubectl
- eksctl
- Helm 3.x

## Quick Start

### 1. Build the Application

```bash
# Build all job modules (fat JARs)
sbt clean assembly

# Build only a specific job
sbt s3ToDeltaJob/assembly
```

### 2. Build and Push Docker Image

#### Option A: Push to GCP Artifact Registry

```bash
# Set environment variables
export GCP_REGION="us-central1"
export GCP_PROJECT="opsramp-registry"
export GCP_REPO="pa-charts"
export IMAGE_TAG="1.0.0"
export REGISTRY_TYPE="gcp"

# Optional: For CI/CD with service account
export SA_KEY_FILE="sa-key.json"

# Run build and push script
chmod +x scripts/build-and-push.sh
./scripts/build-and-push.sh
```

#### Option B: Push to GLCP (GreenLake Cloud Platform) Container Registry

```bash
# Set environment variables
export REGISTRY_TYPE="glcp"
export GLCP_REGION="us1"  # Options: us1, eu1, ap1
export GLCP_ORG="your-glcp-org-id"
export GLCP_SPACE="default"
export IMAGE_TAG="1.0.0"

# Authentication credentials
export GLCP_CLIENT_ID="your-glcp-client-id"
export GLCP_CLIENT_SECRET="your-glcp-client-secret"

# Optional: For CI/CD with credentials file
export GLCP_CLIENT_CREDENTIALS="glcp-credentials.json"

# Run build and push script
chmod +x scripts/build-and-push.sh
./scripts/build-and-push.sh
```

**Note:** To obtain GLCP credentials:
1. Log in to HPE GreenLake Cloud Platform
2. Navigate to your organization settings
3. Create API client credentials (Client ID and Client Secret)
4. Save the credentials securely

### 3. Setup Kubernetes Cluster

#### Option A: AWS EKS Cluster (for GCP registry)

```bash
# Set environment variables
export CLUSTER_NAME="spark-delta-cluster"
export AWS_REGION="us-east-1"
export GCP_REGION="us-central1"
export GCP_PROJECT="opsramp-registry"

# For GCP registry access from AWS
export SA_KEY_FILE="sa-key.json"

# Run setup script (if you have one for AWS EKS)
# chmod +x scripts/setup-aws-cluster.sh
# ./scripts/setup-aws-cluster.sh
```

#### Option B: GLCP Kubernetes Cluster

For GLCP, you'll use the managed Kubernetes service within GreenLake:

1. **Create or access a Kubernetes cluster in GLCP**:
   - Log in to HPE GreenLake Cloud Platform
   - Navigate to the Kubernetes service
   - Create a new cluster or select an existing one
   - Download the kubeconfig file

2. **Configure kubectl to use GLCP cluster**:
   ```bash
   export KUBECONFIG=/path/to/glcp-kubeconfig.yaml
   kubectl config current-context
   ```

3. **Create image pull secret for GLCP registry**:
   ```bash
   kubectl create namespace spark-apps
   
   kubectl create secret docker-registry glcp-registry-secret \
     --docker-server=containers.us1.greenlakecloud.hpe.com \
     --docker-username=${GLCP_CLIENT_ID} \
     --docker-password=${GLCP_CLIENT_SECRET} \
     --namespace=spark-apps
   ```

4. **Create service account**:
   ```bash
   kubectl create serviceaccount spark-service-account -n spark-apps
   ```

### 4. Configure and Deploy Spark Application

Before deploying, update `k8s/spark-application.yaml`:

1. **For GCP Artifact Registry**, update the image path:
   ```yaml
   image: "us-central1-docker.pkg.dev/opsramp-registry/pa-charts/pa-spark:1.0.0"
   imagePullSecrets:
     - name: gcp-registry-secret
   ```

2. **For GLCP Container Registry**, update the image path:
   ```yaml
   image: "containers.us1.greenlakecloud.hpe.com/your-org-id/default/pa-spark:1.0.0"
   imagePullSecrets:
     - name: glcp-registry-secret
   ```

3. Update S3 or GCS paths based on your storage:
   ```yaml
   arguments:
     # For AWS S3
     - "s3a://your-source-bucket/input-data/"
     - "s3a://your-destination-bucket/delta-table/"
     - "parquet"
     
     # Or for GCS
     - "gs://your-source-bucket/input-data/"
     - "gs://your-destination-bucket/delta-table/"
     - "parquet"
   ```

4. Update IAM role or service account annotations as needed

Deploy the application:

```bash
chmod +x scripts/deploy-spark-app.sh
./scripts/deploy-spark-app.sh
```

## Adding New Jobs

To add a new Spark job module:

1. Create a new directory under `jobs/`:
   ```
   jobs/
   └── new-job-name/
       └── src/
           └── main/
               └── scala/
                   └── com/opsramp/spark/
                       └── NewJobApp.scala
   ```

2. Add the module to `build.sbt`:
   ```scala
   lazy val newJob = (project in file("jobs/new-job-name"))
     .settings(commonSettings)
     .settings(
       name := "new-job-name",
       libraryDependencies ++= sparkDependencies ++ commonDependencies,
       assembly / assemblyMergeStrategy := {
         case PathList("META-INF", xs @ _*) => MergeStrategy.discard
         case "reference.conf" => MergeStrategy.concat
         case x => MergeStrategy.first
       },
       assembly / assemblyJarName := "new-job-assembly.jar"
     )
   
   // Add to root aggregate
   lazy val root = (project in file("."))
     .aggregate(s3ToDeltaJob, newJob)
   ```

3. Update `Dockerfile` to include the new job jar:
   ```dockerfile
   COPY jobs/new-job-name/target/scala-2.12/new-job-assembly.jar /opt/spark-app/jobs/
   ```

4. Create a new K8s manifest in `k8s/` for the job.

## Jobs

### S3 to Delta Lake Job

Reads data from S3 and writes to Delta Lake on S3.

**Arguments:**

| Argument | Description | Required |
|----------|-------------|----------|
| source-s3-path | S3 path to read data from | Yes |
| destination-delta-path | S3 path to write Delta Lake table | Yes |
| file-format | Input format: csv, json, parquet, avro | No (default: parquet) |

**Supported Input Formats:**
- **CSV**: Reads with header and inferred schema
- **JSON**: Reads with multiLine support
- **Parquet**: Native Spark parquet reader
- **Avro**: Requires avro format

## Container Registry Setup

### GCP Artifact Registry

If you're using GCP Artifact Registry, no special setup is needed beyond what's in the Quick Start section.

### GLCP (GreenLake Cloud Platform) Container Registry

HPE GreenLake Cloud Platform provides a managed container registry for storing Docker images.

#### Prerequisites for GLCP:
1. Active HPE GreenLake Cloud Platform account
2. Access to an organization in GLCP
3. API client credentials (Client ID and Secret)

#### Setting up GLCP API Credentials:

1. **Create API Client Credentials**:
   - Log in to [HPE GreenLake Cloud Platform](https://common.cloud.hpe.com/)
   - Navigate to **Manage** → **API Clients**
   - Click **Create API Client**
   - Provide a name and description
   - Save the **Client ID** and **Client Secret** (you won't be able to see the secret again)

2. **Assign Appropriate Permissions**:
   - Ensure the API client has permissions to push/pull container images
   - Typical role: Container Registry Administrator or Contributor

3. **Store Credentials Securely**:
   ```bash
   # Set as environment variables
   export GLCP_CLIENT_ID="your-client-id-here"
   export GLCP_CLIENT_SECRET="your-client-secret-here"
   
   # Or create a credentials file (JSON format)
   cat > glcp-credentials.json << EOF
   {
     "client_id": "your-client-id-here",
     "client_secret": "your-client-secret-here"
   }
   EOF
   chmod 600 glcp-credentials.json
   ```

#### GLCP Registry Regions:
- `us1` - United States
- `eu1` - Europe
- `ap1` - Asia Pacific

The registry URL format is: `containers.{region}.greenlakecloud.hpe.com`

## Monitoring

### Check Application Status

```bash
kubectl get sparkapplication -n spark-apps
kubectl describe sparkapplication spark-delta-s3-app -n spark-apps
```

### View Logs

```bash
# Driver logs
kubectl logs -f spark-delta-s3-app-driver -n spark-apps

# Executor logs
kubectl logs -f spark-delta-s3-app-exec-1 -n spark-apps
```

### Spark UI

```bash
kubectl port-forward spark-delta-s3-app-driver 4040:4040 -n spark-apps
# Access at http://localhost:4040
```

## Configuration

### Spark Configurations (in spark-application.yaml)

```yaml
sparkConf:
  # Delta Lake
  "spark.sql.extensions": "io.delta.sql.DeltaSparkSessionExtension"
  "spark.sql.catalog.spark_catalog": "org.apache.spark.sql.delta.catalog.DeltaCatalog"
  
  # S3
  "spark.hadoop.fs.s3a.impl": "org.apache.hadoop.fs.s3a.S3AFileSystem"
  
  # Performance
  "spark.sql.adaptive.enabled": "true"
  "spark.serializer": "org.apache.spark.serializer.KryoSerializer"
```

### Resource Tuning

Adjust driver and executor resources based on your workload:

```yaml
driver:
  cores: 1
  memory: "2g"

executor:
  cores: 2
  instances: 2
  memory: "4g"
```

## Troubleshooting

### Common Issues

1. **Image Pull Error (GCP)**
   - Verify GCP service account has correct permissions
   - Check image path in spark-application.yaml
   - Ensure registry secret is created correctly

2. **Image Pull Error (GLCP)**
   - Verify GLCP client credentials are valid
   - Check that the GLCP organization and space are correct
   - Ensure registry secret is created with correct server URL
   - Verify the image was pushed successfully to GLCP registry

3. **S3 Access Denied**
   - Verify IAM role has correct S3 permissions
   - Check IRSA annotation on service account

4. **GCS Access Denied (for GCP)**
   - Verify service account has Storage Object Viewer/Creator roles
   - Check that GCS connector JARs are included in Docker image

5. **Out of Memory**
   - Increase executor memory
   - Enable dynamic allocation

### Debug Commands

```bash
# Check pods
kubectl get pods -n spark-apps

# Check events
kubectl get events -n spark-apps --sort-by='.lastTimestamp'

# Check Spark Operator logs
kubectl logs -f deployment/spark-operator -n spark-operator
```

## Local Testing

```bash
# Run locally with spark-submit
spark-submit \
  --class com.opsramp.spark.S3ToDeltaLakeApp \
  --master local[*] \
  --packages io.delta:delta-spark_2.12:3.0.0,org.apache.hadoop:hadoop-aws:3.3.4 \
  --conf spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension \
  --conf spark.sql.catalog.spark_catalog=org.apache.spark.sql.delta.catalog.DeltaCatalog \
  jobs/s3-to-delta/target/scala-2.12/s3-to-delta-assembly.jar \
  s3a://your-bucket/input/ \
  s3a://your-bucket/delta-output/ \
  parquet
```

## License

MIT License
