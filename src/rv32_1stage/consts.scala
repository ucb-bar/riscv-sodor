//**************************************************************************
// RISCV Processor Constants
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 May 28

package Sodor
package constants
{

import chisel3._

trait ScalarOpConstants
{
   //************************************
   // Control Signals

   val Y      = true.B
   val N      = false.B

   // PC Select Signal
   val PC_4   = 0.asUInt(3.W)  // PC + 4
   val PC_BR  = 1.asUInt(3.W)  // branch_target
   val PC_J   = 2.asUInt(3.W)  // jump_target
   val PC_JR  = 3.asUInt(3.W)  // jump_reg_target
   val PC_EXC = 4.asUInt(3.W)  // exception

   // Branch Type
   val BR_N   = 0.asUInt(4.W)  // Next
   val BR_NE  = 1.asUInt(4.W)  // Branch on NotEqual
   val BR_EQ  = 2.asUInt(4.W)  // Branch on Equal
   val BR_GE  = 3.asUInt(4.W)  // Branch on Greater/Equal
   val BR_GEU = 4.asUInt(4.W)  // Branch on Greater/Equal Unsigned
   val BR_LT  = 5.asUInt(4.W)  // Branch on Less Than
   val BR_LTU = 6.asUInt(4.W)  // Branch on Less Than Unsigned
   val BR_J   = 7.asUInt(4.W)  // Jump
   val BR_JR  = 8.asUInt(4.W)  // Jump Register

   // RS1 Operand Select Signal
   val OP1_RS1 = 0.asUInt(2.W) // Register Source #1
   val OP1_IMU = 1.asUInt(2.W) // immediate, U-type
   val OP1_IMZ = 2.asUInt(2.W) // Zero-extended rs1 field of inst, for CSRI instructions
   val OP1_X   = 0.asUInt(2.W)

   // RS2 Operand Select Signal
   val OP2_RS2 = 0.asUInt(2.W) // Register Source #2
   val OP2_IMI = 1.asUInt(2.W) // immediate, I-type
   val OP2_IMS = 2.asUInt(2.W) // immediate, S-type
   val OP2_PC  = 3.asUInt(2.W) // PC
   val OP2_X   = 0.asUInt(2.W)

   // Register File Write Enable Signal
   val REN_0   = false.B
   val REN_1   = true.B
   val REN_X   = false.B

   // ALU Operation Signal
   val ALU_ADD = 1.asUInt(4.W)
   val ALU_SUB = 2.asUInt(4.W)
   val ALU_SLL = 3.asUInt(4.W)
   val ALU_SRL = 4.asUInt(4.W)
   val ALU_SRA = 5.asUInt(4.W)
   val ALU_AND = 6.asUInt(4.W)
   val ALU_OR  = 7.asUInt(4.W)
   val ALU_XOR = 8.asUInt(4.W)
   val ALU_SLT = 9.asUInt(4.W)
   val ALU_SLTU= 10.asUInt(4.W)
   val ALU_COPY1= 11.asUInt(4.W)
   val ALU_X   = 0.asUInt(4.W)

   // Writeback Select Signal
   val WB_ALU  = 0.asUInt(2.W)
   val WB_MEM  = 1.asUInt(2.W)
   val WB_PC4  = 2.asUInt(2.W)
   val WB_CSR  = 3.asUInt(2.W)
   val WB_X    = 0.asUInt(2.W)

   // Memory Function Type (Read,Write,Fence) Signal
   val MWR_R   = 0.asUInt(2.W)
   val MWR_W   = 1.asUInt(2.W)
   val MWR_F   = 2.asUInt(2.W)
   val MWR_X   = 0.asUInt(2.W)

   // Memory Enable Signal
   val MEN_0   = Bool(false)
   val MEN_1   = Bool(true)
   val MEN_X   = Bool(false)

   // Memory Mask Type Signal
   val MSK_B   = 0.asUInt(3.W)
   val MSK_BU  = 1.asUInt(3.W)
   val MSK_H   = 2.asUInt(3.W)
   val MSK_HU  = 3.asUInt(3.W)
   val MSK_W   = 4.asUInt(3.W)
   val MSK_X   = 4.asUInt(3.W)


   // Cache Flushes & Sync Primitives
   val M_N      = 0.asUInt(3.W)
   val M_SI     = 1.asUInt(3.W)   // synch instruction stream
   val M_SD     = 2.asUInt(3.W)   // synch data stream
   val M_FA     = 3.asUInt(3.W)   // flush all caches
   val M_FD     = 4.asUInt(3.W)   // flush data cache

   // Memory Functions (read, write, fence)
   val MT_READ  = 0.asUInt(2.W)
   val MT_WRITE = 1.asUInt(2.W)
   val MT_FENCE = 2.asUInt(2.W)

}


}

object Constants extends
   Sodor.constants.ScalarOpConstants with
   Common.constants.RISCVConstants with
   Common.MemoryOpConstants
{
}
