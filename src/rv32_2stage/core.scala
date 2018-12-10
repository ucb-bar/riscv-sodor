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

package Sodor
{

import chisel3._
import chisel3.util._

import Common._

class CoreIo(implicit val conf: SodorConfiguration) extends Bundle
{
  val imem = new MemPortIo(conf.xprlen)
  val dmem = new MemPortIo(conf.xprlen)
  val ddpath = Flipped(new DebugDPath())
  val dcpath = Flipped(new DebugCPath())
}

class Core(implicit val conf: SodorConfiguration) extends Module
{
  val io = IO(new CoreIo())
  val c  = Module(new CtlPath())
  val d  = Module(new DatPath())

  c.io.ctl  <> d.io.ctl
  c.io.dat  <> d.io.dat

  io.imem <> c.io.imem
  io.imem <> d.io.imem
  io.imem.req.valid := c.io.imem.req.valid

  io.dmem <> c.io.dmem
  io.dmem <> d.io.dmem
  io.dmem.req.valid := c.io.dmem.req.valid
  io.dmem.req.bits.typ := c.io.dmem.req.bits.typ
  io.dmem.req.bits.fcn := c.io.dmem.req.bits.fcn

  d.io.ddpath <> io.ddpath
  c.io.dcpath <> io.dcpath
}

}
