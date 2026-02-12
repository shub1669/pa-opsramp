# PA OpsRamp - Spark Jobs

A modular Scala-based Apache Spark application repository that runs Spark jobs on AWS EKS with Docker images hosted on JFrog Artifactory.

## Project Structure

```
pa-opsramp/
├── build.sbt                           # Root SBT build configuration (multi-module)
├── Dockerfile                          # Docker image definition
├── project/
│   ├── build.properties               # SBT version
│   └── plugins.sbt                    # SBT plugins (assembly)
├── jobs/                              # Modular Spark jobs directory
│   └── s3-to-delta/                   # S3 to Delta Lake job module
│       └── src/
│           └── main/
│               └── scala/
│                   └── com/opsramp/spark/
│                       └── S3ToDeltaLakeApp.scala
├── k8s/
│   └── spark-application.yaml         # Kubernetes SparkApplication manifest
├── scripts/
│   ├── build-and-push.sh             # Build and push to JFrog
│   ├── setup-aws-cluster.sh          # Setup AWS EKS cluster
│   └── deploy-spark-app.sh           # Deploy Spark app to EKS
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

### 2. Build and Push Docker Image to JFrog

```bash
# Set environment variables
export JFROG_REGISTRY="your-jfrog-instance.jfrog.io"
export JFROG_REPO="docker-local"
export JFROG_USER="your-username"
export JFROG_PASSWORD="your-password"
export IMAGE_TAG="1.0.0"

# Run build and push script
chmod +x scripts/build-and-push.sh
./scripts/build-and-push.sh
```

### 3. Setup AWS EKS Cluster

```bash
# Set environment variables
export CLUSTER_NAME="spark-delta-cluster"
export AWS_REGION="us-east-1"
export JFROG_REGISTRY="your-jfrog-instance.jfrog.io"
export JFROG_USER="your-username"
export JFROG_PASSWORD="your-password"

# Run setup script
chmod +x scripts/setup-aws-cluster.sh
./scripts/setup-aws-cluster.sh
```

### 4. Configure and Deploy Spark Application

Before deploying, update `k8s/spark-application.yaml`:

1. Update the image path:
   ```yaml
   image: "your-jfrog-instance.jfrog.io/docker-local/spark-delta-s3:1.0.0"
   ```

2. Update S3 paths:
   ```yaml
   arguments:
     - "s3a://your-source-bucket/input-data/"
     - "s3a://your-destination-bucket/delta-table/"
     - "parquet"
   ```

3. Update AWS account ID in annotations:
   ```yaml
   eks.amazonaws.com/role-arn: "arn:aws:iam::YOUR_ACCOUNT_ID:role/SparkS3AccessRole"
   ```

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

1. **Image Pull Error**
   - Verify JFrog registry secret is created
   - Check image path in spark-application.yaml

2. **S3 Access Denied**
   - Verify IAM role has correct S3 permissions
   - Check IRSA annotation on service account

3. **Out of Memory**
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
