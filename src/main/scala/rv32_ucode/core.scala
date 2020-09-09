//**************************************************************************
// RISCV Micro-Coded Processor
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 May 28
//
// A rough design spec can be found at:
// http://inst.eecs.berkeley.edu/~cs152/sp11/assignments/ps1/handout1.pdf
//

package sodor.ucode

import chisel3._
import chisel3.util._

import sodor.common._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.CoreInterrupts

class CoreIo(implicit val p: Parameters, val conf: SodorCoreParams) extends Bundle
{
  val ddpath = Flipped(new DebugDPath())
  val dcpath = Flipped(new DebugCPath())
  val mem  = new MemPortIo(conf.xprlen)
  val interrupt = Input(new CoreInterrupts())
  val hartid = Input(UInt())
  val reset_vector = Input(UInt())
}

class Core(implicit val p: Parameters, val conf: SodorCoreParams) extends AbstractCore
{
  val io = IO(new CoreIo())
  val c  = Module(new CtlPath())
  val d  = Module(new DatPath())

  c.io.ctl  <> d.io.ctl
  c.io.dat  <> d.io.dat

  c.io.mem <> io.mem
  d.io.mem <> io.mem
  io.mem.req.valid := c.io.mem.req.valid
  io.mem.req.bits.fcn := c.io.mem.req.bits.fcn
  io.mem.req.bits.typ := c.io.mem.req.bits.typ

  d.io.ddpath <> io.ddpath
  c.io.dcpath <> io.dcpath

  d.io.interrupt := io.interrupt
  d.io.hartid := io.hartid
  d.io.reset_vector := io.reset_vector

  val mem_ports = List(io.mem)
  val interrupt = io.interrupt
  val hartid = io.hartid
  val reset_vector = io.reset_vector
}
