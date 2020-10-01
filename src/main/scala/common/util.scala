//**************************************************************************
// RISCV Processor Utility Functions
//--------------------------------------------------------------------------

package sodor.common

import chisel3._
import chisel3.util._
import scala.math._
import scala.collection.mutable.ArrayBuffer

object Util
{
  implicit def intToUInt(x: Int): UInt = x.U
  implicit def intToBoolean(x: Int): Boolean = if (x != 0) true else false
  implicit def booleanToInt(x: Boolean): Int = if (x) 1 else 0
  implicit def booleanToBool(x: Boolean): Bool = x.B
  implicit def sextToConv(x: UInt) = new AnyRef {
    def sextTo(n: Int): UInt = Cat(Fill(n - x.getWidth, x(x.getWidth-1)), x)
  }

  implicit def wcToUInt(c: WideCounter): UInt = c.value
  implicit class UIntIsOneOf(val x: UInt) extends AnyVal {
    def isOneOf(s: Seq[UInt]): Bool = s.map(x === _).reduce(_||_)

    def isOneOf(u1: UInt, u2: UInt*): Bool = isOneOf(u1 +: u2.toSeq)
  }

  implicit class UIntToAugmentedUInt(val x: UInt) extends AnyVal {
    def sextTo(n: Int): UInt = {
      require(x.getWidth <= n)
      if (x.getWidth == n) x
      else Cat(Fill(n - x.getWidth, x(x.getWidth-1)), x)
    }

    def padTo(n: Int): UInt = {
      require(x.getWidth <= n)
      if (x.getWidth == n) x
      else Cat(0.U((n - x.getWidth).W), x)
    }

    def extract(hi: Int, lo: Int): UInt = {
      if (hi == lo-1) 0.U
      else x(hi, lo)
    }

    def inRange(base: UInt, bounds: UInt) = x >= base && x < bounds
  }
}


//do two masks have at least 1 bit match?
object maskMatch
{
   def apply(msk1: UInt, msk2: UInt): Bool =
   {
      val br_match = (msk1 & msk2) =/= 0.U
      return br_match
   }
}

//clear one-bit in the Mask as specified by the idx
object clearMaskBit
{
   def apply(msk: UInt, idx: UInt): UInt =
   {
      return (msk & ~(1.U << idx))(msk.getWidth-1, 0)
   }
}

//shift a register over by one bit
object PerformShiftRegister
{
   def apply(reg_val: Bits, new_bit: Bool): Bits =
   {
      reg_val := Cat(reg_val(reg_val.getWidth-1, 0).asUInt(), new_bit.asUInt()).asUInt()
      reg_val
   }
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
      else x.head > x.tail.head && decreasing(x.tail)
    require(decreasing(w :: n.toList))
    w
  }
}


// a counter that clock gates most of its MSBs using the LSB carry-out
case class WideCounter(width: Int, inc: UInt = 1.U, reset: Boolean = true)
{
  private val isWide = width > 2*inc.getWidth
  private val smallWidth = if (isWide) inc.getWidth max log2Ceil(width) else width
  private val small = if (reset) RegInit(0.asUInt(smallWidth.W)) else Reg(UInt(smallWidth.W))
  private val nextSmall = small +& inc
  small := nextSmall

  private val large = if (isWide) {
    val r = if (reset) RegInit(0.asUInt((width - smallWidth).W)) else Reg(UInt((width - smallWidth).W))
    when (nextSmall(smallWidth)) { r := r + 1.U }
    r
  } else null

  val value = if (isWide) Cat(large, small) else small
  lazy val carryOut = {
    val lo = (small ^ nextSmall) >> 1
    if (!isWide) lo else {
      val hi = Mux(nextSmall(smallWidth), large ^ (large +& 1.U), 0.U) >> 1
      Cat(hi, lo)
    }
  }

  def := (x: UInt) = {
    small := x
    if (isWide) large := x >> smallWidth
  }
}

// taken from rocket FPU
object RegEn
{
   def apply[T <: Data](data: T, en: Bool) =
   {
      val r = Reg(data)
      when (en) { r := data }
      r
   }
   def apply[T <: Bits](data: T, en: Bool, resetVal: T) =
   {
      val r = RegInit(resetVal)
      when (en) { r := data }
      r
   }
}

object Str
{
  def apply(s: String): UInt = {
    var i = BigInt(0)
    require(s.forall(validChar _))
    for (c <- s)
      i = (i << 8) | c
    i.asUInt((s.length*8).W)
  }
  def apply(x: Char): Bits = {
    require(validChar(x))
    val lit = x.asUInt(8.W)
    lit
  }
  def apply(x: UInt): Bits = apply(x, 10)
  def apply(x: UInt, radix: Int): Bits = {
    val rad = radix.U
    val digs = digits(radix)
    val w = x.getWidth
    require(w > 0)

    var q = x
    var s = digs(q % rad)
    for (i <- 1 until ceil(log(2)/log(radix)*w).toInt) {
      q = q / rad
      s = Cat(Mux((radix == 10).B && q === 0.U, Str(' '), digs(q % rad)), s)
    }
    s
  }
  def apply(x: SInt): Bits = apply(x, 10)
  def apply(x: SInt, radix: Int): Bits = {
    val neg = x < 0.S
    val abs = Mux(neg, -x, x).asUInt()
    if (radix != 10) {
      Cat(Mux(neg, Str('-'), Str(' ')), Str(abs, radix))
    } else {
      val rad = radix.U
      val digs = digits(radix)
      val w = abs.getWidth
      require(w > 0)

      var q = abs
      var s = digs(q % rad)
      var needSign = neg
      for (i <- 1 until ceil(log(2)/log(radix)*w).toInt) {
        q = q / rad
        val placeSpace = q === 0.U
        val space = Mux(needSign, Str('-'), Str(' '))
        needSign = needSign && !placeSpace
        s = Cat(Mux(placeSpace, space, digs(q % rad)), s)
      }
      Cat(Mux(needSign, Str('-'), Str(' ')), s)
    }
  }

  def bigIntToString(x: BigInt): String = {
    val s = new StringBuilder
    var b = x
    while (b != 0) {
      s += (x & 0xFF).toChar
      b = b >> 8
    }
    s.toString
  }

  private def digit(d: Int): Char = (if (d < 10) '0'+d else 'a'-10+d).toChar
  private def digits(radix: Int): Vec[Bits] =
    VecInit((0 until radix).map(i => Str(digit(i))))

  private def validChar(x: Char) = x == (x & 0xFF)
}
