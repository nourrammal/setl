package com.jcdecaux.setl

import com.datastax.spark.connector.cql.CassandraConnector
import org.apache.spark.SparkConf
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.cassandra._
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.{BeforeAndAfterAll, SequentialNestedSuiteExecution}


class SparkSessionBuilderSuite extends AnyFunSuite with BeforeAndAfterAll with SequentialNestedSuiteExecution {

  val connector: CassandraConnector = CassandraConnector(MockCassandra.cassandraConf)

  override def beforeAll(): Unit = {
    super.beforeAll()
    new MockCassandra(connector, MockCassandra.keyspace)
      .dropKeyspace()
      .generateKeyspace()
      .generateCountry("countries")
  }

  test("SparkSessionBuilder default instantiation") {
    val sparkSessionBuilder = new SparkSessionBuilder()
    assert(sparkSessionBuilder.appEnv === "local")
    assert(sparkSessionBuilder.appName === "SparkApplication")
    assert(sparkSessionBuilder.cassandraHost === null)
    assert(sparkSessionBuilder.sparkConf.getClass === classOf[SparkConf])
    assert(sparkSessionBuilder.initialization === true)
    assert(sparkSessionBuilder.spark === null)
    sparkSessionBuilder.build()
    assert(sparkSessionBuilder.spark != null)
    assert(sparkSessionBuilder.sparkMasterUrl === "local[*]")
  }

  test("setSparkMaster method should override master url when env is local") {
    // default local spark master url
    val builder = new SparkSessionBuilder().setEnv("local").build()
    assert(builder.sparkMasterUrl === "local[*]")

    // override default url
    val builder2 = new SparkSessionBuilder().setEnv("local").setSparkMaster("local").build()
    assert(builder2.sparkMasterUrl === "local")
  }

  test("Cassandra connection") {
    val spark: SparkSession = new SparkSessionBuilder("cassandra")
      .withSparkConf(MockCassandra.cassandraConf)
      .setEnv("local")
      .build()
      .get()

    assert(spark.read.cassandraFormat("countries", MockCassandra.keyspace).load().count() === 20)
  }

  test("Custom configuration") {

    val sparkConf = new SparkConf(true)
      .set("spark.cassandra.connection.port", "9042")
      .set("spark.cassandra.connection.keep_alive_ms", "5000")
      .set("spark.cassandra.connection.timeout_ms", "30000")
      .set("spark.ui.showConsoleProgress", "false")
      .set("spark.ui.enabled", "false")
      .set("spark.cleaner.ttl", "3600")
      .set("spark.cassandra.connection.host", MockCassandra.host)
      .set("myProperty", "hehehe")
      .setAppName("CustomConfigApp")
      .setMaster("local[*]")

    val spark = new SparkSessionBuilder()
      .withSparkConf(sparkConf)
      .build()
      .get()

    assert(spark.sparkContext.getConf.get("myProperty") === "hehehe")
    assert(spark.read.cassandraFormat("countries", MockCassandra.keyspace).load().count() === 20)

    val builder2 = new SparkSessionBuilder()
    assert(builder2.initialization)

    builder2.configure(sparkConf)
    assert(!builder2.initialization)

    assert(builder2.get("myProperty") === null)
    assert(builder2.get("spark.cleaner.ttl") === null)
    assert(builder2.sparkConf.get("myProperty") === "hehehe")
    assert(builder2.sparkConf.get("spark.ui.enabled") === "false")

  }

  test("SparkSessionBuilder exception thrown") {
    //    assertThrows[UnknownException.Environment](new SparkSessionBuilder().setEnv("hahaha"))
    assertThrows[IllegalArgumentException](new SparkSessionBuilder("cassandra").setEnv("prod").getOrCreate())
  }

  test("SparkSessionBuilder should handle configuration setting") {
    val builder = new SparkSessionBuilder()
      .setEnv("test_env")

    assert(builder.sparkMasterUrl === null)
    builder.setSparkMaster("some_url")
    assert(builder.sparkMasterUrl === "some_url")

    builder.setShufflePartitions(100)
    assert(builder.getShufflePartitions === "100")
    assert(builder.getParallelism === "100")

    builder.setParallelism(500)
    assert(builder.getShufflePartitions === "500")
    assert(builder.getParallelism === "500")

  }

  test("SparkSessionBuilder Kryo") {
    // TODO: kryo is not yet supported
    val builder = new SparkSessionBuilder()
      .setEnv("local")

    assert(!builder.useKryo)
    builder.useKryo(true)
    assert(builder.useKryo)

    builder.registerClass(classOf[String])
    builder.registerClasses(Array(classOf[String], classOf[Setl]))

    val spark = builder.getOrCreate()
    assert(spark.sparkContext.getConf.get("spark.serializer") === "org.apache.spark.serializer.KryoSerializer")
  }


}
