//**************************************************************************
// RISCV Processor
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 Jul 30
//
// Describes a simple RISCV 2-stage processor
//   - Statically predict pc+4, kill instruction fetch
//   - Single-cycle memory
//   - No div/mul/rem
//   - No FPU
//   - No double-word nor sub-word memory access support

package sodor.stage2

import chisel3._
import chisel3.util._

import sodor.common._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.CoreInterrupts

class CoreIo(implicit val p: Parameters, val conf: SodorCoreParams) extends Bundle
{
  val imem = new MemPortIo(conf.xprlen)
  val dmem = new MemPortIo(conf.xprlen)
  val ddpath = Flipped(new DebugDPath())
  val dcpath = Flipped(new DebugCPath())
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

  io.imem <> c.io.imem
  io.imem <> d.io.imem

  io.dmem <> c.io.dmem
  io.dmem <> d.io.dmem
  io.dmem.req.valid := c.io.dmem.req.valid
  io.dmem.req.bits.typ := c.io.dmem.req.bits.typ
  io.dmem.req.bits.fcn := c.io.dmem.req.bits.fcn

  d.io.ddpath <> io.ddpath
  c.io.dcpath <> io.dcpath

  d.io.interrupt := io.interrupt
  d.io.hartid := io.hartid
  d.io.reset_vector := io.reset_vector

  val mem_ports = List(io.dmem, io.imem)
  val interrupt = io.interrupt
  val hartid = io.hartid
  val reset_vector = io.reset_vector
}
