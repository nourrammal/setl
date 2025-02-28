package com.jcdecaux.setl.storage.connector

import java.io.ByteArrayOutputStream

import com.datastax.spark.connector.cql.{CassandraConnector => CC}
import com.jcdecaux.setl.config.{Conf, Properties}
import com.jcdecaux.setl.{MockCassandra, SparkSessionBuilder, TestObject}
import org.apache.log4j.{Logger, SimpleLayout, WriterAppender}
import org.apache.spark.sql.{Dataset, SparkSession}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite

class CassandraConnectorSuite extends AnyFunSuite with BeforeAndAfterAll {


  val connector: CC = CC(MockCassandra.cassandraConf)

  val keyspace = "test_space"

  override def beforeAll(): Unit = {
    super.beforeAll()
    new MockCassandra(connector, "test_space")
      .dropKeyspace()
      .generateKeyspace()
      .generateCountry("countries")
  }

  test("Manipulate cassandra table") {
    val spark: SparkSession = new SparkSessionBuilder("cassandra")
      .withSparkConf(MockCassandra.cassandraConf)
      .setEnv("local")
      .build().get()

    val cqlConnector = new CassandraConnector(
      keyspace = keyspace,
      table = "test_spark_connector",
      spark = spark,
      partitionKeyColumns = Some(Seq("partition1", "partition2")),
      clusteringKeyColumns = Some(Seq("clustering1"))
    )

    import spark.implicits._

    val testTable: Dataset[TestObject] = Seq(
      TestObject(1, "p1", "c1", 1L),
      TestObject(2, "p2", "c2", 2L),
      TestObject(3, "p3", "c3", 3L)
    ).toDS()

    // Create table and write data
    cqlConnector.create(testTable.toDF())
    cqlConnector.write(testTable.toDF())

    // read table
    val readTable = cqlConnector.read()
    readTable.show()
    assert(readTable.count() === 3)

    // delete row
    cqlConnector.delete("partition1 = 1 and partition2 = 'p1'")
    assert(cqlConnector.read().count() === 2)
  }

  test("Test with auxiliary cassandra connector constructor") {

    println(System.getProperty("testtest", "default"))

    val spark: SparkSession = new SparkSessionBuilder("cassandra")
      .withSparkConf(MockCassandra.cassandraConf)
      .setEnv("local")
      .build().get()

    val conf = new Conf()
    conf.set("keyspace", keyspace)
    conf.set("table", "test_spark_connector")
    conf.set("partitionKeyColumns", Some(Seq("partition1", "partition2")).toString)
    conf.set("clusteringKeyColumns", Some(Seq("clustering1")).toString)

    val conf2 = new Conf()
    conf2.set("keyspace", "test_space")
    conf2.set("table", "test_spark_connector")
    conf2.set("partitionKeyColumns", Some(Seq("partition1", "partition2")).toString)

    import spark.implicits._

    val testTable: Dataset[TestObject] = Seq(
      TestObject(1, "p1", "c1", 1L),
      TestObject(2, "p2", "c2", 2L),
      TestObject(3, "p3", "c3", 3L)
    ).toDS()

    val connector = new CassandraConnector(spark, Properties.cassandraConfig)

    assert(connector.partitionKeyColumns === Option(Seq("partition1", "partition2")))
    assert(connector.clusteringKeyColumns === Option(Seq("clustering1")))

    // Create table and write data
    connector.create(testTable.toDF())
    connector.write(testTable.toDF())

    // read table
    val readTable = connector.read()
    readTable.show()
    assert(readTable.count() === 3)

    // delete row
    connector.delete("partition1 = 1 and partition2 = 'p1'")
    assert(connector.read().count() === 2)

    val connector2 = new CassandraConnector(spark, Properties.cassandraConfigWithoutClustering)
    assert(connector2.read().count() === 2)

    val cqlConnector = new CassandraConnector(
      keyspace = keyspace,
      table = "test_spark_connector",
      spark = spark,
      partitionKeyColumns = Some(Seq("partition1", "partition2")),
      clusteringKeyColumns = Some(Seq("clustering1"))
    )
    cqlConnector.create(testTable.toDF())
    cqlConnector.write(testTable.toDF())
    assert(cqlConnector.read().count() === 3)

    val cqlConnector2 = new CassandraConnector(
      keyspace = keyspace,
      table = "test_spark_connector",
      partitionKeyColumns = Some(Seq("partition1", "partition2")),
      clusteringKeyColumns = Some(Seq("clustering1"))
    )
    assert(cqlConnector2.read().count() === 3)

    val cqlConnector3 = new CassandraConnector(conf)
    assert(cqlConnector3.read().count() === 3)

    val cqlConnector4 = new CassandraConnector(conf2)
    assert(cqlConnector4.read().count() === 3)

    val cqlConnector5 = new CassandraConnector(spark, conf2)
    assert(cqlConnector5.read().count() === 3)

    cqlConnector.delete("partition1 = 1 and partition2 = 'p1'")
    cqlConnector2.delete("partition1 = 1 and partition2 = 'p1'")
    cqlConnector3.delete("partition1 = 1 and partition2 = 'p1'")
    cqlConnector4.delete("partition1 = 1 and partition2 = 'p1'")
    cqlConnector5.delete("partition1 = 1 and partition2 = 'p1'")
  }

  test("Write with suffix should have no impact") {
    val spark: SparkSession = new SparkSessionBuilder("cassandra")
      .withSparkConf(MockCassandra.cassandraConf)
      .setEnv("local")
      .build().get()

    import spark.implicits._

    val logger = Logger.getLogger(classOf[CassandraConnector])
    val outContent = new ByteArrayOutputStream()
    val appender = new WriterAppender(new SimpleLayout, outContent)
    logger.addAppender(appender)
    val warnMessage = "Suffix will be ignored in CassandraConnector"

    val testTable: Dataset[TestObject] = Seq(
      TestObject(1, "p1", "c1", 1L),
      TestObject(2, "p2", "c2", 2L),
      TestObject(3, "p3", "c3", 3L)
    ).toDS()


    val connector = new CassandraConnector(spark, Properties.cassandraConfig)
    connector.create(testTable.toDF())
    connector.write(testTable.toDF())

    val connector2 = new CassandraConnector(
      keyspace = keyspace,
      table = "test_spark_connector",
      spark = spark,
      partitionKeyColumns = Some(Seq("partition1", "partition2")),
      clusteringKeyColumns = Some(Seq("clustering1"))
    )
    connector2.create(testTable.toDF(), Some("suffix"))
    assert(outContent.toString.contains(warnMessage))

    outContent.reset()
    connector2.write(testTable.toDF(), Some("suffix"))
    assert(outContent.toString.contains(warnMessage))

    assert(connector.read().count() == connector2.read().count())
  }
}
