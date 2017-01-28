//**************************************************************************
// RISCV Processor Tile
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2013 Jun 28
//
// Describes a RISCV 3-stage processor, heavily optimized for low-area. This
// core is designed to be the one "realistic" core within Sodor.
// Features:
// - Configurable number of ports to memory (Princeton vs Harvard)
// - synchronous memory
// - RV32IS
// - No div/mul/rem
// - No FPU
// - implements supervisor mode (can trap to handle the above instructions)
//

package Sodor
{

import Chisel._
import Node._
import Constants._
import Common._   
import Common.Util._   


class SodorTileIo extends Bundle  
{
   val host     = new HTIFIO()
}

class SodorTile(implicit val conf: SodorConfiguration) extends Module
{
   val io = new SodorTileIo()

   // notice that while the core is put into reset, the scratchpad needs to be
   // alive so that the HTIF can load in the program.
   val core   = Module(new Core(resetSignal = io.host.reset))
   val memory = Module(new ScratchPadMemory(num_core_ports = NUM_MEMORY_PORTS, seq_read = true)) 

   if (NUM_MEMORY_PORTS == 1)
   {
      val arbiter = Module(new SodorMemArbiter) // only used for single port memory
      core.io.imem <> arbiter.io.imem
      core.io.dmem <> arbiter.io.dmem
      arbiter.io.mem <> memory.io.core_ports(0)
   }
   else
   {
      core.io.imem <> memory.io.core_ports(0)
      core.io.dmem <> memory.io.core_ports(1)
   }

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
