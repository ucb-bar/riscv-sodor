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
   val host = new HTIFIO()
}

class SodorTile(implicit val conf: SodorConfiguration) extends Module
{
   val io = IO(new SodorTileIo())

   // notice that while the core is put into reset, the scratchpad needs to be
   // alive so that the HTIF can load in the program.
   val core   = Module(new Core())
   val memory = Module(new AsyncScratchPadMemory(num_core_ports = 2))

   core.io.reset := io.host.reset
   core.io.dmem <> memory.io.core_ports(0)
   core.io.imem <> memory.io.core_ports(1)
   
   // HTIF/memory request
   memory.io.htif_port.req.valid     := io.host.mem_req.valid
   memory.io.htif_port.req.bits.addr := io.host.mem_req.bits.addr.toUInt
   memory.io.htif_port.req.bits.data := io.host.mem_req.bits.data
   memory.io.htif_port.req.bits.fcn  := Mux(io.host.mem_req.bits.rw, M_XWR, M_XRD)
   io.host.mem_req.ready             := memory.io.htif_port.req.ready     

   // HTIF/memory response
   io.host.mem_rep.valid := memory.io.htif_port.resp.valid
   io.host.mem_rep.bits := memory.io.htif_port.resp.bits.data

   core.io.host <> io.host
}
 
}
