package com.jcdecaux.setl.workflow

import com.jcdecaux.setl.BenchmarkResult
import com.jcdecaux.setl.annotation.{Benchmark, InterfaceStability}
import com.jcdecaux.setl.exception.AlreadyExistsException
import com.jcdecaux.setl.internal._
import com.jcdecaux.setl.transformation.{AbstractFactory, Deliverable, Factory}

import scala.collection.mutable.ArrayBuffer
import scala.collection.parallel.mutable.ParArray
import scala.reflect.ClassTag

/**
 * A Stage is a collection of independent Factories. All the stages of a pipeline will be executed
 * sequentially at runtime. Within a stage, all factories could be executed in parallel or in sequential order.
 */
@InterfaceStability.Evolving
class Stage extends Logging
  with Identifiable
  with HasRegistry[Factory[_]]
  with HasDescription
  with HasBenchmark
  with Writable {

  this._benchmark = Some(true)
  private[this] var _optimization: Boolean = false
  private[this] var _end: Boolean = true
  private[this] var _parallel: Boolean = true
  private[this] var _stageId: Int = _
  private[this] var _deliverable: Array[Deliverable[_]] = _
  private[this] val _benchmarkResult: ArrayBuffer[BenchmarkResult] = ArrayBuffer.empty

  private[workflow] def end: Boolean = _end

  private[workflow] def end_=(value: Boolean): Unit = _end = value

  private[workflow] def start: Boolean = if (stageId == 0) true else false

  private[workflow] def stageId: Int = _stageId

  private[workflow] def setStageId(id: Int): this.type = {
    _stageId = id
    this
  }

  /** Return all the factories of this stage */
  def factories: Array[Factory[_]] = this.getRegistry.values.toArray

  /** Return all the deliverable of this stage */
  def deliverable: Array[Deliverable[_]] = this._deliverable

  /** True if factories of this stage will be executed in parallel */
  def parallel: Boolean = _parallel

  /**
   * Alias of writable
   *
   * @param persistence if set to true, then the write method of the factory will be invoked
   * @return
   */
  @deprecated("To avoid misunderstanding, use writable()", "0.4.0")
  def persist(persistence: Boolean): this.type = this.writable(persistence)

  /** Return true if the write method will be invoked by the pipeline */
  @deprecated("To avoid misunderstanding, use writable", "0.4.0")
  def persist: Boolean = writable

  /**
   * Set to true to run all factories of this stage in parallel. Otherwise they will be executed in sequential order
   *
   * @param boo true for parallel. otherwise false
   * @return
   */
  def parallel(boo: Boolean): this.type = {
    _parallel = boo
    this
  }

  /** Return true if the pipeline execution will be optimized by the given optimizer */
  def optimization: Boolean = this._optimization

  /**
   * Set to true to allow the PipelineOptimizer to optimize the execution order of factories within the stage. Default
   * false
   *
   * @param boo true to allow optimization
   * @return this stage
   */
  def optimization(boo: Boolean): this.type = {
    _optimization = boo
    this
  }

  /**
   * Instantiate a factory with its class and its constructor arguments
   *
   * @param cls             class of the factory to be instantiated
   * @param constructorArgs arguments of the factory's primary constructor
   * @return an object of type Factory[_]
   */
  private[this] def instantiateFactory(cls: Class[_ <: Factory[_]],
                                       constructorArgs: Array[Object]): Factory[_] = {
    val primaryConstructor = cls.getConstructors.head

    val newFactory = if (primaryConstructor.getParameterCount == 0) {
      primaryConstructor.newInstance()
    } else {
      primaryConstructor.newInstance(constructorArgs: _*)
    }

    newFactory.asInstanceOf[Factory[_]]
  }

  /**
   * Add a new factory by providing its class and the constructor arguments
   *
   * @param factory         class of the factory to be added
   * @param constructorArgs arguments of the primary constructor of the factory
   * @throws AlreadyExistsException if the factory to be added exists already, this exception will be thrown
   * @return this stage with the added factory
   */
  @throws[IllegalArgumentException](
    "Exception will be thrown if the length of constructor arguments are not correct"
  )
  def addFactory(factory: Class[_ <: Factory[_]],
                 constructorArgs: Object*): this.type = {
    addFactory(instantiateFactory(factory, constructorArgs.toArray))
  }

  /**
   * Add a new factory by providing its class and the constructor arguments
   *
   * @param constructorArgs arguments of the primary constructor of the factory
   * @param writable        should the `write` method of the factory be invoked by the pipeline?
   * @tparam T class of the factory to be instantiated
   * @throws AlreadyExistsException if the factory to be added exists already, this exception will be thrown
   * @return this stage with the added factory
   */
  @throws[IllegalArgumentException](
    "Exception will be thrown if the length of constructor arguments are not correct"
  )
  def addFactory[T <: Factory[_] : ClassTag](constructorArgs: Array[Object] = Array.empty,
                                             writable: Boolean = true): this.type = {
    val cls = implicitly[ClassTag[T]].runtimeClass.asInstanceOf[Class[T]]
    addFactory(instantiateFactory(cls, constructorArgs).writable(writable))
  }

  /**
   * Add a new factory to this stage
   *
   * @param factory a factory object
   * @throws AlreadyExistsException if the factory to be added exists already, this exception will be thrown
   * @return this stage with the added factory
   */
  @throws[AlreadyExistsException]
  def addFactory(factory: Factory[_]): this.type = {
    registerNewItem(factory)
    this
  }

  /** Describe the current stage */
  override def describe(): this.type = {
    log.info(s"Stage $stageId contains ${getRegistryLength} factories")
    factories.foreach(_.describe())
    this
  }

  /** Execute the stage */
  def run(): this.type = {
    _deliverable = parallelFactories match {
      case Left(par) =>
        log.debug(s"Stage $stageId will be run in parallel mode")
        par.map(runFactory).toArray

      case Right(nonpar) =>
        log.debug(s"Stage $stageId will be run in sequential mode")
        nonpar.map(runFactory)
    }
    this
  }

  /**
   * Execute a factory with the benchmarking. <br>
   *
   * This method doesn't return the deliverable of factory. It just invokes the read, process and write method
   * of the factory. To retrieve the result
   *
   * @param factory The factory to be executed.
   * @return the benchmark result of the factory
   */
  private[this] def handleBenchmark(factory: Factory[_]): BenchmarkResult = {
    val factoryName = factory.getClass.getSimpleName

    val benchmarkInvocationHandler = new BenchmarkInvocationHandler(factory)

    log.info(s"Start benchmarking $factoryName")
    val start = System.nanoTime()

    // Create the factory proxy
    val proxyFactory = java.lang.reflect.Proxy
      .newProxyInstance(
        getClass.getClassLoader,
        Array(classOf[AbstractFactory[_]]),
        benchmarkInvocationHandler
      )
      .asInstanceOf[AbstractFactory[_]]

    proxyFactory.read()
    proxyFactory.process()

    if (shouldWrite(factory)) {
      log.debug(s"Persist output of ${factory.getPrettyName}")
      proxyFactory.write()
    }

    val elapsed = (System.nanoTime() - start) / 1000000000.0
    log.info(s"Execution of $factoryName finished in $elapsed s")

    val result = benchmarkInvocationHandler.getBenchmarkResult

    BenchmarkResult(
      factory.getClass.getSimpleName,
      result.getOrDefault("read", 0.0),
      result.getOrDefault("process", 0.0),
      result.getOrDefault("write", 0.0),
      result.getOrDefault("get", 0.0),
      elapsed
    )
  }

  /** Execute a factory and return the deliverable of this factory */
  private[this] val runFactory: Factory[_] => Deliverable[_] = {
    factory: Factory[_] =>

      if (this.benchmark.getOrElse(false) && factory.getClass.isAnnotationPresent(classOf[Benchmark])) {

        // Benchmark the factory
        val factoryBench = handleBenchmark(factory)
        _benchmarkResult.append(factoryBench)

      } else {

        // Without benchmarking
        factory.read().process()
        if (shouldWrite(factory)) {
          log.debug(s"Persist output of ${factory.getPrettyName}")
          factory.write()
        }

      }

      factory.getDelivery
  }

  /** Return true if both this stage and the factory are writable, otherwise false */
  private[this] val shouldWrite: Factory[_] => Boolean = factory => {
    this.writable && factory.writable
  }

  /** According to the parallel setting of this stage, return either a ParArray or an Array of factories */
  private[this] def parallelFactories: Either[ParArray[Factory[_]], Array[Factory[_]]] = {
    if (_parallel) {
      Left(factories.par)
    } else {
      Right(factories)
    }
  }

  /** Return an array of Node representing the factories of this stage */
  private[workflow] def createNodes(): Array[Node] = {
    factories.map { fac =>
      new Node(factory = fac, this.stageId, end)
    }
  }

  /**
   * Get the aggregated benchmark result.
   *
   * @return an array of BenchmarkResult
   */
  override def getBenchmarkResult: Array[BenchmarkResult] =
    _benchmarkResult.toArray

}
