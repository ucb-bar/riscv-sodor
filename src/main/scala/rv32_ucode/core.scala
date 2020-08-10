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

import freechips.rocketchip.tile.CoreInterrupts
import freechips.rocketchip.tile.TileInputConstants

class CoreIo(implicit val conf: SodorConfiguration) extends Bundle
{
  val ddpath = Flipped(new DebugDPath())
  val dcpath = Flipped(new DebugCPath())
  val mem  = new MemPortIo(conf.xprlen)
  val interrupt = Input(new CoreInterrupts()(conf.p))
  val constants = new TileInputConstants()(conf.p)
}

class Core(implicit val conf: SodorConfiguration) extends AbstractCore
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
  d.io.constants := io.constants

  val mem_ports = List(io.mem)
  val interrupt = io.interrupt
  val constants = io.constants
}
