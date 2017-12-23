// See LICENSE.Berkeley for license details.
// See LICENSE.SiFive for license details.

package util

import Chisel._
import config._
import scala.math._

class ParameterizedBundle(implicit p: Parameters) extends Bundle {
  override def cloneType = {
    try {
      this.getClass.getConstructors.head.newInstance(p).asInstanceOf[this.type]
    } catch {
      case e: java.lang.IllegalArgumentException =>
        throwException("Unable to use ParamaterizedBundle.cloneType on " +
                       this.getClass + ", probably because " + this.getClass +
                       "() takes more than one argument.  Consider overriding " +
                       "cloneType() on " + this.getClass, e)
    }
  }
}

object DecoupledHelper {
  def apply(rvs: Bool*) = new DecoupledHelper(rvs)
}

class DecoupledHelper(val rvs: Seq[Bool]) {
  def fire(exclude: Bool, includes: Bool*) = {
    (rvs.filter(_ ne exclude) ++ includes).reduce(_ && _)
  }
}

object MuxT {
  def apply[T <: Data, U <: Data](cond: Bool, con: (T, U), alt: (T, U)): (T, U) =
    (Mux(cond, con._1, alt._1), Mux(cond, con._2, alt._2))

  def apply[T <: Data, U <: Data, W <: Data](cond: Bool, con: (T, U, W), alt: (T, U, W)): (T, U, W) =
    (Mux(cond, con._1, alt._1), Mux(cond, con._2, alt._2), Mux(cond, con._3, alt._3))

  def apply[T <: Data, U <: Data, W <: Data, X <: Data](cond: Bool, con: (T, U, W, X), alt: (T, U, W, X)): (T, U, W, X) =
    (Mux(cond, con._1, alt._1), Mux(cond, con._2, alt._2), Mux(cond, con._3, alt._3), Mux(cond, con._4, alt._4))
}

/** Creates a cascade of n MuxTs to search for a key value. */
object MuxTLookup {
  def apply[S <: UInt, T <: Data, U <: Data](key: S, default: (T, U), mapping: Seq[(S, (T, U))]): (T, U) = {
    var res = default
    for ((k, v) <- mapping.reverse)
      res = MuxT(k === key, v, res)
    res
  }

  def apply[S <: UInt, T <: Data, U <: Data, W <: Data](key: S, default: (T, U, W), mapping: Seq[(S, (T, U, W))]): (T, U, W) = {
    var res = default
    for ((k, v) <- mapping.reverse)
      res = MuxT(k === key, v, res)
    res
  }
}

object Str
{
  def apply(s: String): UInt = {
    var i = BigInt(0)
    require(s.forall(validChar _))
    for (c <- s)
      i = (i << 8) | c
    UInt(i, s.length*8)
  }
  def apply(x: Char): UInt = {
    require(validChar(x))
    UInt(x.toInt, 8)
  }
  def apply(x: UInt): UInt = apply(x, 10)
  def apply(x: UInt, radix: Int): UInt = {
    val rad = UInt(radix)
    val w = x.getWidth
    require(w > 0)

    var q = x
    var s = digit(q % rad)
    for (i <- 1 until ceil(log(2)/log(radix)*w).toInt) {
      q = q / rad
      s = Cat(Mux(Bool(radix == 10) && q === UInt(0), Str(' '), digit(q % rad)), s)
    }
    s
  }
  def apply(x: SInt): UInt = apply(x, 10)
  def apply(x: SInt, radix: Int): UInt = {
    val neg = x < SInt(0)
    val abs = x.abs.asUInt
    if (radix != 10) {
      Cat(Mux(neg, Str('-'), Str(' ')), Str(abs, radix))
    } else {
      val rad = UInt(radix)
      val w = abs.getWidth
      require(w > 0)

      var q = abs
      var s = digit(q % rad)
      var needSign = neg
      for (i <- 1 until ceil(log(2)/log(radix)*w).toInt) {
        q = q / rad
        val placeSpace = q === UInt(0)
        val space = Mux(needSign, Str('-'), Str(' '))
        needSign = needSign && !placeSpace
        s = Cat(Mux(placeSpace, space, digit(q % rad)), s)
      }
      Cat(Mux(needSign, Str('-'), Str(' ')), s)
    }
  }

  private def digit(d: UInt): UInt = Mux(d < UInt(10), Str('0')+d, Str(('a'-10).toChar)+d)(7,0)
  private def validChar(x: Char) = x == (x & 0xFF)
}

object Split
{
  // is there a better way to do do this?
  def apply(x: Bits, n0: Int) = {
    val w = checkWidth(x, n0)
    (x(w-1,n0), x(n0-1,0))
  }
  def apply(x: Bits, n1: Int, n0: Int) = {
    val w = checkWidth(x, n1, n0)
    (x(w-1,n1), x(n1-1,n0), x(n0-1,0))
  }
  def apply(x: Bits, n2: Int, n1: Int, n0: Int) = {
    val w = checkWidth(x, n2, n1, n0)
    (x(w-1,n2), x(n2-1,n1), x(n1-1,n0), x(n0-1,0))
  }

  private def checkWidth(x: Bits, n: Int*) = {
    val w = x.getWidth
    def decreasing(x: Seq[Int]): Boolean =
      if (x.tail.isEmpty) true
      else x.head >= x.tail.head && decreasing(x.tail)
    require(decreasing(w :: n.toList))
    w
  }
}

object Random
{
  def apply(mod: Int, random: UInt): UInt = {
    if (isPow2(mod)) random(log2Up(mod)-1,0)
    else PriorityEncoder(partition(apply(1 << log2Up(mod*8), random), mod))
  }
  def apply(mod: Int): UInt = apply(mod, randomizer)
  def oneHot(mod: Int, random: UInt): UInt = {
    if (isPow2(mod)) UIntToOH(random(log2Up(mod)-1,0))
    else PriorityEncoderOH(partition(apply(1 << log2Up(mod*8), random), mod)).asUInt
  }
  def oneHot(mod: Int): UInt = oneHot(mod, randomizer)

  private def randomizer = LFSR16()
  private def round(x: Double): Int =
    if (x.toInt.toDouble == x) x.toInt else (x.toInt + 1) & -2
  private def partition(value: UInt, slices: Int) =
    Seq.tabulate(slices)(i => value < UInt(round((i << value.getWidth).toDouble / slices)))
}

object Majority {
  def apply(in: Set[Bool]): Bool = {
    val n = (in.size >> 1) + 1
    val clauses = in.subsets(n).map(_.reduce(_ && _))
    clauses.reduce(_ || _)
  }

  def apply(in: Seq[Bool]): Bool = apply(in.toSet)

  def apply(in: UInt): Bool = apply(in.toBools.toSet)
}
