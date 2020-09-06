//**************************************************************************
// RISCV Processor
//--------------------------------------------------------------------------

package sodor.stage5

import chisel3._
import sodor.common._

import freechips.rocketchip.tile.CoreInterrupts
import freechips.rocketchip.tile.TileInputConstants

class CoreIo(implicit val conf: SodorConfiguration) extends Bundle
{
   val ddpath = Flipped(new DebugDPath())
   val dcpath = Flipped(new DebugCPath())
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val interrupt = Input(new CoreInterrupts()(conf.p))
   val constants = new TileInputConstants()(conf.p)
}

class Core()(implicit val conf: SodorConfiguration) extends AbstractCore
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

   d.io.ddpath <> io.ddpath
   c.io.dcpath <> io.dcpath

   d.io.interrupt := io.interrupt
   d.io.constants := io.constants

   val mem_ports = List(io.dmem, io.imem)
   val interrupt = io.interrupt
   val constants = io.constants
}
