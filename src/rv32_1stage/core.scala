//**************************************************************************
// RISCV Processor 
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 Jul 30
//
// Describes a simple RISCV 1-stage processor
//   - No div/mul/rem
//   - No FPU
//   - implements a minimal supervisor mode (can trap to handle the
//       above instructions)
//
// The goal of the 1-stage is to provide the simpliest, easiest-to-read code to
// demonstrate the RISC-V ISA.
 
package Sodor
{

import chisel3._
import Common._
import config._

class CoreIo(implicit p: Parameters) extends Bundle 
{
  val imem = new MemPortIo(p(xprlen))
  val dmem = new MemPortIo(p(xprlen))
  val ddpath = Flipped(new DebugDPath())
  val dcpath = Flipped(new DebugCPath())
}

class Core(implicit p: Parameters) extends Module
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

}

}
