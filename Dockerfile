# Use official Spark image with Scala support
FROM apache/spark:3.5.8-scala2.12-java17-python3-ubuntu

# Set environment variables
ENV SPARK_HOME=/opt/spark
ENV PATH=$PATH:$SPARK_HOME/bin:$SPARK_HOME/sbin

USER root

RUN apt-get update && apt-get install -y \
    curl \
    wget \
    unzip \
    && rm -rf /var/lib/apt/lists/*

# Download Delta Lake and GCS dependencies for GCP
RUN cd $SPARK_HOME/jars && \
    wget -q https://repo1.maven.org/maven2/io/delta/delta-spark_2.12/3.0.0/delta-spark_2.12-3.0.0.jar && \
    wget -q https://repo1.maven.org/maven2/io/delta/delta-storage/3.0.0/delta-storage-3.0.0.jar && \
    wget -q https://storage.googleapis.com/hadoop-lib/gcs/gcs-connector-hadoop3-2.2.18.jar

RUN mkdir -p /opt/spark-app/jobs

COPY jobs/s3-to-delta/target/scala-2.12/s3-to-delta-assembly.jar /opt/spark-app/jobs/

# Set working directory
WORKDIR /opt/spark-app

# Switch back to spark user for security
USER spark

# Default entrypoint - can be overridden for different jobs
ENTRYPOINT ["/opt/spark/bin/spark-submit", \
    "--class", "com.opsramp.spark.S3ToDeltaLakeApp", \
    "--conf", "spark.sql.extensions=io.delta.sql.DeltaSparkSessionExtension", \
    "--conf", "spark.sql.catalog.spark_catalog=org.apache.spark.sql.delta.catalog.DeltaCatalog", \
    "--conf", "spark.hadoop.fs.gs.impl=com.google.cloud.hadoop.fs.gcs.GoogleHadoopFileSystem", \
    "--conf", "spark.hadoop.fs.AbstractFileSystem.gs.impl=com.google.cloud.hadoop.fs.gcs.GoogleHadoopFS", \
    "/opt/spark-app/jobs/s3-to-delta-assembly.jar"]

CMD ["--help"]
