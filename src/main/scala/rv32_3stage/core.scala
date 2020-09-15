//**************************************************************************
// RISCV Processor
//--------------------------------------------------------------------------

package sodor.stage3

import chisel3._
import chisel3.util._

import sodor.common._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.tile.CoreInterrupts

class CoreIo(implicit val p: Parameters, val conf: SodorCoreParams) extends Bundle
{
  val ddpath = Flipped(new DebugDPath())
  val dcpath = Flipped(new DebugCPath())
  val imem = new MemPortIo(conf.xprlen)
  val dmem = new MemPortIo(conf.xprlen)
  val interrupt = Input(new CoreInterrupts())
  val hartid = Input(UInt())
  val reset_vector = Input(UInt())
}

class Core(implicit val p: Parameters, val conf: SodorCoreParams) extends AbstractCore
{
  val io = IO(new CoreIo())

  val frontend = Module(new FrontEnd())
  val cpath  = Module(new CtlPath())
  val dpath  = Module(new DatPath())

  frontend.io.reset_vector := io.reset_vector
  frontend.io.imem <> io.imem
  frontend.io.cpu <> cpath.io.imem
  frontend.io.cpu <> dpath.io.imem
  frontend.io.cpu.req.valid := cpath.io.imem.req.valid
  frontend.io.cpu.exe_kill := cpath.io.imem.exe_kill

  cpath.io.ctl  <> dpath.io.ctl
  cpath.io.dat  <> dpath.io.dat

  cpath.io.dmem <> io.dmem
  dpath.io.dmem <> io.dmem

  dpath.io.ddpath <> io.ddpath
  cpath.io.dcpath <> io.dcpath

  dpath.io.interrupt := io.interrupt
  dpath.io.hartid := io.hartid

  val mem_ports = List(io.dmem, io.imem)
  val interrupt = io.interrupt
  val hartid = io.hartid
  val reset_vector = io.reset_vector
}
