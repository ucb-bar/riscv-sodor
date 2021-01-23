//**************************************************************************
// RISCV Micro-Code
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 May 28
//
// Micro-code that controls the processor is found in here. The Micro-code
// Compiler takes the micro-code in here and generates a ROM for use by the
// control path.
//
//--------------------------------------------------------------------------
//      Quick tutorial on writing microcode:
//
//      Here is the example microcode for the ADD macro-instruction.  Label the
//      FIRST micro-op with the name of the macro-instruction.  It MUST match the
//      name of the macro-instruction as provided in "instructions.scala", since
//      this match is used by the MicrocodeCompiler to generate the
//      UopDispatchTable.
//
//      The last executed micro-op should micro-jump (UBR_J) back to FETCH to
//      begin fetching the next instruction. If need be, you can add your own
//      labels in front of any micro-op, and you can use your new label as a
//      target to micro-branch to (your own personal labels do NOT have any
//      naming restrictions. Only the FIRST micro-op must be labeled with a
//      name that matches the macro-instruction name you wish to match to on
//      instruction dispatch).
//
//
//   /* ADD              */
//   /* A  <- Reg[rs1]   */,Label("ADD"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, MT_X , IS_X  , IEN_0, UBR_N), "X")
//   /* B  <- Reg[rs2]   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X    , AEN_0, LDMA_X, MWR_X, MEN_0, MT_X , IS_X  , IEN_0, UBR_N), "X")
//   /* Reg[rd] <- A + B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_ADD  , AEN_1, LDMA_X, MWR_X, MEN_0, MT_X , IS_X  , IEN_0, UBR_J), "FETCH")
//             ^               ^                                             ^
//        Psuedocode       Label (acts like a goto target in C or asm).      |                                                                                                      ^
//                                                                       The control signals are concatenated together here.                                                        |
//                                                                                                                                                                         This is the micro-
//                                                                                                                                                                            branch target.
//                                                                                                                                                                       Use "X" for "Don't Care".
//                                                                                                                                                                        Otherwise, must match
//                                                                                                                                                                         an existing Label().
//--------------------------------------------------------------------------
//

package sodor.ucode

import chisel3._
import chisel3.util._

import freechips.rocketchip.rocket.CSR

import Constants._
import sodor.common._

object Microcode
{

   val codes = Array[MicroOp](
                         /*      State                 | CSR | LD_IR | Reg   | Reg  | Reg  | Ld A | Ld B |   ALUOP    | En   | Ld MA | Mem  | Mem  | Mem  | Imm  | En   |  UBr | NextState */
                         /*                            |     |       | Sel   |  Wr  | En   |      |      |            | ALU  |       |  Wr  |  En  | Mask | Sel  | Imm  |      |           */

  /* --- Misc. Operations -------------------------- */

   /* Instruction Fetch*/
   /* MA <- PC         */ Label("FETCH"),   Signals(Cat(CSR.N, LDIR_X, RS_PC , RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_1, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* A  <- PC         */
   /* IR <- Mem        */,                  Signals(Cat(CSR.N, LDIR_1, RS_X  , RWR_0, REN_0, LDA_0, LDB_X, ALU_X      , AEN_0, LDMA_0, MWR_0, MEN_1, MT_W , IS_X , IEN_0, UBR_S), "X")
   /* PC <- A + 4      */,                  Signals(Cat(CSR.N, LDIR_0, RS_PC , RWR_1, REN_0, LDA_0, LDB_X, ALU_INC_A_4, AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_D), "X")
   /*Dispatch on Opcode*/

   /* NOP              */
   /* UBr to FETCH     */,Label("NOP"),     Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")


   /* ILLEGAL-OP       */
   /* UBr to FETCH     */,Label("ILLEGAL"), Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
                         ,                  Signals(Cat(CSR.N, LDIR_0, RS_PC , RWR_1, REN_0, LDA_X, LDB_X, ALU_EVEC   , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* UNIMPLEMENTED    */
   /* UBr to FETCH     */,Label("UNIMP") ,  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* --- Load & Store Instructions ----------------- */

   /* LW               */
   /* A  <- Reg[rs1]   */,Label("LW"),      Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* MA <- A + B      */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_ADD    , AEN_1, LDMA_1, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- Mem   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_0, MWR_0, MEN_1, MT_W , IS_X , IEN_0, UBR_S), "X")
   /* UBr to FETCH     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   // extra LW4 uop required since we can't both ujump to fetch0 or spin on LW3

   /* SW               */
   /* A  <- Reg[rs1]   */,Label("SW"),      Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B <- Sext(SImm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_S , IEN_1, UBR_N), "X")
   /* MA <- A + B      */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_ADD    , AEN_1, LDMA_1, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Mem <- Reg[rs2]  */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_0, MWR_1, MEN_0, MT_W , IS_X , IEN_0, UBR_S), "X")
   /* UBr to FETCH     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   /* SImm12 is a "split immediate" */

   /* LB               */
   /* A  <- Reg[rs1]   */,Label("LB"),      Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* MA <- A + B      */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_ADD    , AEN_1, LDMA_1, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- Mem   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_0, MWR_0, MEN_1, MT_B , IS_X , IEN_0, UBR_S), "X")
   /* UBr to FETCH     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   // extra LB4 uop required since we can't both ujump to fetch0 or spin on LB3

   /* SB               */
   /* A  <- Reg[rs1]   */,Label("SB"),      Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B <- Sext(SImm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_S , IEN_1, UBR_N), "X")
   /* MA <- A + B      */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_ADD    , AEN_1, LDMA_1, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Mem <- Reg[rs2]  */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_0, MWR_1, MEN_0, MT_B , IS_X , IEN_0, UBR_S), "X")
   /* UBr to FETCH     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   /* SImm12 is a "split immediate" */

   /* LH               */
   /* A  <- Reg[rs1]   */,Label("LH"),      Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* MA <- A + B      */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_ADD    , AEN_1, LDMA_1, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- Mem   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_0, MWR_0, MEN_1, MT_H , IS_X , IEN_0, UBR_S), "X")
   /* UBr to FETCH     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   // extra LH4 uop required since we can't both ujump to fetch0 or spin on LH3

   /* SH               */
   /* A  <- Reg[rs1]   */,Label("SH"),      Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B <- Sext(SImm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_S , IEN_1, UBR_N), "X")
   /* MA <- A + B      */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_ADD    , AEN_1, LDMA_1, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Mem <- Reg[rs2]  */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_0, MWR_1, MEN_0, MT_H , IS_X , IEN_0, UBR_S), "X")
   /* UBr to FETCH     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   /* SImm12 is a "split immediate" */

   /* LBU              */
   /* A  <- Reg[rs1]   */,Label("LBU"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* MA <- A + B      */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_ADD    , AEN_1, LDMA_1, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- Mem   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_0, MWR_0, MEN_1, MT_BU, IS_X , IEN_0, UBR_S), "X")
   /* UBr to FETCH     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   // extra LBU4 uop required since we can't both ujump to fetch0 or spin on LBU3

   /* LHU              */
   /* A  <- Reg[rs1]   */,Label("LHU"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* MA <- A + B      */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_ADD    , AEN_1, LDMA_1, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- Mem   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_0, MWR_0, MEN_1, MT_HU, IS_X , IEN_0, UBR_S), "X")
   /* UBr to FETCH     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   // extra LHU4 uop required since we can't both ujump to fetch0 or spin on LHU3

   /* --- Atomic Memory Operation Instructions ------ */


   /* --- Integer Register-Immediate Instructions --- */

   /* LUI              */
   /* Reg[rd]<- Imm20  */,Label("LUI"),     Signals(Cat(CSR.N, LDIR_0, RS_RD,  RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_U , IEN_1, UBR_J), "FETCH")

   /* ADDI             */
   /* A  <- Reg[rs1]   */,Label("ADDI"),    Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A + B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_ADD    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* SLTI             */
   /* A  <- Reg[rs1]   */,Label("SLTI"),    Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* Reg[rd] <- $A<$B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_SLT    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* SLTIU            */
   /* A  <- Reg[rs1]   */,Label("SLTIU"),   Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A < B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_SLTU   , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* SLLI             */
   /* A  <- Reg[rs1]   */,Label("SLLI"),    Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A << B*/,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_SLL    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* SRLI             */
   /* A  <- Reg[rs1]   */,Label("SRLI"),    Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A>>>B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_SRL    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* SRAI             */
   /* A  <- Reg[rs1]   */,Label("SRAI"),    Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A>>>B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_SRA    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* ANDI             */
   /* A  <- Reg[rs1]   */,Label("ANDI"),    Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A & B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_AND    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* ORI              */
   /* A  <- Reg[rs1]   */,Label("ORI"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A | B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_OR     , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* XORI             */
   /* A  <- Reg[rs1]   */,Label("XORI"),    Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* Reg[rd] <- A ^ B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_XOR    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* --- Integer Register-Register Instructions ---- */

   /* ADD              */
   /* A  <- Reg[rs1]   */,Label("ADD"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A + B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_ADD    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* SUB              */
   /* A  <- Reg[rs1]   */,Label("SUB"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A - B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_SUB    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* SLT              */
   /* A  <- Reg[rs1]   */,Label("SLT"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- $A<$B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_SLT    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* SLTU             */
   /* A  <- Reg[rs1]   */,Label("SLTU"),    Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A < B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_SLTU   , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* SLL              */
   /* A  <- Reg[rs1]   */,Label("SLL"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A << B*/,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_SLL    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* SRL              */
   /* A  <- Reg[rs1]   */,Label("SRL"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A>>>B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_SRL    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* SRA              */
   /* A  <- Reg[rs1]   */,Label("SRA"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A>>>B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_SRA    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* AND              */
   /* A  <- Reg[rs1]   */,Label("AND"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A & B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_AND    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* OR               */
   /* A  <- Reg[rs1]   */,Label("OR"),      Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A | B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_OR     , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* XOR              */
   /* A  <- Reg[rs1]   */,Label("XOR"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- Reg[rs2]   */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A ^ B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_XOR    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")


   /* --- Control Transfer Instructions ------------- */

   // note: Reg[PC] is actually storing PC+4...


   /* JAL              */
   /* A  <- PC         */,Label("JAL"),     Signals(Cat(CSR.N, LDIR_0, RS_PC , RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* B  <- Sext(Imm25)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_J , IEN_1, UBR_N),  "X")
   /* A  <- A + B      */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_X, ALU_ADD    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* B  <- PC         */,                  Signals(Cat(CSR.N, LDIR_0, RS_PC , RWR_0, REN_1, LDA_X, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* PC <- A - 4      */,                  Signals(Cat(CSR.N, LDIR_0, RS_PC , RWR_1, REN_0, LDA_0, LDB_X, ALU_DEC_A_4, AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* Reg[x1] <- B     */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_X, LDB_0, ALU_COPY_B , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J),  "FETCH")

   /* JALR             */
   /* A  <- Reg[rs1]   */,Label("JALR"),    Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* B  <- Sext(Imm12)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N),  "X")
   /* A  <- A + B      */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_0, ALU_ADD    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* B  <- PC         */,                  Signals(Cat(CSR.N, LDIR_0, RS_PC , RWR_0, REN_1, LDA_X, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* PC <- A          */,                  Signals(Cat(CSR.N, LDIR_0, RS_PC , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* Reg[rd] <- B     */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_B , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J),  "FETCH")

   /* AUIPC            */
   /* A  <- PC         */,Label("AUIPC"),   Signals(Cat(CSR.N, LDIR_0, RS_PC , RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* A  <- A - 4      */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_X,ALU_DEC_A_4 , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* B  <- Imm-UType  */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_U , IEN_1, UBR_N),  "X")
   /* Reg[rd] <- A + B */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_X, LDB_X, ALU_ADD    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J),  "FETCH")



   /* BEQ              */
   /* A <- Reg[rs1]    */,Label("BEQ"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* if zero?(A-B)    */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_SUB    , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_EZ), "BZ_TAKEN")
   /*   ubr to BZ-TAKEN*/
   /* else             */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J),  "FETCH")
   /*    UBr to FETCH */

   /* BNE              */
   /* A <- Reg[rs1]    */,Label("BNE"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* if not zero?(A-B)*/,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_SUB    , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_NZ), "BZ_TAKEN")
   /*   ubr to BZ-TAKEN*/
   /* else             */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J),  "FETCH")
   /*    UBr to FETCH */

   /* BLT              */
   /* A <- Reg[rs1]    */,Label("BLT"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* A <- (A < B)     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_0, ALU_SLT    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* if not zero?     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_COPY_A , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_NZ), "BZ_TAKEN")
   /*   ubr to BZ-TAKEN*/
   /* else             */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J),  "FETCH")
   /*    UBr to FETCH */

   /* BLTU             */
   /* A <- Reg[rs1]    */,Label("BLTU"),    Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* A <- (A < B)     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_0, ALU_SLTU   , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* if not zero?     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_COPY_A , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_NZ), "BZ_TAKEN")
   /*   ubr to BZ-TAKEN*/
   /* else             */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J),  "FETCH")
   /*    UBr to FETCH */

   /* BGE              */
   /* A <- Reg[rs1]    */,Label("BGE"),     Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* A <- (A < B)     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_0, ALU_SLT    , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* if not zero?     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_COPY_A , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_EZ), "BZ_TAKEN")
   /*   ubr to BZ-TAKEN*/
   /* else             */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J),  "FETCH")
   /*    UBr to FETCH */

   /* BGEU             */
   /* A <- Reg[rs1]    */,Label("BGEU"),    Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* A <- (A < B)     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_0, ALU_SLTU   , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N),  "X")
   /* if not zero?     */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_COPY_A , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_EZ), "BZ_TAKEN")
   /*   ubr to BZ-TAKEN*/
   /* else             */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J),  "FETCH")
   /*    UBr to FETCH */

   /* BZ-TAKEN        */
   /* note: PC register is actually
      holding PC+4 (see 'FETCH2), so we have to
      dec4 to get the correct behavior. */
   /* A  <- PC        */,Label("BZ_TAKEN"), Signals(Cat(CSR.N, LDIR_0, RS_PC, RWR_0, REN_1, LDA_1, LDB_X, ALU_X       , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* A  <- A - 4     */,                   Signals(Cat(CSR.N, LDIR_0, RS_X , RWR_0, REN_0, LDA_1, LDB_X, ALU_DEC_A_4 , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B  <- SSH1(Imm) */,                   Signals(Cat(CSR.N, LDIR_0, RS_X , RWR_0, REN_0, LDA_0, LDB_1, ALU_X       , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_B , IEN_1, UBR_N), "X")
   /* PC <- A + B     */,                   Signals(Cat(CSR.N, LDIR_0, RS_PC, RWR_1, REN_0, LDA_0, LDB_0, ALU_ADD     , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   /* UBr to FETCH   */
   // FENCE implemented as NOP
                        ,Label("FENCE")
                        ,Label("FENCE_I"),  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")



   /* --- CSR Instructions -------- */

   /* CSRRW             */
   /* Reg[CSR addr]<-Imm*/,Label("CSRRW"),  Signals(Cat(CSR.N, LDIR_0, RS_CA , RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* A <- Reg[rs1]     */,                 Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[CSR wdata]<-A */,                 Signals(Cat(CSR.N, LDIR_0, RS_CR , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* A <- CSR.RS[addr] */,                 Signals(Cat(CSR.W, LDIR_0, RS_CR , RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A      */,                 Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   /* UBr to FETCH      */

   /* CSRRC             */
   /* Reg[CSR addr]<-Imm*/,Label("CSRRC"),  Signals(Cat(CSR.N, LDIR_0, RS_CA , RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* A <- Reg[rs1]     */,                 Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[CSR wdata]<-A */,                 Signals(Cat(CSR.N, LDIR_0, RS_CR , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* A <- CSR.RS[addr] */,                 Signals(Cat(CSR.C, LDIR_0, RS_CR , RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A      */,                 Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   /* UBr to FETCH      */

   /* CSRRS             */
   /* Reg[CSR addr]<-Imm*/,Label("CSRRS"),  Signals(Cat(CSR.N, LDIR_0, RS_CA , RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* A <- Reg[rs1]     */,                 Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[CSR wdata]<-A */,                 Signals(Cat(CSR.N, LDIR_0, RS_CR , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* A <- CSR.RS[addr] */,                 Signals(Cat(CSR.S, LDIR_0, RS_CR , RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A      */,                 Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   /* UBr to FETCH      */

   /* CSRRWI            */
   /* Reg[CSR addr]<-Imm*/,Label("CSRRWI"), Signals(Cat(CSR.N, LDIR_0, RS_CA , RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* A <- Zext(ZImm)   */,                 Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_Z , IEN_1, UBR_N), "X")
   /* Reg[CSR wdata]<-A */,                 Signals(Cat(CSR.N, LDIR_0, RS_CR , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* A <- CSR.RS[addr] */,                 Signals(Cat(CSR.W, LDIR_0, RS_CR , RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A      */,                 Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   /* UBr to FETCH      */

   /* CSRRCI            */
   /* Reg[CSR addr]<-Imm*/,Label("CSRRCI"), Signals(Cat(CSR.N, LDIR_0, RS_CA , RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* A <- Zext(ZImm)   */,                 Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_Z , IEN_1, UBR_N), "X")
   /* Reg[CSR wdata]<-A */,                 Signals(Cat(CSR.N, LDIR_0, RS_CR , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* A <- CSR.RS[addr] */,                 Signals(Cat(CSR.C, LDIR_0, RS_CR , RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A      */,                 Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   /* UBr to FETCH      */

   /* CSRRSI            */
   /* Reg[CSR addr]<-Imm*/,Label("CSRRSI"), Signals(Cat(CSR.N, LDIR_0, RS_CA , RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* A <- Zext(ZImm)   */,                 Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_Z , IEN_1, UBR_N), "X")
   /* Reg[CSR wdata]<-A */,                 Signals(Cat(CSR.N, LDIR_0, RS_CR , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* A <- CSR.RS[addr] */,                 Signals(Cat(CSR.S, LDIR_0, RS_CR , RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* Reg[rd] <- A      */,                 Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_X, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   /* UBr to FETCH      */

   /* --- Privileged Instructions -------- */

   /*{ERET,ECALL,EBREAK}*/
   /* pass inst to CSR  */
   /* File and jmp to   */
   /* mepc.             */,Label("DRET")
                          ,Label("MRET")
                          ,Label("ECALL")
                          ,Label("EBREAK")
   /* Reg[CSR addr]<-Imm*/                , Signals(Cat(CSR.N, LDIR_0, RS_CA , RWR_1, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_I , IEN_1, UBR_N), "X")
   /* PC <- EVEC        */,                 Signals(Cat(CSR.I, LDIR_0, RS_PC , RWR_1, REN_0, LDA_0, LDB_X, ALU_EVEC   , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* WFI               */
   /* UBr to FETCH      */,Label("WFI")  ,  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_X, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")

   /* Custom complex instructions */


   /* A <- Reg[rs1]    */,Label("MOVN")  ,  Signals(Cat(CSR.N, LDIR_0, RS_RS1, RWR_0, REN_1, LDA_1, LDB_X, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* B <- Reg[rs2]    */,                  Signals(Cat(CSR.N, LDIR_0, RS_RS2, RWR_0, REN_1, LDA_0, LDB_1, ALU_X      , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_N), "X")
   /* if B == 0        */,                  Signals(Cat(CSR.N, LDIR_0, RS_X  , RWR_0, REN_0, LDA_0, LDB_0, ALU_COPY_B , AEN_0, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_EZ), "FETCH")
   /* ubr to FETCH     */
   /* Reg[rd] <- A     */,                  Signals(Cat(CSR.N, LDIR_0, RS_RD , RWR_1, REN_0, LDA_0, LDB_0, ALU_COPY_A , AEN_1, LDMA_X, MWR_0, MEN_0, MT_X , IS_X , IEN_0, UBR_J), "FETCH")
   /* ubr to FETCH     */

   /* TODO: Add the microcode for your custom instruction here */


 )
}
