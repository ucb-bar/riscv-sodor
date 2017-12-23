package util

import Chisel._
import chisel3.core.Record
import scala.collection.immutable.ListMap

final case class HeterogeneousBag[T <: Data](elts: Seq[T]) extends Record with collection.IndexedSeq[T] {
  def apply(x: Int) = elts(x)
  def length = elts.length

  val elements = ListMap(elts.zipWithIndex.map { case (n,i) => (i.toString, n) }:_*)
  override def cloneType: this.type = (new HeterogeneousBag(elts.map(_.cloneType))).asInstanceOf[this.type]
}
