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

package zynq
{

import chisel3._
import chisel3.util._

import RV32_3stage.Constants._
import Common._   
import Common.Util._   
import RV32_3stage._


class SodorTile(implicit val conf: SodorConfiguration) extends Module
{
   val io = IO(new Bundle {
      val dmi = Flipped(new DMIIO())
   })

   val core   = Module(new Core())
   val memory = Module(new MemAccessToTL(num_core_ports=2)) 
   val debug = Module(new DebugModule())
   core.reset := debug.io.resetcore | reset.toBool

   val arbiter = Module(new SodorMemArbiter) // only used for single port memory
   core.io.imem <> arbiter.io.imem
   core.io.dmem <> arbiter.io.dmem
   debug.io.debugmem <> arbiter.io.debugmem
   arbiter.io.mem <> memory.io.core_ports(0)
   arbiter.io.hack := memory.io.hack

   // DTM memory access
   debug.io.ddpath <> core.io.ddpath
   debug.io.dcpath <> core.io.dcpath 
   debug.io.dmi <> io.dmi
}
 
}
