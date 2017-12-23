//**************************************************************************
// RISCV Processor Tile
//--------------------------------------------------------------------------
//

package Sodor
{

import chisel3._
import chisel3.util._
import config._
import Constants._
import Common._   
import Common.Util._   

class SodorTile(implicit p: Parameters) extends Module
{
   val io = IO(new Bundle {
      val dmi = Flipped(new DMIIO())
   })
   
   val core   = Module(new Core())
   val memory = Module(new AsyncScratchPadMemory(num_core_ports = 2))
   val debug = Module(new DebugModule())

   core.io.dmem <> memory.io.core_ports(0)
   core.io.imem <> memory.io.core_ports(1)
   debug.io.debugmem <> memory.io.debug_port

   core.reset := debug.io.resetcore | reset.toBool
   debug.io.ddpath <> core.io.ddpath
   debug.io.dcpath <> core.io.dcpath 
   debug.io.dmi.req <> Queue(io.dmi.req)
   io.dmi.resp <> Queue(debug.io.dmi.resp)
}
 
}
