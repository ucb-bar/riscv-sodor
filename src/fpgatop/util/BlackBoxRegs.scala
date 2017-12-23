// See LICENSE.SiFive for license details.

package util

import Chisel._

import config._

/** This black-boxes an Async Reset
  *  (or Set)
  * Register.
  *  
  * Because Chisel doesn't support
  * parameterized black boxes, 
  * we unfortunately have to 
  * instantiate a number of these.
  *  
  *  We also have to hard-code the set/
  *  reset behavior.
  *  
  *  Do not confuse an asynchronous
  *  reset signal with an asynchronously
  *  reset reg. You should still 
  *  properly synchronize your reset 
  *  deassertion.
  *  
  *  @param d Data input
  *  @param q Data Output
  *  @param clk Clock Input
  *  @param rst Reset Input
  *  @param en Write Enable Input
  *  
  */

class AsyncResetReg extends BlackBox {
  val io = new Bundle {
    val d = Bool(INPUT)
    val q = Bool(OUTPUT)
    val en = Bool(INPUT)

    val clk = Clock(INPUT)
    val rst = Bool(INPUT)
  }
}

class SimpleRegIO(val w: Int) extends Bundle{
  val d = UInt(INPUT, width = w)
  val q = UInt(OUTPUT, width = w)
  val en = Bool(INPUT)
}

class AsyncResetRegVec(val w: Int, val init: BigInt) extends Module {
  val io = new SimpleRegIO(w)

  val async_regs = List.fill(w)(Module(new AsyncResetReg))

  val q = for ((reg, idx) <- async_regs.zipWithIndex) yield {
    def maybeInvert(x: Bool) = if (((init >> idx) & 1) == 1) !x else x
    reg.io.clk := clock
    reg.io.rst := reset
    reg.io.d   := maybeInvert(io.d(idx))
    reg.io.en  := io.en
    reg.suggestName(s"reg_$idx")
    maybeInvert(reg.io.q)
  }

  io.q := q.asUInt
}

object AsyncResetReg {
  // Create Single Registers
  def apply(d: Bool, clk: Clock, rst: Bool, init: Boolean, name: Option[String]): Bool = {
    def maybeInvert(x: Bool) = if (init) !x else x
    val reg = Module(new AsyncResetReg)
    reg.io.d := maybeInvert(d)
    reg.io.clk := clk
    reg.io.rst := rst
    reg.io.en  := Bool(true)
    name.foreach(reg.suggestName(_))
    maybeInvert(reg.io.q)
  }

  def apply(d: Bool, clk: Clock, rst: Bool): Bool = apply(d, clk, rst, false, None)
  def apply(d: Bool, clk: Clock, rst: Bool, name: String): Bool = apply(d, clk, rst, false, Some(name))

  // Create Vectors of Registers
  def apply(updateData: UInt, resetData: BigInt, enable: Bool, name: Option[String] = None): UInt = {
    val w = updateData.getWidth max resetData.bitLength
    val reg = Module(new AsyncResetRegVec(w, resetData))
    name.foreach(reg.suggestName(_))
    reg.io.d := updateData
    reg.io.en := enable
    reg.io.q
  }
  def apply(updateData: UInt, resetData: BigInt, enable: Bool, name: String): UInt = apply(updateData,
    resetData, enable, Some(name))


  def apply(updateData: UInt, resetData: BigInt): UInt = apply(updateData, resetData, enable=Bool(true))
  def apply(updateData: UInt, resetData: BigInt, name: String): UInt = apply(updateData, resetData, enable=Bool(true), Some(name))

  def apply(updateData: UInt, enable: Bool): UInt = apply(updateData, resetData=BigInt(0), enable)
  def apply(updateData: UInt, enable: Bool, name: String): UInt = apply(updateData, resetData=BigInt(0), enable, Some(name))

  def apply(updateData: UInt): UInt = apply(updateData, resetData=BigInt(0), enable=Bool(true))
  def apply(updateData: UInt, name:String): UInt = apply(updateData, resetData=BigInt(0), enable=Bool(true), Some(name))
}
