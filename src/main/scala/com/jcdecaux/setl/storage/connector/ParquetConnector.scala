package com.jcdecaux.setl.storage.connector

import com.jcdecaux.setl.annotation.InterfaceStability
import com.jcdecaux.setl.config.{Conf, FileConnectorConf}
import com.jcdecaux.setl.enums.Storage
import com.jcdecaux.setl.util.TypesafeConfigUtils
import com.typesafe.config.Config
import org.apache.spark.sql._

/**
 * ParquetConnector contains functionality for transforming [[DataFrame]] into parquet files
 */
@InterfaceStability.Evolving
class ParquetConnector(override val options: FileConnectorConf) extends FileConnector(options) {

  def this(options: Map[String, String]) = this(FileConnectorConf.fromMap(options))

  def this(path: String, saveMode: SaveMode) = this(Map("path" -> path, "saveMode" -> saveMode.toString))

  def this(config: Config) = this(TypesafeConfigUtils.getMap(config))

  def this(conf: Conf) = this(conf.toMap)

  @deprecated("use the constructor with no spark session", "0.3.4")
  def this(spark: SparkSession, options: FileConnectorConf) = this(options)

  @deprecated("use the constructor with no spark session", "0.3.4")
  def this(spark: SparkSession, options: Map[String, String]) = this(FileConnectorConf.fromMap(options))

  @deprecated("use the constructor with no spark session", "0.3.4")
  def this(spark: SparkSession, path: String, saveMode: SaveMode) = this(path, saveMode)

  @deprecated("use the constructor with no spark session", "0.3.4")
  def this(spark: SparkSession, config: Config) = this(TypesafeConfigUtils.getMap(config))

  @deprecated("use the constructor with no spark session", "0.3.4")
  def this(spark: SparkSession, conf: Conf) = this(conf.toMap)

  override val storage: Storage = Storage.PARQUET

  this.options.setStorage(storage)
}
