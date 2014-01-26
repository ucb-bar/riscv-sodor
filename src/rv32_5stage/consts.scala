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

//   val XPRLEN = 32                // native width of machine
                                  // (i.e., the width of a register in 
                                  // the general-purpose register file)
}

trait ScalarOpConstants
{
  
   //************************************
   // Control Signals 
   val Y        = Bool(true)
   val N        = Bool(false)
    
   // PC Select Signal
   val PC_PLUS4 = UInt(0, 2)  // PC + 4
   val PC_BRJMP = UInt(1, 2)  // brjmp_target 
   val PC_JALR  = UInt(2, 2)  // jump_reg_target
                                   
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
   val OP1_RS1   = UInt(0, 1) // Register Source #1
   val OP1_PC    = UInt(1, 1) // PC
   val OP1_X     = UInt(0, 1)  

   // RS2 Operand Select Signal
   val OP2_RS2    = UInt(0, 3) // Register Source #2
   val OP2_ITYPE  = UInt(1, 3) // immediate, I-type
   val OP2_STYPE  = UInt(2, 3) // immediate, S-type
   val OP2_SBTYPE = UInt(3, 3)
   val OP2_UTYPE  = UInt(4, 3) // immediate, U-type
   val OP2_UJTYPE = UInt(5, 3) // immediate, UJ-type
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
   val ALU_COPY_2 = UInt(10, 4)
   val ALU_X      = UInt ( 0, 4)
    
   // Writeback Address Select Signal
   val WA_RD   = Bool(true)   // write to register rd
   val WA_RA   = Bool(false)  // write to RA register (return address)
   val WA_X    = Bool(true)
    
   // Writeback Select Signal
   val WB_ALU  = UInt(0, 2)
   val WB_MEM  = UInt(1, 2)
   val WB_PC4  = UInt(2, 2)
   val WB_PCR  = UInt(3, 2)
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
                     
   // Enable Co-processor Register Signal (ToHost Register, etc.)
   val PCR_N   = UInt(0,2)
   val PCR_F   = UInt(1,2)
   val PCR_T   = UInt(2,2)
     
 
   // The Bubble Instruction (Machine generated NOP)
   // Insert (XOR x0,x0,x0) which is different from software compiler 
   // generated NOPs which are (ADDI x0, x0, 0).
   // Reasoning for this is to let visualizers and stat-trackers differentiate
   // between software NOPs and machine-generated Bubbles in the pipeline.
   val BUBBLE  = Bits(0x233, 32)

   val RA = UInt(1) // address of the return address register
  
}

}

