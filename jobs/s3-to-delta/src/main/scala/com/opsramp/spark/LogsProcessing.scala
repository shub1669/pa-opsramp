package com.opsramp.spark

import com.opsramp.spark.commonutils.CommonUtils
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions._
import io.delta.tables.DeltaTable

// Run Command :
// cd /Users/shubhamsoni/Desktop/melody/pa-opsramp && sbt clean "s3ToDeltaJob/runMain com.opsramp.spark.LogsProcessing"
object LogsProcessing {

  def main(args: Array[String]): Unit = {

    println("\n Starting Logs Processing Pipeline...\n")

    org.apache.log4j.Logger.getLogger("org").setLevel(org.apache.log4j.Level.ERROR)
    org.apache.log4j.Logger.getLogger("akka").setLevel(org.apache.log4j.Level.ERROR)
    org.apache.log4j.Logger.getLogger("io").setLevel(org.apache.log4j.Level.ERROR)

    val currentDir = new java.io.File(".").getCanonicalPath

    val projectRoot = if (currentDir.endsWith("jobs/s3-to-delta")) {
      new java.io.File(currentDir).getParentFile.getParentFile.getCanonicalPath
    } else if (currentDir.contains("/pa-opsramp")) {
      val idx = currentDir.indexOf("/pa-opsramp")
      currentDir.substring(0, idx + "/pa-opsramp".length)
    } else {
      currentDir
    }

    val sourcePath = s"$projectRoot/data/logs"
    val outputBasePath = s"$projectRoot/data/delta-output"

    val spark = CommonUtils.getSparkSession("LogsProcessingPipeline", isLocal = true)
    spark.sparkContext.setLogLevel("ERROR")

    import spark.implicits._

    try {
      // Step 1: Read raw JSON logs
      val rawLogsDF = CommonUtils.readJsonLogs(spark, sourcePath)

      val flattenedLogsDF = flattenLogs(rawLogsDF)
      val totalLogs = flattenedLogsDF.count()

      val enrichedLogsDF = CommonUtils.addProcessingMetadata(flattenedLogsDF)
      val logsWithPatterns = CommonUtils.detectCriticalPatterns(enrichedLogsDF, "message")

      logsWithPatterns.cache()


      val criticalLogsDF = logsWithPatterns.filter(
        col("level").isin(CommonUtils.CRITICAL_LEVELS: _*) ||
        col("has_critical_pattern") === true
      )

      val warningLogsDF = CommonUtils.filterWarningLogs(logsWithPatterns)

      val sampleLogsDF = logsWithPatterns.filter(
        col("level").isin(CommonUtils.SAMPLE_LEVELS: _*) &&
        col("has_critical_pattern") === false
      )

      val criticalCount = criticalLogsDF.count()
      val warningCount = warningLogsDF.count()
      val sampleCount = sampleLogsDF.count()

      // Print segregation summary
      println("\n" + "=" * 60)
      println("LOG SEGREGATION SUMMARY")
      println("=" * 60)
      println(f"Total Logs:    $totalLogs%,d")
      println(f"Critical Logs: $criticalCount%,d (${criticalCount * 100.0 / totalLogs}%.2f%%)")
      println(f"Warning Logs:  $warningCount%,d (${warningCount * 100.0 / totalLogs}%.2f%%)")
      println(f"Sample Logs:   $sampleCount%,d (${sampleCount * 100.0 / totalLogs}%.2f%%)")
      println("=" * 60)

      val criticalOutputPath = s"$outputBasePath/critical_logs"
      val warningOutputPath = s"$outputBasePath/warning_logs"
      val sampleOutputPath = s"$outputBasePath/sample_logs"
      val allLogsOutputPath = s"$outputBasePath/all_logs"

      if (criticalCount > 0) {
        CommonUtils.writeToDelta(criticalLogsDF, criticalOutputPath)
      }

      if (warningCount > 0) {
        CommonUtils.writeToDelta(warningLogsDF, warningOutputPath)
      }

      if (sampleCount > 0) {
        CommonUtils.writeToDelta(sampleLogsDF, sampleOutputPath)
      }

      CommonUtils.writeToDelta(logsWithPatterns, allLogsOutputPath)

      println("\nDelta Tables Status:")
      verifyDeltaTables(spark, outputBasePath)

      if (criticalCount > 0) {
        println("\n--- Critical Logs Sample (Top 5) ---")
        criticalLogsDF.select("opsramp_log_id", "level", "message", "time", "k8s_pod_name")
          .show(5, truncate = 80)
      }

      logsWithPatterns.unpersist()

      println("\n✓ Log Processing Pipeline Completed Successfully!")

    } catch {
      case e: Exception =>
        System.err.println(s"Error processing logs: ${e.getMessage}")
        e.printStackTrace()
        System.exit(1)
    } finally {
      spark.stop()
    }
  }


  def flattenLogs(df: DataFrame): DataFrame = {

    if (df.columns.contains("results")) {
      df.select(explode(col("results")).as("result"))
        .select(
          col("result.timestamp").as("event_timestamp"),
          col("result.log.*")
        )
        .toDF(df.select(explode(col("results")).as("result"))
          .select(col("result.timestamp").as("event_timestamp"), col("result.log.*"))
          .columns
          .map(_.replace(".", "_")): _*)
    } else {
      // Assume already flattened structure
      df.toDF(df.columns.map(_.replace(".", "_")): _*)
    }
  }

  /**
   * Verify that Delta tables were written correctly
   */
  def verifyDeltaTables(spark: SparkSession, basePath: String): Unit = {
    val tables = Seq("critical_logs", "warning_logs", "sample_logs", "all_logs")

    tables.foreach { tableName =>
      val tablePath = s"$basePath/$tableName"
      try {
        if (DeltaTable.isDeltaTable(spark, tablePath)) {
          val count = spark.read.format("delta").load(tablePath).count()
          println(s"✓ $tableName: $count records")
        } else {
          println(s"○ $tableName: Not created (no data)")
        }
      } catch {
        case _: Exception =>
          println(s"○ $tableName: Not found")
      }
    }
  }
}
