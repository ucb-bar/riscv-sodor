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


class SodorTileIo extends Bundle  
{
   implicit val conf = SodorConfiguration()
   val dmi = Flipped(new DMIIO())
}

class SodorTile(implicit val conf: SodorConfiguration) extends Module
{
   val io = IO(new SodorTileIo())

   // notice that while the core is put into reset, the scratchpad needs to be
   // alive so that the HTIF can load in the program.
   val core   = Module(new Core())
   val memory = Module(new AsyncScratchPadMemory(num_core_ports = 2))

   core.io.dmem <> memory.io.core_ports(0)
   core.io.imem <> memory.io.core_ports(1)
   
}
 
}
