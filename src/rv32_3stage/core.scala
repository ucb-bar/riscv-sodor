//**************************************************************************
// RISCV Processor 
//--------------------------------------------------------------------------

package RV32_3stage

import chisel3._
import config._
import Common._

class CoreIo(implicit p: Parameters) extends Bundle 
{
  val ddpath = Flipped(new DebugDPath())
  val dcpath = Flipped(new DebugCPath())
  val imem = new MemPortIo(p(xprlen))
  val dmem = new MemPortIo(p(xprlen))
}

class Core(implicit p: Parameters) extends Module
{
   val io = IO(new CoreIo())

   val frontend = Module(new FrontEnd())
   val cpath  = Module(new CtlPath())
   val dpath  = Module(new DatPath())

   frontend.io.imem <> io.imem
   frontend.io.cpu <> cpath.io.imem
   frontend.io.cpu <> dpath.io.imem
   frontend.io.cpu.req.valid := cpath.io.imem.req.valid

   cpath.io.ctl  <> dpath.io.ctl
   cpath.io.dat  <> dpath.io.dat
   
   cpath.io.dmem <> io.dmem
   dpath.io.dmem <> io.dmem
   
   dpath.io.ddpath <> io.ddpath
   cpath.io.dcpath <> io.dcpath
}


