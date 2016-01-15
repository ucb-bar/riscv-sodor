//**************************************************************************
// RISCV Processor Constants
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 Feb 1

package Sodor
package constants
{

import Chisel._
import Node._

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
   val Y        = Bool(true)
   val N        = Bool(false)

   // PC Select Signal
   val PC_4     = UInt(0, 2)  // PC + 4
   val PC_BRJMP = UInt(1, 2)  // brjmp_target
   val PC_JALR  = UInt(2, 2)  // jump_reg_target
   val PC_EXC   = UInt(3, 2)  // exception

   // Branch Type
   val BR_N     = UInt(0, 4)  // Next
   val BR_NE    = UInt(1, 4)  // Branch on NotEqual
   val BR_EQ    = UInt(2, 4)  // Branch on Equal
   val BR_GE    = UInt(3, 4)  // Branch on Greater/Equal
   val BR_GEU   = UInt(4, 4)  // Branch on Greater/Equal Unsigned
   val BR_LT    = UInt(5, 4)  // Branch on Less Than
   val BR_LTU   = UInt(6, 4)  // Branch on Less Than Unsigned
   val BR_J     = UInt(7, 4)  // Jump
   val BR_JR    = UInt(8, 4)  // Jump Register

   // RS1 Operand Select Signal
   val OP1_RS1   = UInt(0, 2) // Register Source #1
   val OP1_PC    = UInt(1, 2) // PC
   val OP1_IMZ   = UInt(2, 2) // Zero-extended Immediate from RS1 field, for use by CSRI instructions
   val OP1_X     = UInt(0, 2)

   // RS2 Operand Select Signal
   val OP2_RS2    = UInt(0, 3) // Register Source #2
   val OP2_ITYPE  = UInt(1, 3) // immediate, I-type
   val OP2_STYPE  = UInt(2, 3) // immediate, S-type
   val OP2_SBTYPE = UInt(3, 3) // immediate, B
   val OP2_UTYPE  = UInt(4, 3) // immediate, U-type
   val OP2_UJTYPE = UInt(5, 3) // immediate, J-type
   val OP2_X      = UInt(0, 3)

   // Register Operand Output Enable Signal
   val OEN_0   = Bool(false)
   val OEN_1   = Bool(true)

   // Register File Write Enable Signal
   val REN_0   = Bool(false)
   val REN_1   = Bool(true)

   // ALU Operation Signal
   val ALU_ADD    = UInt ( 0, 4)
   val ALU_SUB    = UInt ( 1, 4)
   val ALU_SLL    = UInt ( 2, 4)
   val ALU_SRL    = UInt ( 3, 4)
   val ALU_SRA    = UInt ( 4, 4)
   val ALU_AND    = UInt ( 5, 4)
   val ALU_OR     = UInt ( 6, 4)
   val ALU_XOR    = UInt ( 7, 4)
   val ALU_SLT    = UInt ( 8, 4)
   val ALU_SLTU   = UInt ( 9, 4)
   val ALU_COPY_1 = UInt (10, 4)
   val ALU_COPY_2 = UInt (11, 4)
   val ALU_X      = UInt ( 0, 4)

   // Writeback Select Signal
   val WB_ALU  = UInt(0, 2)
   val WB_MEM  = UInt(1, 2)
   val WB_PC4  = UInt(2, 2)
   val WB_CSR  = UInt(3, 2)
   val WB_X    = UInt(0, 2)

   // Memory Write Signal
   val MWR_0   = Bool(false)
   val MWR_1   = Bool(true)
   val MWR_X   = Bool(false)

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

}

}

