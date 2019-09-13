//**************************************************************************
// RISCV Processor
//--------------------------------------------------------------------------

package Sodor
{

import chisel3._
import Common._

class CoreIo(implicit val conf: SodorConfiguration) extends Bundle
{
   val ddpath = Flipped(new DebugDPath())
   val dcpath = Flipped(new DebugCPath())
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
}

class Core()(implicit val conf: SodorConfiguration) extends Module()
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

   d.io.ddpath <> io.ddpath
   c.io.dcpath <> io.dcpath
}

}
