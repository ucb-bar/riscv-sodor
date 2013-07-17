//**************************************************************************
// RISCV Processor Control Path
//--------------------------------------------------------------------------
//
// Christopher Celio

package Sodor
{

import Chisel._
import Node._

import Common._
import Common.Instructions._
import Constants._

class CtlToDatIo extends Bundle() 
{
   val stall     = Bool(OUTPUT)
   val pc_sel    = UFix(OUTPUT, 3) 
   val op1_sel   = UFix(OUTPUT, 1) 
   val op2_sel   = UFix(OUTPUT, 2) 
   val alu_fun   = UFix(OUTPUT, 4) 
   val wb_sel    = UFix(OUTPUT, 3) 
   val wa_sel    = Bool(OUTPUT) 
   val rf_wen    = Bool(OUTPUT) 
   val pcr_fcn   = UFix(OUTPUT, 3) 
   val exception = Bool(OUTPUT)
   val exc_cause = UFix(OUTPUT, 6)
   val eret      = Bool(OUTPUT)

   val debug_dmem_val = Bool(OUTPUT)
   val debug_dmem_typ = Bits(OUTPUT, MT_X.getWidth)
}

class CpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val dat  = new DatToCtlIo().flip()
   val ctl  = new CtlToDatIo()
   override def clone = { new CpathIo().asInstanceOf[this.type] }
}

                                                                                                                            
class CtlPath(implicit conf: SodorConfiguration) extends Mod
{                                                                                                                   //      mem flush/sync
  val io = new CpathIo()                                                                                            //      |   is eret
                                                                                                                    //      |   |  is syscall
   val csignals =                                                                                                   //      |   |  |  is privileged
      ListLookup(io.dat.inst,                                                                                       //      |   |  |  | 
                             List(N,  BR_N  , OP1_RS1, OP2_IMI, ALU_X   , WB_X , WA_X , REN_0, MEN_0, MWR_X, MT_X,  PCR_N, M_N, N, N, N),
               Array(        /*  val | BR   | op1   |  op2   | ALU     |  wb   | wa   | rf   | mem  | mem  | mask |  pcr |    | super-  */
                             /* inst | type |  sel  |   sel  |  fcn    |  sel  | sel  | wen  |  en  |  wr  | type |  fcn |    |  visor  */
                  LW      -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_ADD , WB_MEM, WA_RD, REN_1, MEN_1, M_XRD, MT_W,  PCR_N, M_N, N, N, N),
                  LH      -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_ADD , WB_MEM, WA_RD, REN_1, MEN_1, M_XRD, MT_H,  PCR_N, M_N, N, N, N),
                  LHU     -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_ADD , WB_MEM, WA_RD, REN_1, MEN_1, M_XRD, MT_HU, PCR_N, M_N, N, N, N),
                  LB      -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_ADD , WB_MEM, WA_RD, REN_1, MEN_1, M_XRD, MT_B,  PCR_N, M_N, N, N, N),
                  LBU     -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_ADD , WB_MEM, WA_RD, REN_1, MEN_1, M_XRD, MT_BU, PCR_N, M_N, N, N, N),
                  SW      -> List(Y, BR_N  , OP1_RS1, OP2_IMB, ALU_ADD , WB_X  , WA_X , REN_0, MEN_1, M_XWR, MT_W,  PCR_N, M_N, N, N, N),
                  SH      -> List(Y, BR_N  , OP1_RS1, OP2_IMB, ALU_ADD , WB_X  , WA_X , REN_0, MEN_1, M_XWR, MT_H,  PCR_N, M_N, N, N, N),
                  SB      -> List(Y, BR_N  , OP1_RS1, OP2_IMB, ALU_ADD , WB_X  , WA_X , REN_0, MEN_1, M_XWR, MT_B,  PCR_N, M_N, N, N, N),
                  LUI     -> List(Y, BR_N  , OP1_X  , OP2_UI , ALU_COPY2,WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  
                  ADDI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_ADD , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  ANDI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_AND , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  ORI     -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_OR  , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  XORI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_XOR , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SLTI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_SLT , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SLTIU   -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_SLTU, WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SLLI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_SLL , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SRAI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_SRA , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SRLI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_SRL , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  
                  SLL     -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_SLL , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  ADD     -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_ADD , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SUB     -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_SUB , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SLT     -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_SLT , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SLTU    -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_SLTU, WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  riscvAND-> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_AND , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  riscvOR -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_OR  , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  riscvXOR-> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_XOR , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SRA     -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_SRA , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SRL     -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_SRL , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  
                  AUIPC   -> List(Y, BR_N  , OP1_PC , OP2_UI , ALU_ADD , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  
                  J       -> List(Y, BR_J  , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  JAL     -> List(Y, BR_J  , OP1_X  , OP2_X  , ALU_X   , WB_PC4, WA_RA, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  JALR_C  -> List(Y, BR_JR , OP1_X  , OP2_X  , ALU_X   , WB_PC4, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  JALR_R  -> List(Y, BR_JR , OP1_X  , OP2_X  , ALU_X   , WB_PC4, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  JALR_J  -> List(Y, BR_JR , OP1_X  , OP2_X  , ALU_X   , WB_PC4, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  BEQ     -> List(Y, BR_EQ , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  BNE     -> List(Y, BR_NE , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  BGE     -> List(Y, BR_GE , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  BGEU    -> List(Y, BR_GEU, OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  BLT     -> List(Y, BR_LT , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  BLTU    -> List(Y, BR_LTU, OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  
                  MTPCR   -> List(Y, BR_N  , OP1_X  , OP2_RS2, ALU_COPY2,WB_PCR, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_T, M_N, N, N, Y),
                  MFPCR   -> List(Y, BR_N  , OP1_X  , OP2_RS2, ALU_X   , WB_PCR, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_F, M_N, N, N, Y),
                  CLEARPCR-> List(Y, BR_N  , OP1_X  , OP2_IMI, ALU_COPY2,WB_PCR, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_C, M_N, N, N, Y),
                  SETPCR  -> List(Y, BR_N  , OP1_X  , OP2_IMI, ALU_COPY2,WB_PCR, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_S, M_N, N, N, Y),

                  SYSCALL -> List(Y, BR_N  , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , MT_X,  PCR_N, M_FD, N, Y, N), 
                  ERET    -> List(Y, BR_N  , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , MT_X,  PCR_N, M_FD, Y, N, Y), 
                  FENCE_I -> List(Y, BR_N  , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , MT_X,  PCR_N, M_SI, N, N, N), 
                  FENCE   -> List(Y, BR_N  , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_1, M_X  , MT_X,  PCR_N, M_SD, N, N, N),
                  CFLUSH  -> List(Y, BR_N  , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , MT_X,  PCR_N, M_FA, N, N, N),
                  // we are already sequentially consistent, so no need to honor the fence instruction
                  
                  RDTIME  -> List(Y, BR_N  , OP1_X  , OP2_X  , ALU_X   , WB_TSC, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  RDCYCLE -> List(Y, BR_N  , OP1_X  , OP2_X  , ALU_X   , WB_TSC, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  RDINSTRET->List(Y, BR_N  , OP1_X  , OP2_X  , ALU_X   , WB_IRT, WA_RD, REN_1, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N)
                  ))

   // Put these control signals in variables
   val cs_inst_val :: cs_br_type :: cs_op1_sel :: cs_op2_sel :: cs_alu_fun :: cs_wb_sel :: cs_wa_sel :: cs_rf_wen :: cs_mem_en :: cs_mem_fcn :: cs_msk_sel :: cs_pcr_fcn :: cs_sync_fcn :: cs_eret :: cs_syscall :: cs_privileged ::  Nil = csignals

   val exception = Bool() 
                           
   // Branch Logic   
   val ctrl_pc_sel = Mux(exception || cs_eret.toBool ,  PC_EXC,
                     Mux(cs_br_type === BR_N  ,  PC_4,
                     Mux(cs_br_type === BR_NE ,  Mux(!io.dat.br_eq,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_EQ ,  Mux( io.dat.br_eq,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_GE ,  Mux(!io.dat.br_lt,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_GEU,  Mux(!io.dat.br_ltu, PC_BR, PC_4),
                     Mux(cs_br_type === BR_LT ,  Mux( io.dat.br_lt,  PC_BR, PC_4),
                     Mux(cs_br_type === BR_LTU,  Mux( io.dat.br_ltu, PC_BR, PC_4),
                     Mux(cs_br_type === BR_J  ,  PC_J,
                     Mux(cs_br_type === BR_JR ,  PC_JR,
                     PC_4
                     ))))))))))
                           
   val stall =  !io.imem.resp.valid || !((cs_mem_en.toBool && io.dmem.resp.valid) || !cs_mem_en)

   io.ctl.stall    := stall
   io.ctl.pc_sel   := ctrl_pc_sel
   io.ctl.op1_sel  := cs_op1_sel
   io.ctl.op2_sel  := cs_op2_sel
   io.ctl.alu_fun  := cs_alu_fun
   io.ctl.wb_sel   := cs_wb_sel
   io.ctl.wa_sel   := cs_wa_sel.toBool
   io.ctl.rf_wen   := Mux(stall, Bool(false), cs_rf_wen.toBool)
   io.ctl.pcr_fcn  := Mux(stall, PCR_N, cs_pcr_fcn)
   
   // Memory Requests
   io.imem.req.valid    := Bool(true)
   io.imem.req.bits.fcn := M_XRD
   io.imem.req.bits.typ := MT_WU

   io.dmem.req.valid    := cs_mem_en.toBool
   io.dmem.req.bits.fcn := cs_mem_fcn
   io.dmem.req.bits.typ := cs_msk_sel

   // Memory Flushes & Syncs (which are only handled via flushes in this pipeline)
   // scratchpad has no flushes
//   io.imem.flush   := (cs_sync_fcn === M_SI) || (cs_sync_fcn === M_FA)
//   io.dmem.flush   := (cs_sync_fcn === M_SI) || (cs_sync_fcn === M_FA) || (cs_sync_fcn === M_FD)
   
   // Exception Handling
   io.ctl.eret      := cs_eret.toBool
   io.ctl.exception := exception
   
   // executing ERET when traps are enabled causes illegal istruction exception
   val exc_illegal = (!cs_inst_val && io.imem.resp.valid) ||
                     (cs_eret.toBool && io.dat.status.et)
   
   val exc_priv    = cs_privileged.toBool && !io.dat.status.s
    
   exception       := cs_syscall.toBool ||
                      exc_illegal ||
                      exc_priv

                 
   io.ctl.exc_cause := Mux(exc_illegal, EXCEPTION_ILLEGAL,
                       Mux(exc_priv,    EXCEPTION_PRIVILEGED,
                       Mux(cs_syscall.toBool,  EXCEPTION_SYSCALL,
                                        UFix(0,5))))
   
   // only here to thread ctrl signals to printf in dpath.scala                  
   io.ctl.debug_dmem_val := cs_mem_en.toBool
   io.ctl.debug_dmem_typ := cs_msk_sel
}

}
