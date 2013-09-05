//**************************************************************************
// RISCV Processor Constants
//--------------------------------------------------------------------------


package Sodor
package constants
{

import Chisel._
import Node._
   
trait SodorProcConstants
{
   //************************************
   // Machine Parameters
   
   val NUM_MEMORY_PORTS = 2 // number of ports to memory (1 or 2)

}
   
trait ScalarOpConstants
{
   //************************************
   // Control Signals 

   val Y      = Bool(true)
   val N      = Bool(false)
    
   // PC Select Signal
   val PC_4   = UInt(0, 2)  // PC + 4
   val PC_BRJMP=UInt(1, 2)  // br or jmp target
   val PC_JR  = UInt(2, 2)  // jumpreg target
   val PC_EXC = UInt(3, 2)  // exception 
                                  
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
   val OP1_RS1 = UInt(0, 1) // Register Source #1
   val OP1_PC  = UInt(1, 1) // PC
   val OP1_X   = UInt(0, 1)
   
   // RS2 Operand Select Signal
   val OP2_RS2 = UInt(0, 3) // Register Source #2
   val OP2_IMI = UInt(1, 3) // immediate, I-type
   val OP2_IMB = UInt(2, 3) // immediate, B-type
   val OP2_UI  = UInt(3, 3) // immediate, U-type
   val OP2_4   = UInt(4, 3) // literal 4 (for PC+4 shift)
   val OP2_X   = UInt(0, 3)
                      
   // Register File Write Enable Signal
   val REN_0   = Bool(false)
   val REN_1   = Bool(true)
   val REN_X   = Bool(false)
           
   // Is 32b Word or 64b Doubldword?
   val SZ_DW = 1
   val DW_X   = Bool(false) //Bool(XPRLEN==64)
   val DW_32  = Bool(false)
   val DW_64  = Bool(true)
   val DW_XPR = Bool(false) //Bool(XPRLEN==64)

    
   // Writeback Address Select Signal
   val WA_RD   = Bool(true)   // write to register rd
   val WA_RA   = Bool(false)  // write to register x1 (return address)
   val WA_X    = Bool(true)
    
   // Writeback Select Signal
   val WB_ALU  = UInt(0, 3)
   val WB_MEM  = UInt(1, 3)
   val WB_PC4  = UInt(2, 3)
   val WB_PCR  = UInt(3, 3)
   val WB_TSC  = UInt(4, 3)
   val WB_IRT  = UInt(5, 3)
   val WB_X    = UInt(0, 3)
   
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
                     
   // Enable Co-processor Register Signal (ToHost Register, etc.)
   val PCR_N   = UInt(0,3)    // do nothing
   val PCR_F   = UInt(1,3)    // mtpcr
   val PCR_T   = UInt(2,3)    // mfpcr
   val PCR_C   = UInt(3,3)    // clear pcr
   val PCR_S   = UInt(4,3)    // set pcr
 
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


   // The Bubble Instruction (Machine generated NOP)
   // Insert (XOR x0,x0,x0) which is different from software compiler 
   // generated NOPs which are (ADDI x0, x0, 0).
   // Reasoning for this is to let visualizers and stat-trackers differentiate
   // between software NOPs and machine-generated Bubbles in the pipeline.
   val BUBBLE  = Bits(0x233, 32)

   val RA = UInt(1) // return address register for JAL
 
}
 
}

