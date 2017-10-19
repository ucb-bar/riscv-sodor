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

import chisel3._
import chisel3.util._
import config._
import RV32_3stage.Constants._
import Common._   
import Common.Util._   
import RV32_3stage._


class SodorTile(implicit p: Parameters) extends Module
{
   val io = IO(new Bundle {
      val dmi = Flipped(new DMIIO()(p))
   })

   val core   = Module(new Core()(p))
   val memory = Module(new SyncScratchPadMemory(num_core_ports = p(NUM_MEMORY_PORTS))(p)) 
   val debug = Module(new DebugModule()(p))
   core.reset := debug.io.resetcore | reset.toBool

   if (p(NUM_MEMORY_PORTS) == 1)
   {
      val arbiter = Module(new SodorMemArbiter()(p)) // only used for single port memory
      core.io.imem <> arbiter.io.imem
      core.io.dmem <> arbiter.io.dmem
      arbiter.io.mem <> memory.io.core_ports(0)
   }
   else
   {
      core.io.imem <> memory.io.core_ports(1)
      core.io.dmem <> memory.io.core_ports(0)
   }

   // DTM memory access
   debug.io.debugmem <> memory.io.debug_port
   debug.io.ddpath <> core.io.ddpath
   debug.io.dcpath <> core.io.dcpath 
   debug.io.dmi.req <> Queue(io.dmi.req)
   io.dmi.resp <> Queue(debug.io.dmi.resp)
}
 
}
