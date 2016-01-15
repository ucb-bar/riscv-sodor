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
import Common._

trait SodorProcConstants
{

   //************************************
   // Machine Parameters
}

trait ScalarOpConstants extends MemoryOpConstants
{
   //************************************
   // Micro-code Generated Control Signals

   // Load IR Register Signal
   val LDIR_0  = UInt(0, 1)
   val LDIR_1  = UInt(1, 1)
   val LDIR_X  = UInt(0, 1)

   // Register File Address Select Signal
   val RS_PC   = UInt(0, 3)
   val RS_RD   = UInt(1, 3)
   val RS_RS1  = UInt(2, 3)
   val RS_RS2  = UInt(3, 3)
   val RS_CA   = UInt(5, 3)
   val RS_CR   = UInt(6, 3)
   val RS_X0   = UInt(7, 3)
   val RS_X    = UInt(7, 3)

   // Register File Write Signal
   val RWR_0   = UInt(0, 1)
   val RWR_1   = UInt(1, 1)
   val RWR_X   = UInt(0, 1)

   // Register File Enable Signal
   val REN_0   = UInt(0, 1)
   val REN_1   = UInt(1, 1)
   val REN_X   = UInt(0, 1)

   // Load A Register Signal
   val LDA_0  = UInt(0, 1)
   val LDA_1  = UInt(1, 1)
   val LDA_X  = UInt(0, 1)

   // Load B Register Signal
   val LDB_0  = UInt(0, 1)
   val LDB_1  = UInt(1, 1)
   val LDB_X  = UInt(0, 1)

   // ALU Operation Signal
   val ALU_COPY_A   = UInt ( 0, 5)
   val ALU_COPY_B   = UInt ( 1, 5)
   val ALU_INC_A_1  = UInt ( 2, 5)
   val ALU_DEC_A_1  = UInt ( 3, 5)
   val ALU_INC_A_4  = UInt ( 4, 5)
   val ALU_DEC_A_4  = UInt ( 5, 5)
   val ALU_ADD      = UInt ( 6, 5)
   val ALU_SUB      = UInt ( 7, 5)
   val ALU_SLL      = UInt ( 8, 5)
   val ALU_SRL      = UInt ( 9, 5)
   val ALU_SRA      = UInt (10, 5)
   val ALU_AND      = UInt (11, 5)
   val ALU_OR       = UInt (12, 5)
   val ALU_XOR      = UInt (13, 5)
   val ALU_SLT      = UInt (14, 5)
   val ALU_SLTU     = UInt (15, 5)
   val ALU_INIT_PC  = UInt (16, 5)  // output START_ADDR, used to initialize the PC register
   val ALU_MASK_12  = UInt (17, 5)  // output A with lower 12 bits cleared (AUIPC)
   val ALU_EVEC     = UInt (18, 5)  // output evec from CSR file
   val ALU_X        = UInt ( 0, 5)

   // ALU Enable Signal
   val AEN_0   = UInt(0, 1)
   val AEN_1   = UInt(1, 1)
   val AEN_X   = UInt(0, 1)

   val LDMA_0  = UInt(0, 1)
   val LDMA_1  = UInt(1, 1)
   val LDMA_X  = UInt(0, 1)

   // Memory Write Signal
   val MWR_0   = UInt(0, 1)
   val MWR_1   = UInt(1, 1)
   val MWR_X   = UInt(0, 1)

   // Memory Enable Signal
   val MEN_0   = UInt(0, 1)
   val MEN_1   = UInt(1, 1)
   val MEN_X   = UInt(0, 1)

   // Immediate Extend Select
   val IS_I   = UInt(0, 3)  //I-Type (LDs,ALU) ,  sign-extend             : ({ 20{inst[31:20] })
   val IS_S   = UInt(1, 3)  //S-Type (Stores)  ,  sign-extend             : ({ 20{inst[31:25]}, inst[11:7] })
   val IS_U   = UInt(2, 3)  //U-Type (LUI)     ,  sign-extend             : ({ {inst[31:12], {12{1'b0}} })
   val IS_J   = UInt(3, 3)  //J-Type (JAL)     ,  sign-extend and shift 1b: ({  11{inst[31]}, inst[19: 12], inst[20], inst[30:21], 1'b0 })
   val IS_B   = UInt(4, 3)  //B-Type (Branches),  sign-extend and shift 1b: ({ 19{inst[31]}, inst[7], inst[30:25], inst[11:8], 1'b0 })
   val IS_Z   = UInt(5, 3)  //Z-Type (CSRR*I)  ,  zero-extended rs1 field : ({ 27{1'b0}, inst[19:15] })
   val IS_X   = UInt(0, 3)

   // Immediate Enable Signal
   val IEN_0   = UInt(0, 1)
   val IEN_1   = UInt(1, 1)
   val IEN_X   = UInt(0, 1)

   // Enable ToHost Signal
   val TEN_0   = UInt(0, 1)
   val TEN_1   = UInt(1, 1)
   val TEN_X   = UInt(0, 1)

   // uBranch Type
   val UBR_N   = UInt(0, 3) // Next
   val UBR_D   = UInt(1, 3) // Dispatch on Opcode
   val UBR_J   = UInt(2, 3) // Jump
   val UBR_EZ  = UInt(3, 3) // Jump on ALU-Zero
   val UBR_NZ  = UInt(4, 3) // Jump on Not ALU-Zero
   val UBR_S   = UInt(5, 3) // Spin if Mem-Busy


   // Micro-PC State Logic
   val UPC_NEXT    = UInt(0,2)
   val UPC_ABSOLUTE= UInt(1,2)
   val UPC_CURRENT = UInt(2,2)
   val UPC_DISPATCH= UInt(3,2)

   // Registers
   val PC_IDX = UInt(32)    //pc register

   // Memory Mask Type Signal
   val MSK_SZ  = 3
}

}

