package com.jcdecaux.setl.internal

import scala.reflect.runtime

trait HasDiagram {

  /** Generate the diagram */
  def toDiagram: String

  /** Get the diagram ID */
  def diagramId: String

  protected def getTypeArgList(tpe: runtime.universe.Type): List[runtime.universe.Symbol] = {
    tpe
      .baseClasses.head
      .asClass
      .primaryConstructor
      .typeSignature
      .paramLists
      .head
  }

  protected def formatDiagramId(prettyName: String,
                                deliveryId: String,
                                suffix: String): String = {
    prettyName.replaceAll("[\\[\\]]", "") + deliveryId.capitalize + suffix
  }

  /** Display the diagram */
  def showDiagram(): Unit = println(toDiagram)

}
