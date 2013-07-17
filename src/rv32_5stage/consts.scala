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
   val PC_PLUS4 = UFix(0, 2)  // PC + 4
   val PC_BRJMP = UFix(1, 2)  // brjmp_target 
   val PC_JALR  = UFix(2, 2)  // jump_reg_target
                                   
   // Branch Type
   val BR_N     = UFix(0, 4)  // Next
   val BR_NE    = UFix(1, 4)  // Branch on NotEqual
   val BR_EQ    = UFix(2, 4)  // Branch on Equal
   val BR_GE    = UFix(3, 4)  // Branch on Greater/Equal
   val BR_GEU   = UFix(4, 4)  // Branch on Greater/Equal Unsigned
   val BR_LT    = UFix(5, 4)  // Branch on Less Than
   val BR_LTU   = UFix(6, 4)  // Branch on Less Than Unsigned
   val BR_J     = UFix(7, 4)  // Jump 
   val BR_JR    = UFix(8, 4)  // Jump Register
 
   // RS1 Operand Select Signal
   val OP1_RS1   = UFix(0, 1) // Register Source #1
   val OP1_PC    = UFix(1, 1) // PC
   val OP1_X     = UFix(0, 1)  

   // RS2 Operand Select Signal
   val OP2_RS2   = UFix(0, 3) // Register Source #2
   val OP2_BTYPE = UFix(1, 3) // immediate, B-type
   val OP2_ITYPE = UFix(2, 3) // immediate, I-type
   val OP2_LTYPE = UFix(3, 3) // immediate, L-type
   val OP2_JTYPE = UFix(4, 3) // immediate, J-type
   val OP2_X     = UFix(0, 3)
   
   // Register Operand Output Enable Signal
   val OEN_0   = Bool(false)
   val OEN_1   = Bool(true)
                      
   // Register File Write Enable Signal
   val REN_0   = Bool(false)
   val REN_1   = Bool(true)
           
   // ALU Operation Signal
   val ALU_ADD = UFix ( 0, 4)
   val ALU_SUB = UFix ( 1, 4)
   val ALU_SLL = UFix ( 2, 4)
   val ALU_SRL = UFix ( 3, 4)
   val ALU_SRA = UFix ( 4, 4)
   val ALU_AND = UFix ( 5, 4)
   val ALU_OR  = UFix ( 6, 4)
   val ALU_XOR = UFix ( 7, 4)
   val ALU_SLT = UFix ( 8, 4)
   val ALU_SLTU= UFix ( 9, 4)
   val ALU_COPY_2=UFix(10, 4)
   val ALU_X   = UFix ( 0, 4)
    
   // Writeback Address Select Signal
   val WA_RD   = Bool(true)   // write to register rd
   val WA_RA   = Bool(false)  // write to RA register (return address)
   val WA_X    = Bool(true)
    
   // Writeback Select Signal
   val WB_ALU  = UFix(0, 2)
   val WB_MEM  = UFix(1, 2)
   val WB_PC4  = UFix(2, 2)
   val WB_PCR  = UFix(3, 2)
   val WB_X    = UFix(0, 2)
   
   // Memory Write Signal
   val MWR_0   = Bool(false)
   val MWR_1   = Bool(true)
   val MWR_X   = Bool(false)
                       
   // Memory Enable Signal
   val MEN_0   = Bool(false)
   val MEN_1   = Bool(true)
   val MEN_X   = Bool(false)
                         
   // Memory Mask Type Signal
   val MSK_B   = UFix(0, 3)
   val MSK_BU  = UFix(1, 3)
   val MSK_H   = UFix(2, 3)
   val MSK_HU  = UFix(3, 3)
   val MSK_W   = UFix(4, 3)
   val MSK_X   = UFix(4, 3)
                     
   // Enable Co-processor Register Signal (ToHost Register, etc.)
   val PCR_N   = UFix(0,2)
   val PCR_F   = UFix(1,2)
   val PCR_T   = UFix(2,2)
     
 
   // The Bubble Instruction (Machine generated NOP)
   // Insert (XOR x0,x0,x0) which is different from software compiler 
   // generated NOPs which are (ADDI x0, x0, 0).
   // Reasoning for this is to let visualizers and stat-trackers differentiate
   // between software NOPs and machine-generated Bubbles in the pipeline.
   val BUBBLE  = Bits(0x233, 32)

   val RA = UFix(1) // address of the return address register
  
}

}

