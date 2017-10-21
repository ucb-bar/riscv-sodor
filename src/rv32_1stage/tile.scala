//**************************************************************************
// RISCV Processor Tile
//--------------------------------------------------------------------------
//

package Sodor
{

import chisel3._
import chisel3.util._

import Constants._
import Common._   
import Common.Util._   
import config._


class SodorTile(implicit p: Parameters) extends Module
{
   val io = IO(new Bundle {
      val dmi = Flipped(new DMIIO())
   })

   // notice that while the core is put into reset, the scratchpad needs to be
   // alive so that the HTIF can load in the program.
   val debug = Module(new DebugModule())
   val core   = Module(new Core())
   val memory = Module(new AsyncScratchPadMemory(num_core_ports = 2))
   core.io.dmem <> memory.io.core_ports(0)
   core.io.imem <> memory.io.core_ports(1)
   debug.io.debugmem <> memory.io.debug_port
   core.reset := debug.io.resetcore | reset.toBool
   debug.io.ddpath <> core.io.ddpath
   debug.io.dcpath <> core.io.dcpath 
   debug.io.dmi.req <> Queue(io.dmi.req,1)
   io.dmi.resp <> debug.io.dmi.resp
}

}