package com.jcdecaux.setl.internal

import com.jcdecaux.setl.annotation.CompoundKey
import com.jcdecaux.setl.exception.InvalidSchemaException
import com.jcdecaux.setl.storage.Compressor
import org.apache.spark.sql.catalyst.encoders.ExpressionEncoder
import org.apache.spark.sql.types.StructType
import org.apache.spark.sql.{Column, DataFrame, Dataset, functions}

import scala.reflect.runtime.{universe => ru}

/**
 * SchemaConverter will rename the column of a dataset/dataframe according to the given case class T.
 *
 * {{{
 *   import com.jcdecaux.setl.annotations.colName
 *   case class MyObject(@colName("col1") column1: String, column2: String)
 *
 *   val ds: Dataset[MyObject] = Seq(MyObject("a", "A"), MyObject("b", "B")).toDS()
 *   // +-------+-------+
 *   // |column1|column2|
 *   // +-------+-------+
 *   // |      a|      A|
 *   // |      b|      B|
 *   // +-------+-------+
 *
 *   val df = SchemaConverter.toDF(ds)
 *   // +----+-------+
 *   // |col1|column2|
 *   // +----+-------+
 *   // |   a|      A|
 *   // |   b|      B|
 *   // +----+-------+
 *
 *   val ds2 = SchemaConverter.fromDF[MyObject](df)
 *   // +-------+-------+
 *   // |column1|column2|
 *   // +-------+-------+
 *   // |      a|      A|
 *   // |      b|      B|
 *   // +-------+-------+
 *
 * }}}
 */
object SchemaConverter extends Logging {

  private[setl] val COMPOUND_KEY: String = StructAnalyser.COMPOUND_KEY
  private[setl] val COLUMN_NAME: String = StructAnalyser.COLUMN_NAME
  private[setl] val COMPRESS: String = StructAnalyser.COMPRESS

  private[this] val compoundKeySuffix: String = "_key"
  private[this] val compoundKeyPrefix: String = "_"
  private[this] val compoundKeySeparator: String = "-"

  private[this] val compoundKeyName: String => String =
    (compoundKeyId: String) => s"$compoundKeyPrefix$compoundKeyId$compoundKeySuffix"

  private[this] val compoundKeyColumn: Seq[Column] => Column =
    (columns: Seq[Column]) => functions.concat_ws(compoundKeySeparator, columns: _*)


  /**
   * Convert a DataFrame to Dataset according to the annotations
   *
   * @param dataFrame input df
   * @tparam T type of dataset
   * @return
   */
  @throws[InvalidSchemaException]
  def fromDF[T: ru.TypeTag](dataFrame: DataFrame): Dataset[T] = {
    val encoder = ExpressionEncoder[T]
    val structType = StructAnalyser.analyseSchema[T]

    val dfColumns = dataFrame.columns
    val columnsToAdd = structType
      .filter {
        field =>
          val dfContainsFieldName = dfColumns.contains(field.name)
          val dfContainsFieldAlias = if (field.metadata.contains(COLUMN_NAME)) {
            dfColumns.contains(field.metadata.getStringArray(COLUMN_NAME).head)
          } else {
            false
          }

          // add an empty column only if the DF contains neither the field nor the field's alias
          (!dfContainsFieldName) && (!dfContainsFieldAlias)
      }

    // If there is any non-nullable missing column, throw an InvalidSchemaException
    if (!columnsToAdd.forall(_.nullable)) {
      throw new InvalidSchemaException(
        s"Find missing non-nullable column(s) [${columnsToAdd.filter(!_.nullable).map(_.name).mkString(",")}]")
    }

    val df = dataFrame
      .transform(dropCompoundKeyColumns(structType))
      .transform(replaceDFColNameByFieldName(structType))
      .transform(decompressColumn(structType))

    // Add null column for each element of columnsToAdd into df
    columnsToAdd
      .foldLeft(df)((df, field) => df.withColumn(field.name, functions.lit(null).cast(field.dataType)))
      .select(encoder.schema.map(x => functions.col(x.name)): _*) // re-order columns
      .as[T](encoder)
  }

  /**
   * Convert a dataset to a DataFrame according to annotations
   *
   * @param dataset input dataset
   * @tparam T type of dataset
   * @return
   */
  def toDF[T: ru.TypeTag](dataset: Dataset[T]): DataFrame = {
    val structType = StructAnalyser.analyseSchema[T]

    dataset
      .toDF()
      .transform(addCompoundKeyColumns(structType))
      .transform(compressColumn(structType))
      .transform(replaceFieldNameByColumnName(structType))
  }

  /**
   * {{{
   *    import com.jcdecaux.setl.annotations.ColumnName
   *
   *    case class MyObject(@ColumnName("col1") column1: String, column2: String)
   *
   *    convert
   *    +----+-------+
   *    |col1|column2|
   *    +----+-------+
   *    |   a|      A|
   *    |   b|      B|
   *    +----+-------+
   *
   *    to
   *    +-------+-------+
   *    |column1|column2|
   *    +-------+-------+
   *    |      a|      A|
   *    |      b|      B|
   *    +-------+-------+
   *
   * }}}
   *
   * @param structType StrutType containing metadata of column name
   * @param dataFrame  the raw DataFrame loaded from a data persistence store
   * @return a new DataFrame with renamed columns
   */
  def replaceDFColNameByFieldName(structType: StructType)(dataFrame: DataFrame): DataFrame = {
    val changes = structType
      .filter(_.metadata.contains(COLUMN_NAME))
      .map(x => x.metadata.getStringArray(COLUMN_NAME)(0) -> x.name)
      .toMap

    dataFrame.transform(renameColumnsOfDataFrame(changes))
  }

  /**
   * {{{
   *    import com.jcdecaux.setl.annotations.ColumnName
   *
   *    case class MyObject(@ColumnName("col1") column1: String, column2: String)
   *
   *    convert
   *    +-------+-------+
   *    |column1|column2|
   *    +-------+-------+
   *    |      a|      A|
   *    |      b|      B|
   *    +-------+-------+
   *
   *    to
   *    +----+-------+
   *    |col1|column2|
   *    +----+-------+
   *    |   a|      A|
   *    |   b|      B|
   *    +----+-------+
   * }}}
   *
   * @param structType StrutType containing metadata of column name
   * @param dataFrame  the DataFrame to be saved into a data persistence store
   * @return
   */
  def replaceFieldNameByColumnName(structType: StructType)(dataFrame: DataFrame): DataFrame = {
    val changes = structType
      .filter(_.metadata.contains(COLUMN_NAME))
      .map(x => x.name -> x.metadata.getStringArray(COLUMN_NAME)(0))
      .toMap

    dataFrame.transform(renameColumnsOfDataFrame(changes))
  }

  private[this] def renameColumnsOfDataFrame(mapping: Map[String, String])
                                            (dataFrame: DataFrame): DataFrame = {
    mapping.foldLeft(dataFrame) {
      (df, change) => df.withColumnRenamed(change._1, change._2)
    }
  }

  /**
   * Drop all compound key columns
   */
  def dropCompoundKeyColumns(structType: StructType)(dataFrame: DataFrame): DataFrame = {
    val columnsToDrop = structType
      .filter(_.metadata.contains(COMPOUND_KEY))
      .map(_.metadata.getStringArray(COMPOUND_KEY)(0))
      .toSet

    if (columnsToDrop.nonEmpty && dataFrame.columns.intersect(columnsToDrop.toSeq.map(compoundKeyName)).isEmpty) {
      log.warn("Some compound key columns are missing in the data source")
    }

    columnsToDrop
      .foldLeft(dataFrame)((df, col) => df.drop(compoundKeyName(col)))
  }

  /**
   * {{{
   *   import com.jcdecaux.setl.annotations.CombinedKey
   *   case class MyObject(@CombinedKey("primary", "2") column1: String,
   *                       @CombinedKey("primary", "1") column2: String)
   *
   *   from
   *   +-------+-------+
   *   |column1|column2|
   *   +-------+-------+
   *   |      a|      A|
   *   |      b|      B|
   *   +-------+-------+
   *
   *   create
   *   +-------+-------+------------+
   *   |column1|column2|_primary_key|
   *   +-------+-------+------------+
   *   |      a|      A|         A-a|
   *   |      b|      B|         B-b|
   *   +-------+-------+------------+
   * }}}
   *
   * @param structType structType containing the meta-information of the source DataFrame
   * @param dataFrame  the DataFrame to be saved into a data persistence store
   * @return
   */
  private[this] def addCompoundKeyColumns(structType: StructType)(dataFrame: DataFrame): DataFrame = {
    val keyColumns = structType
      .filter(_.metadata.contains(COMPOUND_KEY))
      .flatMap {
        structField =>
          structField.metadata
            .getStringArray(COMPOUND_KEY)
            .map {
              data =>
                val compoundKey = CompoundKey.deserialize(data)
                (structField.name, compoundKey)
            }
      }
      .groupBy(_._2.id)
      .map {
        case (key, fields) =>
          val sortedColumns = fields.sortBy(_._2.position).map {
            case (colname, _) => functions.col(colname)
          }

          (key, sortedColumns)
      }.toList.sortBy(_._1)

    // For each element in keyColumns, add a new column to the input dataFrame
    keyColumns
      .foldLeft(dataFrame)(
        (df, col) => df.withColumn(compoundKeyName(col._1), compoundKeyColumn(col._2))
      )
  }

  /**
   * For column having the annotation @Compress(compressor), compress the column with the given compressor
   *
   * @param structType structType containing the meta-information of the source DataFrame
   * @param dataFrame  DataFrame to be compressed
   * @return a new DataFrame with compressed column(s)
   */
  def compressColumn(structType: StructType)(dataFrame: DataFrame): DataFrame = {

    val columnToCompress = structType.filter(_.metadata.contains(COMPRESS))

    columnToCompress
      .foldLeft(dataFrame) {
        (df, sf) =>
          val compressorName = sf.metadata.getStringArray(COMPRESS).head
          val compressor = Class.forName(compressorName).newInstance().asInstanceOf[Compressor]
          val compress: String => Array[Byte] = input => compressor.compress(input)
          val compressUDF = functions.udf(compress)
          df.withColumn(sf.name, compressUDF(functions.to_json(functions.col(sf.name))))
      }
  }

  /**
   * Decompress a DataFrame having compressed column(s)
   *
   * @param structType structType containing the meta-information of the target DataFrame
   * @param dataFrame  DataFrame to be decompressed
   * @return a DataFrame with column(s) decompressed
   */
  def decompressColumn(structType: StructType)(dataFrame: DataFrame): DataFrame = {

    val columnToDecompress = structType.filter(_.metadata.contains(COMPRESS))

    columnToDecompress
      .foldLeft(dataFrame) {
        (df, sf) => {
          val compressorName = sf.metadata.getStringArray(COMPRESS).head
          val compressor = Class.forName(compressorName).newInstance().asInstanceOf[Compressor]
          val decompress: Array[Byte] => String = input => compressor.decompress(input)
          val decompressUDF = functions.udf(decompress)
          df.withColumn(sf.name, functions.from_json(decompressUDF(functions.col(sf.name)), sf.dataType))
        }
      }
  }
}
