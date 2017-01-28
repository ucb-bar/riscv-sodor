//**************************************************************************
// RISCV Processor Constants
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 May 28

package Sodor
package constants
{

import Chisel._
import Node._

trait SodorProcConstants
{
   //************************************
   // Machine Parameters

   val NUM_MEMORY_PORTS = 2;

   // if the front-end ONLY predicts PC+4, this simplifies quite a bit of logic.
   // First, the PC select mux never needs to compute ExePC + 4 on a branch
   // redirect (since PC+4 is always predicted).
   // Second, JAL can write-back to rd the ExePC, since it will already be PC+4
   // relative to the JAL.
   val PREDICT_PCP4 = true; require(PREDICT_PCP4==true) // no BTB, etc, added yet

   //************************************
   // Debugging
   val PRINT_COMMIT_LOG = false
}

trait ScalarOpConstants
{
   //************************************
   // Control Signals

   val Y      = Bool(true)
   val N      = Bool(false)

   // PC Select Signal
   val PC_4   = UInt(0, 3)  // PC + 4
   val PC_BR  = UInt(1, 3)  // branch_target
   val PC_J   = UInt(2, 3)  // jump_target
   val PC_JR  = UInt(3, 3)  // jump_reg_target
   val PC_EXC = UInt(4, 3)  // exception

   // Branch Type
   val BR_N   = UInt(0, 4)  // Next
   val BR_NE  = UInt(1, 4)  // Branch on NotEqual
   val BR_EQ  = UInt(2, 4)  // Branch on Equal
   val BR_GE  = UInt(3, 4)  // Branch on Greater/Equal
   val BR_GEU = UInt(4, 4)  // Branch on Greater/Equal Unsigned
   val BR_LT  = UInt(5, 4)  // Branch on Less Than
   val BR_LTU = UInt(6, 4)  // Branch on Less Than Unsigned
   val BR_J   = UInt(7, 4)  // Jump
   val BR_JR  = UInt(8, 4)  // Jump Register

   // RS1 Operand Select Signal
   val OP1_RS1 = UInt(0, 2) // Register Source #1
   val OP1_IMU = UInt(1, 2) // immediate, U-type
   val OP1_IMZ = UInt(2, 2) // zero-extended 5-bit immediate; used by CSRI instructions
   val OP1_X   = UInt(0, 2)

   // RS2 Operand Select Signal
   val OP2_RS2 = UInt(0, 2) // Register Source #2
   val OP2_IMI = UInt(1, 2) // immediate, I-type
   val OP2_IMS = UInt(2, 2) // immediate, S-type
   val OP2_PC  = UInt(3, 2) // PC
   val OP2_X   = UInt(0, 2)

   // Register File Write Enable Signal
   val REN_0   = Bool(false)
   val REN_1   = Bool(true)
   val REN_X   = Bool(false)

   // Writeback Select Signal
   val WB_ALU  = UInt(0, 2)
   val WB_MEM  = UInt(1, 2)
   val WB_PC4  = UInt(2, 2)
   val WB_CSR  = UInt(3, 2)
   val WB_X    = UInt(0, 2)

   // Memory Function Type (Read,Write,Fence) Signal
   val MWR_R   = UInt(0, 2)
   val MWR_W   = UInt(1, 2)
   val MWR_F   = UInt(2, 2)
   val MWR_X   = UInt(0, 2)

   // Memory Enable Signal
   val MEN_0   = Bool(false)
   val MEN_1   = Bool(true)
   val MEN_X   = Bool(false)

   // Memory Mask Type Signal
   val MSK_B   = UInt(0, 3)
   val MSK_BU  = UInt(1, 3)
   val MSK_H   = UInt(2, 3)
   val MSK_HU  = UInt(3, 3)
   val MSK_W   = UInt(4, 3)
   val MSK_X   = UInt(4, 3)

   // Cache Flushes & Sync Primitives
   val M_N      = Bits(0,3)
   val M_SI     = Bits(1,3)   // synch instruction stream
   val M_SD     = Bits(2,3)   // synch data stream
   val M_FA     = Bits(3,3)   // flush all caches
   val M_FD     = Bits(4,3)   // flush data cache

   // Memory Functions (read, write, fence)
   val MT_READ  = Bits(0,2)
   val MT_WRITE = Bits(1,2)
   val MT_FENCE = Bits(2,2)


}

}

