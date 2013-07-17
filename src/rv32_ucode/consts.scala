//**************************************************************************
// RISCV U-Coded Processor Constants
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
   // Micro-code Generated Control Signals 
   
   // Load IR Register Signal
   val LDIR_0  = UFix(0, 1)
   val LDIR_1  = UFix(1, 1)
   val LDIR_X  = UFix(0, 1)
    
   // Register File Address Select Signal
   val RS_PC   = UFix(0, 3)
   val RS_RD   = UFix(1, 3)
   val RS_RS1  = UFix(2, 3)
   val RS_RS2  = UFix(3, 3)
   val RS_RA   = UFix(4, 3)
   val RS_X0   = UFix(5, 3)
   val RS_CP   = UFix(6, 3)
   val RS_X    = UFix(5, 3)
                      
   // Register File Write Signal
   val RWR_0   = UFix(0, 1)
   val RWR_1   = UFix(1, 1)
   val RWR_X   = UFix(0, 1)
                      
   // Register File Enable Signal
   val REN_0   = UFix(0, 1)
   val REN_1   = UFix(1, 1)
   val REN_X   = UFix(0, 1)
          
   // Load A Register Signal
   val LDA_0  = UFix(0, 1)
   val LDA_1  = UFix(1, 1)
   val LDA_X  = UFix(0, 1)
          
   // Load B Register Signal
   val LDB_0  = UFix(0, 1)
   val LDB_1  = UFix(1, 1)
   val LDB_X  = UFix(0, 1)
           
   // ALU Operation Signal
   val ALU_COPY_A   = UFix ( 0, 5)
   val ALU_COPY_B   = UFix ( 1, 5)
   val ALU_INC_A_1  = UFix ( 2, 5)
   val ALU_DEC_A_1  = UFix ( 3, 5)
   val ALU_INC_A_4  = UFix ( 4, 5)
   val ALU_DEC_A_4  = UFix ( 5, 5)
   val ALU_ADD      = UFix ( 6, 5)
   val ALU_SUB      = UFix ( 7, 5)
   val ALU_SLL      = UFix ( 8, 5)
   val ALU_SRL      = UFix ( 9, 5)
   val ALU_SRA      = UFix (10, 5)
   val ALU_AND      = UFix (11, 5)
   val ALU_OR       = UFix (12, 5)
   val ALU_XOR      = UFix (13, 5)
   val ALU_SLT      = UFix (14, 5)
   val ALU_SLTU     = UFix (15, 5)
   val ALU_INIT_PC  = UFix (16, 5)  // output START_ADDR, used to initialize the PC register
   val ALU_X        = UFix ( 0, 5)

   // ALU Enable Signal
   val AEN_0   = UFix(0, 1)
   val AEN_1   = UFix(1, 1)
   val AEN_X   = UFix(0, 1)

   val LDMA_0  = UFix(0, 1)
   val LDMA_1  = UFix(1, 1)
   val LDMA_X  = UFix(0, 1)
          
   // Memory Write Signal
   val MWR_0   = UFix(0, 1)
   val MWR_1   = UFix(1, 1)
   val MWR_X   = UFix(0, 1)
                       
   // Memory Enable Signal
   val MEN_0   = UFix(0, 1)
   val MEN_1   = UFix(1, 1)
   val MEN_X   = UFix(0, 1)
                      
   // Immediate Extend Select
   val IS_I   = UFix(0, 3)  //I-Type (LDs,ALU) ,  sign-extend             : ({ 20{inst[21]}, inst[21:10] })  
   val IS_BS  = UFix(1, 3)  //B-Type (Stores)  ,  sign-extend             : ({ 20{inst[31]}, inst[31:27], inst[16:10] })         
   val IS_L   = UFix(2, 3)  //L-Type (LUI)     ,  sign-extend             : ({ {inst[26:7], {12{1'b0}} })                        
   val IS_J   = UFix(3, 3)  //J-Type (J/JAL)   ,  sign-extend and shift 1b: ({  6{inst[31]}, inst[31: 7], 1'b0 })                 
   val IS_BR  = UFix(4, 3)  //B-Type (Branches),  sign-extend and shift 1b: ({ 19{inst[31]}, inst[31:27], inst[16:10], 1'b0 })  
   val IS_X   = UFix(0, 3)  
                   
   // Immediate Enable Signal
   val IEN_0   = UFix(0, 1)
   val IEN_1   = UFix(1, 1)
   val IEN_X   = UFix(0, 1)
   
   // Enable ToHost Signal
   val TEN_0   = UFix(0, 1)
   val TEN_1   = UFix(1, 1)
   val TEN_X   = UFix(0, 1)
                      
   // uBranch Type
   val UBR_N   = UFix(0, 3) // Next
   val UBR_D   = UFix(1, 3) // Dispatch on Opcode
   val UBR_J   = UFix(2, 3) // Jump 
   val UBR_EZ  = UFix(3, 3) // Jump on ALU-Zero
   val UBR_NZ  = UFix(4, 3) // Jump on Not ALU-Zero
   val UBR_S   = UFix(5, 3) // Spin if Mem-Busy 
 
   
   // Micro-PC State Logic
   val UPC_NEXT    = UFix(0,2)
   val UPC_ABSOLUTE= UFix(1,2)
   val UPC_CURRENT = UFix(2,2)
   val UPC_DISPATCH= UFix(3,2)

   // Registers
   val X0   = UFix(0)     //x0             register
   val RA   = UFix(1)     //return address register
   val PC   = UFix(32)    //pc             register
                         
   // Memory Mask Type Signal
   val MSK_B   = UFix(0, 3)
   val MSK_BU  = UFix(1, 3)
   val MSK_H   = UFix(2, 3)
   val MSK_HU  = UFix(3, 3)
   val MSK_W   = UFix(4, 3)
   val MSK_X   = UFix(4, 3)
 
}

}

