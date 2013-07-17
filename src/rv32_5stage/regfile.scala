//**************************************************************************
// RISCV Processor Register File
//--------------------------------------------------------------------------
//

package Sodor
{

import Chisel._
import Node._

import Constants._
import Common._

class RFileIo(implicit conf: SodorConfiguration) extends Bundle()
{
   val rs1_addr = UFix(INPUT, 5)
   val rs1_data = Bits(OUTPUT, conf.xprlen)
   val rs2_addr = UFix(INPUT, 5)
   val rs2_data = Bits(OUTPUT, conf.xprlen)
    
   val waddr    = UFix(INPUT, 5)
   val wdata    = Bits(INPUT, conf.xprlen)
   val wen      = Bool(        INPUT)
}

class RegisterFile(implicit conf: SodorConfiguration) extends Mod
{
   val io = new RFileIo()

   val regfile = Mem(32, Bits(width = conf.xprlen))

   when (io.wen && (io.waddr != UFix(0)))
   {
      regfile(io.waddr) := io.wdata
   }

   io.rs1_data := Mux((io.rs1_addr != UFix(0)), regfile(io.rs1_addr), UFix(0, conf.xprlen))
   io.rs2_data := Mux((io.rs2_addr != UFix(0)), regfile(io.rs2_addr), UFix(0, conf.xprlen))
       
}
}
