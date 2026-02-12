package com.opsramp.spark.commonutils

import org.apache.spark.sql.functions._
import org.apache.spark.sql.{DataFrame, SparkSession}

object CommonUtils {

  val CRITICAL_LEVELS: Seq[String] = Seq("Critical", "Error", "Fatal", "CRITICAL", "ERROR", "FATAL", "Severe", "SEVERE")

  val WARNING_LEVELS: Seq[String] = Seq("Warning", "Warn", "WARNING", "WARN")

  val SAMPLE_LEVELS: Seq[String] = Seq("Info", "Debug", "Trace", "INFO", "DEBUG", "TRACE")

  def getSparkSession(appName: String, isLocal: Boolean): SparkSession = {
    val builder = SparkSession.builder()
      .appName(appName)
      .config("spark.sql.extensions", "io.delta.sql.DeltaSparkSessionExtension")
      .config("spark.sql.catalog.spark_catalog", "org.apache.spark.sql.delta.catalog.DeltaCatalog")

    if (isLocal) {
      builder.master("local[*]")
    }
    builder.getOrCreate()
  }


  def readJsonLogs(spark: SparkSession, path: String): DataFrame = {
    spark.read
      .option("multiLine", "true")
      .json(path)
  }


  def writeToDelta(df: DataFrame, path: String, mode: String = "overwrite"): Unit = {
    df.write
      .format("delta")
      .mode(mode)
      .option("overwriteSchema", "true")
      .save(path)
  }


  def filterCriticalLogs(df: DataFrame, levelColumn: String = "level"): DataFrame = {
    df.filter(col(levelColumn).isin(CRITICAL_LEVELS: _*))
  }


  def filterWarningLogs(df: DataFrame, levelColumn: String = "level"): DataFrame = {
    df.filter(col(levelColumn).isin(WARNING_LEVELS: _*))
  }


  def filterSampleLogs(df: DataFrame, levelColumn: String = "level"): DataFrame = {
    df.filter(col(levelColumn).isin(SAMPLE_LEVELS: _*))
  }


  def addProcessingMetadata(df: DataFrame): DataFrame = {
    df.withColumn("processed_at", current_timestamp())
      .withColumn("processing_date", current_date())
  }


  def detectCriticalPatterns(df: DataFrame, messageColumn: String = "message"): DataFrame = {
    val criticalPatterns = "(?i)(error|exception|failed|failure|critical|fatal|crash|panic|timeout|refused|denied|unauthorized)"
    df.withColumn("has_critical_pattern", col(messageColumn).rlike(criticalPatterns))
  }

}
