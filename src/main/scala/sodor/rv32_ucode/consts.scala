//**************************************************************************
// RISCV U-Coded Processor Constants
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 May 28


package sodor.ucode
package constants
{

import chisel3._
import chisel3.util._

import sodor.common._

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
   val LDIR_0  = 0.asUInt(1.W)
   val LDIR_1  = 1.asUInt(1.W)
   val LDIR_X  = 0.asUInt(1.W)

   // Register File Address Select Signal
   val RS_PC   = 0.asUInt(3.W)
   val RS_RD   = 1.asUInt(3.W)
   val RS_RS1  = 2.asUInt(3.W)
   val RS_RS2  = 3.asUInt(3.W)
   val RS_CA   = 5.asUInt(3.W)
   val RS_CR   = 6.asUInt(3.W)
   val RS_X0   = 7.asUInt(3.W)
   val RS_X    = 7.asUInt(3.W)

   // Register File Write Signal
   val RWR_0   = 0.asUInt(1.W)
   val RWR_1   = 1.asUInt(1.W)
   val RWR_X   = 0.asUInt(1.W)

   // Register File Enable Signal
   val REN_0   = 0.asUInt(1.W)
   val REN_1   = 1.asUInt(1.W)
   val REN_X   = 0.asUInt(1.W)

   // Load A Register Signal
   val LDA_0  = 0.asUInt(1.W)
   val LDA_1  = 1.asUInt(1.W)
   val LDA_X  = 0.asUInt(1.W)

   // Load B Register Signal
   val LDB_0  = 0.asUInt(1.W)
   val LDB_1  = 1.asUInt(1.W)
   val LDB_X  = 0.asUInt(1.W)

   // ALU Operation Signal
   val ALU_COPY_A   = 0.asUInt(5.W)
   val ALU_COPY_B   = 1.asUInt(5.W)
   val ALU_INC_A_1  = 2.asUInt(5.W)
   val ALU_DEC_A_1  = 3.asUInt(5.W)
   val ALU_INC_A_4  = 4.asUInt(5.W)
   val ALU_DEC_A_4  = 5.asUInt(5.W)
   val ALU_ADD      = 6.asUInt(5.W)
   val ALU_SUB      = 7.asUInt(5.W)
   val ALU_SLL      = 8.asUInt(5.W)
   val ALU_SRL      = 9.asUInt(5.W)
   val ALU_SRA      = 10.asUInt(5.W)
   val ALU_AND      = 11.asUInt(5.W)
   val ALU_OR       = 12.asUInt(5.W)
   val ALU_XOR      = 13.asUInt(5.W)
   val ALU_SLT      = 14.asUInt(5.W)
   val ALU_SLTU     = 15.asUInt(5.W)
   val ALU_MASK_12  = 16.asUInt(5.W)  // output A with lower 12 bits cleared (AUIPC)
   val ALU_EVEC     = 17.asUInt(5.W)  // output evec from CSR file
   val ALU_X        = 0.asUInt(5.W)

   // ALU Enable Signal
   val AEN_0   = 0.asUInt(1.W)
   val AEN_1   = 1.asUInt(1.W)
   val AEN_X   = 0.asUInt(1.W)

   val LDMA_0  = 0.asUInt(1.W)
   val LDMA_1  = 1.asUInt(1.W)
   val LDMA_X  = 0.asUInt(1.W)

   // Memory Write Signal
   val MWR_0   = 0.asUInt(1.W)
   val MWR_1   = 1.asUInt(1.W)
   val MWR_X   = 0.asUInt(1.W)

   // Memory Enable Signal
   val MEN_0   = 0.asUInt(1.W)
   val MEN_1   = 1.asUInt(1.W)
   val MEN_X   = 0.asUInt(1.W)

   // Immediate Extend Select
   val IS_I   = 0.asUInt(3.W)  //I-Type (LDs,ALU) ,  sign-extend             : ({ 20{inst[31:20] })
   val IS_S   = 1.asUInt(3.W)  //S-Type (Stores)  ,  sign-extend             : ({ 20{inst[31:25]}, inst[11:7] })
   val IS_U   = 2.asUInt(3.W)  //U-Type (LUI)     ,  sign-extend             : ({ {inst[31:12], {12{1'b0}} })
   val IS_J   = 3.asUInt(3.W)  //J-Type (JAL)     ,  sign-extend and shift 1b: ({  11{inst[31]}, inst[19: 12], inst[20], inst[30:21], 1'b0 })
   val IS_B   = 4.asUInt(3.W)  //B-Type (Branches),  sign-extend and shift 1b: ({ 19{inst[31]}, inst[7], inst[30:25], inst[11:8], 1'b0 })
   val IS_Z   = 5.asUInt(3.W)  //Z-Type (CSRR*I)  ,  zero-extended rs1 field : ({ 27{1'b0}, inst[19:15] })
   val IS_X   = 0.asUInt(3.W)

   // Immediate Enable Signal
   val IEN_0   = 0.asUInt(1.W)
   val IEN_1   = 1.asUInt(1.W)
   val IEN_X   = 0.asUInt(1.W)

   // Enable ToHost Signal
   val TEN_0   = 0.asUInt(1.W)
   val TEN_1   = 1.asUInt(1.W)
   val TEN_X   = 0.asUInt(1.W)

   // uBranch Type
   val UBR_N   = 0.asUInt(3.W) // Next
   val UBR_D   = 1.asUInt(3.W) // Dispatch on Opcode
   val UBR_J   = 2.asUInt(3.W) // Jump
   val UBR_EZ  = 3.asUInt(3.W) // Jump on ALU-Zero
   val UBR_NZ  = 4.asUInt(3.W) // Jump on Not ALU-Zero
   val UBR_S   = 5.asUInt(3.W) // Spin if Mem-Busy


   // Micro-PC State Logic
   val UPC_NEXT    = 0.asUInt(2.W)
   val UPC_ABSOLUTE = 1.asUInt(2.W)
   val UPC_CURRENT = 2.asUInt(2.W)
   val UPC_DISPATCH = 3.asUInt(2.W)

   // Registers
   val PC_IDX = 32.U    //pc register

   // Memory Mask Type Signal
   val MSK_SZ  = 3.W
}

}

