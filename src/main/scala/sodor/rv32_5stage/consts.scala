//**************************************************************************
// RISCV Processor Constants
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 Feb 1

package sodor.stage5
package constants
{

import chisel3._
import chisel3.util._


trait SodorProcConstants
{

   //************************************
   // Machine Parameters
   val USE_FULL_BYPASSING = true  // turn on full bypassing (only stalls
                                  // on load-use). Otherwise rely
                                  // entirely on interlocking to handle
                                  // pipeline hazards.
}

trait ScalarOpConstants
{

   //************************************
   // Control Signals
   val Y        = true.B
   val N        = false.B

   // PC Select Signal
   val PC_4     = 0.asUInt(2.W)  // PC + 4
   val PC_BRJMP = 1.asUInt(2.W)  // brjmp_target
   val PC_JALR  = 2.asUInt(2.W)  // jump_reg_target
   val PC_EXC   = 3.asUInt(2.W)  // exception

   // Branch Type
   val BR_N     = 0.asUInt(4.W)  // Next
   val BR_NE    = 1.asUInt(4.W)  // Branch on NotEqual
   val BR_EQ    = 2.asUInt(4.W)  // Branch on Equal
   val BR_GE    = 3.asUInt(4.W)  // Branch on Greater/Equal
   val BR_GEU   = 4.asUInt(4.W)  // Branch on Greater/Equal Unsigned
   val BR_LT    = 5.asUInt(4.W)  // Branch on Less Than
   val BR_LTU   = 6.asUInt(4.W)  // Branch on Less Than Unsigned
   val BR_J     = 7.asUInt(4.W)  // Jump
   val BR_JR    = 8.asUInt(4.W)  // Jump Register

   // RS1 Operand Select Signal
   val OP1_RS1   = 0.asUInt(2.W) // Register Source #1
   val OP1_PC    = 1.asUInt(2.W) // PC
   val OP1_IMZ   = 2.asUInt(2.W) // Zero-extended Immediate from RS1 field, for use by CSRI instructions
   val OP1_X     = 0.asUInt(2.W)

   // RS2 Operand Select Signal
   val OP2_RS2    = 0.asUInt(3.W) // Register Source #2
   val OP2_ITYPE  = 1.asUInt(3.W) // immediate, I-type
   val OP2_STYPE  = 2.asUInt(3.W) // immediate, S-type
   val OP2_SBTYPE = 3.asUInt(3.W) // immediate, B
   val OP2_UTYPE  = 4.asUInt(3.W) // immediate, U-type
   val OP2_UJTYPE = 5.asUInt(3.W) // immediate, J-type
   val OP2_X      = 0.asUInt(3.W)

   // Register Operand Output Enable Signal
   val OEN_0   = false.B
   val OEN_1   = true.B

   // Register File Write Enable Signal
   val REN_0   = false.B
   val REN_1   = true.B

   // ALU Operation Signal
   val ALU_ADD    = 0.asUInt(4.W)
   val ALU_SUB    = 1.asUInt(4.W)
   val ALU_SLL    = 2.asUInt(4.W)
   val ALU_SRL    = 3.asUInt(4.W)
   val ALU_SRA    = 4.asUInt(4.W)
   val ALU_AND    = 5.asUInt(4.W)
   val ALU_OR     = 6.asUInt(4.W)
   val ALU_XOR    = 7.asUInt(4.W)
   val ALU_SLT    = 8.asUInt(4.W)
   val ALU_SLTU   = 9.asUInt(4.W)
   val ALU_COPY_1 = 10.asUInt(4.W)
   val ALU_COPY_2 = 11.asUInt(4.W)
   val ALU_X      = 0.asUInt(4.W)

   // Writeback Select Signal
   val WB_ALU  = 0.asUInt(2.W)
   val WB_MEM  = 1.asUInt(2.W)
   val WB_PC4  = 2.asUInt(2.W)
   val WB_CSR  = 3.asUInt(2.W)
   val WB_X    = 0.asUInt(2.W)

   // Memory Write Signal
   val MWR_0   = false.B
   val MWR_1   = true.B
   val MWR_X   = false.B

   // Memory Enable Signal
   val MEN_0   = false.B
   val MEN_1   = true.B
   val MEN_X   = false.B

   // Memory Mask Type Signal
   val MSK_B   = 0.asUInt(3.W)
   val MSK_BU  = 1.asUInt(3.W)
   val MSK_H   = 2.asUInt(3.W)
   val MSK_HU  = 3.asUInt(3.W)
   val MSK_W   = 4.asUInt(3.W)
   val MSK_X   = 4.asUInt(3.W)

}

}

