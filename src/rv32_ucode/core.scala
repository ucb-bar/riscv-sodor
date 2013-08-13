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

package Sodor
{

import Chisel._
import Node._
import Common._

class CoreIo(implicit conf: SodorConfiguration) extends Bundle 
{
  val host = new HTIFIO()
  val mem  = new MemPortIo(conf.xprlen)
}

class Core(resetSignal: Bool = null)(implicit conf: SodorConfiguration) extends Module(_reset = resetSignal)
{
  val io = new CoreIo()
  val c  = Module(new CtlPath())
  val d  = Module(new DatPath())
  
  c.io.ctl  <> d.io.ctl
  c.io.dat  <> d.io.dat
  
  c.io.mem <> io.mem
  d.io.mem <> io.mem
  
  d.io.host <> io.host
}

}
