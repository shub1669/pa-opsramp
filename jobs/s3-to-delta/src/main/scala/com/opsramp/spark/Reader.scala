package com.opsramp.spark

import com.opsramp.spark.commonutils.CommonUtils
import org.apache.spark.sql.{DataFrame, SparkSession}


/*
# Read all_logs
sbt "s3ToDeltaJob/runMain com.opsramp.spark.Reader"

# Read critical_logs
  sbt "s3ToDeltaJob/runMain com.opsramp.spark.Reader critical_logs"

# Read sample_logs
  sbt "s3ToDeltaJob/runMain com.opsramp.spark.Reader sample_logs"
*/

object Reader {

  private val VALID_LOG_TYPES: Seq[String] = Seq("all_logs", "critical_logs", "sample_logs")

  def main(args: Array[String]): Unit = {
    org.apache.log4j.Logger.getLogger("org").setLevel(org.apache.log4j.Level.ERROR)
    org.apache.log4j.Logger.getLogger("akka").setLevel(org.apache.log4j.Level.ERROR)
    org.apache.log4j.Logger.getLogger("io").setLevel(org.apache.log4j.Level.ERROR)

    val logType = if (args.length > 0) args(0) else "all_logs"

    if (!VALID_LOG_TYPES.contains(logType)) {
      println(s"Invalid log type: $logType")
      println(s"Valid options: ${VALID_LOG_TYPES.mkString(", ")}")
      System.exit(1)
    }

    val spark = CommonUtils.getSparkSession("DeltaLogReader", isLocal = true)
    spark.sparkContext.setLogLevel("ERROR")

    val deltaPath = getDeltaPath(logType)

    println(s"\n${"=" * 60}")
    println(s"READING DELTA TABLE: $logType")
    println(s"Path: $deltaPath")
    println(s"${"=" * 60}\n")

    try {
      val df = readDeltaTable(spark, deltaPath)
      showLogData(df, logType)
    } catch {
      case e: Exception =>
        println(s"Error reading Delta table: ${e.getMessage}")
        println(s"Make sure the Delta table exists at: $deltaPath")
    } finally {
      spark.stop()
    }
  }

  def getDeltaPath(logType: String): String = {
    val currentDir = new java.io.File(".").getCanonicalPath

    val projectRoot = if (currentDir.endsWith("jobs/s3-to-delta")) {
      new java.io.File(currentDir).getParentFile.getParentFile.getCanonicalPath
    } else if (currentDir.contains("/pa-opsramp")) {
      val idx = currentDir.indexOf("/pa-opsramp")
      currentDir.substring(0, idx + "/pa-opsramp".length)
    } else {
      currentDir
    }

    s"$projectRoot/data/delta-output/$logType"
  }

  def readDeltaTable(spark: SparkSession, path: String): DataFrame = {
    spark.read.format("delta").load(path)
  }

  def showLogData(df: DataFrame, logType: String): Unit = {
    val totalCount = df.count()
    println(s"Total records in $logType: $totalCount")

    println(s"\n--- Schema ---")
    df.printSchema()

    println(s"\n--- Sample Data (Top 10) ---")
    df.select("opsramp_log_id", "level", "message", "time", "k8s_pod_name")
      .show(10, truncate = 80)

    println(s"\n--- Log Level Distribution ---")
    df.groupBy("level")
      .count()
      .orderBy(df("level").desc)
      .show()
  }

}
