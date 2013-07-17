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
//   val XPRLEN = 32           // native width of machine
                             // (i.e., the width of a register in 
                             // the general-purpose register file)
}

trait ScalarOpConstants
{
   //************************************
   // Control Signals 
    
   val Y      = Bool(true)
   val N      = Bool(false)

   // PC Select Signal
   val PC_4   = UFix(0, 3);  // PC + 4
   val PC_BR  = UFix(1, 3);  // branch_target
   val PC_J   = UFix(2, 3);  // jump_target
   val PC_JR  = UFix(3, 3);  // jump_reg_target
                                   
   // Branch Type
   val BR_N   = UFix(0, 4);  // Next
   val BR_NE  = UFix(1, 4);  // Branch on NotEqual
   val BR_EQ  = UFix(2, 4);  // Branch on Equal
   val BR_GE  = UFix(3, 4);  // Branch on Greater/Equal
   val BR_GEU = UFix(4, 4);  // Branch on Greater/Equal Unsigned
   val BR_LT  = UFix(5, 4);  // Branch on Less Than
   val BR_LTU = UFix(6, 4);  // Branch on Less Than Unsigned
   val BR_J   = UFix(7, 4);  // Jump 
   val BR_JR  = UFix(8, 4);  // Jump Register
 
   // RS1 Operand Select Signal
   val OP1_RS1 = UFix(0, 1) // Register Source #1
   val OP1_PC  = UFix(1, 1) // PC (auipc, etc.)
   val OP1_X   = UFix(0, 1)
   
   // RS2 Operand Select Signal
   val OP2_RS2 = UFix(0, 2) // Register Source #2
   val OP2_IMI = UFix(1, 2) // immediate, I-type
   val OP2_IMB = UFix(2, 2) // immediate, B-type
   val OP2_UI  = UFix(3, 2) // immediate, U-type
   val OP2_X   = UFix(0, 2)
    
                      
   // Register File Write Enable Signal
   val REN_0   = Bool(false);
   val REN_1   = Bool(true);
   val REN_X   = Bool(false);
           
   // ALU Operation Signal
   val ALU_ADD = UFix ( 1, 4);
   val ALU_SUB = UFix ( 2, 4);
   val ALU_SLL = UFix ( 3, 4);
   val ALU_SRL = UFix ( 4, 4);
   val ALU_SRA = UFix ( 5, 4);
   val ALU_AND = UFix ( 6, 4);
   val ALU_OR  = UFix ( 7, 4);
   val ALU_XOR = UFix ( 8, 4);
   val ALU_SLT = UFix ( 9, 4);
   val ALU_SLTU= UFix (10, 4);
   val ALU_COPY2=UFix (11, 4);
   val ALU_X   = UFix ( 0, 4);
    
   // Writeback Address Select Signal
   val WA_RD   = Bool(true)   // write to register rd
   val WA_RA   = Bool(false)  // write to register x1 (return address)
   val WA_X    = Bool(true)
    
   // Writeback Select Signal
   val WB_ALU  = UFix(0, 2);
   val WB_MEM  = UFix(1, 2);
   val WB_PC4  = UFix(2, 2);
   val WB_PCR  = UFix(3, 2);
   val WB_X    = UFix(0, 2);
   
   // Memory Function Type (Read,Write,Fence) Signal
   val MWR_R   = UFix(0, 2)
   val MWR_W   = UFix(1, 2)
   val MWR_F   = UFix(2, 2)
   val MWR_X   = UFix(0, 2)
                       
   // Memory Enable Signal
   val MEN_0   = Bool(false);
   val MEN_1   = Bool(true);
   val MEN_X   = Bool(false);
                                             
   // Memory Mask Type Signal
   val MSK_B   = UFix(0, 3)
   val MSK_BU  = UFix(1, 3)
   val MSK_H   = UFix(2, 3)
   val MSK_HU  = UFix(3, 3)
   val MSK_W   = UFix(4, 3)
   val MSK_X   = UFix(4, 3)
                     
   // Enable Co-processor Register Signal (ToHost Register, etc.)
   val PCR_N   = UFix(0,2);
   val PCR_F   = UFix(1,2);
   val PCR_T   = UFix(2,2);
 
   // The Bubble Instruction (Machine generated NOP)
   // Insert (XOR x0,x0,x0) which is different from software compiler 
   // generated NOPs which are (ADDI x0, x0, 0).
   // Reasoning for this is to let visualizers and stat-trackers differentiate
   // between software NOPs and machine-generated Bubbles in the pipeline.
   val BUBBLE  = Bits(0x233, 32);
   val RA      = UFix(1)

}

}

