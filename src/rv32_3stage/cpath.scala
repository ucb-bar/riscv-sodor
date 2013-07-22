//**************************************************************************
// RISCV Processor Control Path
//--------------------------------------------------------------------------


package Sodor
{

import Chisel._
import Node._

import Common._
import Common.Instructions._
import Constants._
import ALU._

class CtrlSignals extends Bundle() 
{
   val if_stall  = Bool()    // hazard on memory port: stall IF
   val if_kill   = Bool()    // squash IF stage (branch mispredict)
   val exe_kill  = Bool()    // squash EX stage (exception/eret occurred)
   val pc_sel    = UInt(width = PC_4.getWidth) 
   val brjmp_sel = Bool()
   val op1_sel   = UInt(width = OP1_X.getWidth) 
   val op2_sel   = UInt(width = OP2_X.getWidth) 
   val alu_fun   = Bits(width = SZ_ALU_FN) 
   val wb_sel    = UInt(width = WB_X.getWidth) 
   val wa_sel    = Bool() 
   val rf_wen    = Bool() 
   val bypassable= Bool()     // instruction's result can be bypassed
   val pcr_fcn   = UInt(width = 3) 

   val dmem_val  = Bool()
   val dmem_fcn  = Bits(width = M_X.getWidth)
   val dmem_typ  = Bits(width = MT_X.getWidth)
 
   // confusing point: these three signals come out in WB
   val exception = Bool()   
   val exc_cause = UInt(width = 6)
   val eret      = Bool()
}

class CpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val dat  = new DatToCtlIo().flip()
   val ctl  = new CtrlSignals().asOutput
   override def clone = { new CpathIo().asInstanceOf[this.type] }
}

                                                                                                                            
class CtlPath(implicit conf: SodorConfiguration) extends Module
{                            //                                                                                                    mem flush/sync
  val io = new CpathIo()     //                                                                                                    |   is eret
                             //                                                                                                    |   |  is syscall
   val csignals =            //              is jmp?                                               bypassable?                     |   |  |  is privileged
      ListLookup(io.dat.inst,//              |                                                     |                               |   |  |  | 
                             List(N,  BR_N , N, OP1_RS1, OP2_IMI, FN_X   , WB_X , WA_X , REN_0, N, MEN_0, MWR_X, MT_X,  PCR_N, M_N, N, N, N),
               Array(        /*  val | BR   |  | op1   |  op2   |  ALU   |  wb   | wa   | rf   |  | mem  | mem  | mask |  pcr |    |   */
                             /* inst | type |  |  sel  |   sel  |   fcn  |  sel  | sel  | wen  |  |  en  |  wr  | type |  fcn |    |   */
                  LW      -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_ADD , WB_MEM, WA_RD, REN_1, N, MEN_1, M_XRD, MT_W,  PCR_N, M_N, N, N, N),
                  LH      -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_ADD , WB_MEM, WA_RD, REN_1, N, MEN_1, M_XRD, MT_H,  PCR_N, M_N, N, N, N),
                  LHU     -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_ADD , WB_MEM, WA_RD, REN_1, N, MEN_1, M_XRD, MT_HU, PCR_N, M_N, N, N, N),
                  LB      -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_ADD , WB_MEM, WA_RD, REN_1, N, MEN_1, M_XRD, MT_B,  PCR_N, M_N, N, N, N),
                  LBU     -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_ADD , WB_MEM, WA_RD, REN_1, N, MEN_1, M_XRD, MT_BU, PCR_N, M_N, N, N, N),
                  SW      -> List(Y, BR_N  , N, OP1_RS1, OP2_IMB, FN_ADD , WB_X  , WA_X , REN_0, N, MEN_1, M_XWR, MT_W,  PCR_N, M_N, N, N, N),
                  SH      -> List(Y, BR_N  , N, OP1_RS1, OP2_IMB, FN_ADD , WB_X  , WA_X , REN_0, N, MEN_1, M_XWR, MT_H,  PCR_N, M_N, N, N, N),
                  SB      -> List(Y, BR_N  , N, OP1_RS1, OP2_IMB, FN_ADD , WB_X  , WA_X , REN_0, N, MEN_1, M_XWR, MT_B,  PCR_N, M_N, N, N, N),
                  
                  LUI     -> List(Y, BR_N  , N, OP1_X  , OP2_UI , FN_OP2 , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  
                  ADDI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_ADD , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  ANDI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_AND , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  ORI     -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_OR  , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  XORI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_XOR , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SLTI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_SLT , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SLTIU   -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_SLTU, WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SLLI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_SL  , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SRAI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_SRA , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SRLI    -> List(Y, BR_N  , N, OP1_RS1, OP2_IMI, FN_SR  , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  
                  SLL     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2, FN_SL  , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  ADD     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2, FN_ADD , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SUB     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2, FN_SUB , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SLT     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2, FN_SLT , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SLTU    -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2, FN_SLTU, WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  riscvAND-> List(Y, BR_N  , N, OP1_RS1, OP2_RS2, FN_AND , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  riscvOR -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2, FN_OR  , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  riscvXOR-> List(Y, BR_N  , N, OP1_RS1, OP2_RS2, FN_XOR , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SRA     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2, FN_SRA , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  SRL     -> List(Y, BR_N  , N, OP1_RS1, OP2_RS2, FN_SR  , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  
                  AUIPC   -> List(Y, BR_N  , N, OP1_PC , OP2_UI , FN_ADD , WB_ALU, WA_RD, REN_1, Y, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  
                  J       -> List(Y, BR_J  , Y, OP1_X  , OP2_X  , FN_X   , WB_X  , WA_X , REN_0, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  JAL     -> List(Y, BR_J  , Y, OP1_RS1, OP2_IMI, FN_ADD , WB_PC4, WA_RA, REN_1, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  JALR_C  -> List(Y, BR_JR , N, OP1_RS1, OP2_IMI, FN_ADD , WB_PC4, WA_RD, REN_1, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  JALR_R  -> List(Y, BR_JR , N, OP1_RS1, OP2_IMI, FN_ADD , WB_PC4, WA_RD, REN_1, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  JALR_J  -> List(Y, BR_JR , N, OP1_RS1, OP2_IMI, FN_ADD , WB_PC4, WA_RD, REN_1, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  BEQ     -> List(Y, BR_EQ , N, OP1_X  , OP2_X  , FN_X   , WB_X  , WA_X , REN_0, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  BNE     -> List(Y, BR_NE , N, OP1_X  , OP2_X  , FN_X   , WB_X  , WA_X , REN_0, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  BGE     -> List(Y, BR_GE , N, OP1_X  , OP2_X  , FN_X   , WB_X  , WA_X , REN_0, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  BGEU    -> List(Y, BR_GEU, N, OP1_X  , OP2_X  , FN_X   , WB_X  , WA_X , REN_0, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  BLT     -> List(Y, BR_LT , N, OP1_X  , OP2_X  , FN_X   , WB_X  , WA_X , REN_0, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  BLTU    -> List(Y, BR_LTU, N, OP1_X  , OP2_X  , FN_X   , WB_X  , WA_X , REN_0, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  
                  MTPCR   -> List(Y, BR_N  , N, OP1_X  , OP2_RS2, FN_OP2 , WB_PCR, WA_RD, REN_1, N, MEN_0, M_X  , MT_X,  PCR_T, M_N, N, N, Y),
                  MFPCR   -> List(Y, BR_N  , N, OP1_X  , OP2_RS2, FN_X   , WB_PCR, WA_RD, REN_1, N, MEN_0, M_X  , MT_X,  PCR_F, M_N, N, N, Y),
                  CLEARPCR-> List(Y, BR_N  , N, OP1_X  , OP2_IMI, FN_OP2 , WB_PCR, WA_RD, REN_1, N, MEN_0, M_X  , MT_X,  PCR_C, M_N, N, N, Y),
                  SETPCR  -> List(Y, BR_N  , N, OP1_X  , OP2_IMI, FN_OP2 , WB_PCR, WA_RD, REN_1, N, MEN_0, M_X  , MT_X,  PCR_S, M_N, N, N, Y),

                  SYSCALL -> List(Y, BR_N  , N, OP1_X  , OP2_X  , FN_X   , WB_X  , WA_X , REN_0, N, MEN_0, M_X  , MT_X,  PCR_N, M_FD, N, Y, N), 
                  ERET    -> List(Y, BR_N  , N, OP1_X  , OP2_X  , FN_X   , WB_X  , WA_X , REN_0, N, MEN_0, M_X  , MT_X,  PCR_N, M_FD, Y, N, Y), 
                  FENCE_I -> List(Y, BR_N  , N, OP1_X  , OP2_X  , FN_X   , WB_X  , WA_X , REN_0, N, MEN_0, M_X  , MT_X,  PCR_N, M_SI, N, N, N), 
                  FENCE   -> List(Y, BR_N  , N,  OP1_X  , OP2_X  , FN_X  , WB_X  , WA_X , REN_0, N, MEN_1, M_X  , MT_X,  PCR_N, M_SD, N, N, N),
                  CFLUSH  -> List(Y, BR_N  , N,  OP1_X  , OP2_X  , FN_X  , WB_X  , WA_X , REN_0, N, MEN_0, M_X  , MT_X,  PCR_N, M_FA, N, N, N),
                                                                  
                  RDTIME  -> List(Y, BR_N  , N, OP1_X  , OP2_X  , FN_X   , WB_TSC, WA_RD, REN_1, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  RDCYCLE -> List(Y, BR_N  , N, OP1_X  , OP2_X  , FN_X   , WB_TSC, WA_RD, REN_1, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N),
                  RDINSTRET->List(Y, BR_N  , N, OP1_X  , OP2_X  , FN_X   , WB_IRT, WA_RD, REN_1, N, MEN_0, M_X  , MT_X,  PCR_N, M_N, N, N, N)
                  // we are already sequentially consistent, so no need to honor the fence instruction
                  ))

   // Put these control signals in variables
   val cs_inst_val :: cs_br_type :: cs_brjmp_sel :: cs_op1_sel :: cs_op2_sel :: cs_alu_fun :: cs_wb_sel :: cs_wa_sel :: cs_rf_wen :: cs_bypassable :: cs_mem_en :: cs_mem_fcn :: cs_msk_sel :: cs_pcr_fcn :: cs_sync_fcn :: cs_eret :: cs_syscall :: cs_privileged ::  Nil = csignals

                           
   // Branch Logic   
   val take_evec = Bool() // jump to the pcr.io.evec target 
                          // (for exceptions or eret, taken in the WB stage)
   val exe_exception = Bool() 

   val ctrl_pc_sel = Mux(take_evec            ,  PC_EXC,
                     Mux(cs_br_type === BR_N  ,  PC_4,
                     Mux(cs_br_type === BR_NE ,  Mux(!io.dat.br_eq,  PC_BRJMP, PC_4),
                     Mux(cs_br_type === BR_EQ ,  Mux( io.dat.br_eq,  PC_BRJMP, PC_4),
                     Mux(cs_br_type === BR_GE ,  Mux(!io.dat.br_lt,  PC_BRJMP, PC_4),
                     Mux(cs_br_type === BR_GEU,  Mux(!io.dat.br_ltu, PC_BRJMP, PC_4),
                     Mux(cs_br_type === BR_LT ,  Mux( io.dat.br_lt,  PC_BRJMP, PC_4),
                     Mux(cs_br_type === BR_LTU,  Mux( io.dat.br_ltu, PC_BRJMP, PC_4),
                     Mux(cs_br_type === BR_J  ,  PC_BRJMP,
                     Mux(cs_br_type === BR_JR ,  PC_JR,
                     PC_4
                     ))))))))))
                           
//   val stall =  !io.imem.resp.valid || !((cs_mem_en && io.dmem.resp.valid) || !cs_mem_en)
   // not using caches, not using back pressure just yet
   // the above line is wrong though: cs_mem_en and resp.valid are in different stages

   val ifkill = !(ctrl_pc_sel === PC_4) 


   io.ctl.if_stall   := !io.imem.resp.valid
   io.ctl.if_kill    := ifkill
   io.ctl.exe_kill   := take_evec
   io.ctl.pc_sel     := ctrl_pc_sel
   io.ctl.brjmp_sel  := cs_brjmp_sel.toBool
   io.ctl.op1_sel    := cs_op1_sel
   io.ctl.op2_sel    := cs_op2_sel
   io.ctl.alu_fun    := cs_alu_fun
   io.ctl.wb_sel     := cs_wb_sel
   io.ctl.wa_sel     := cs_wa_sel.toBool
   io.ctl.rf_wen     := Mux(exe_exception, Bool(false), cs_rf_wen.toBool)
   io.ctl.bypassable := cs_bypassable.toBool
   io.ctl.pcr_fcn    := Mux(exe_exception, PCR_N, cs_pcr_fcn)
   
   // Memory Requests
   io.imem.req.valid    := Bool(true)
   io.imem.req.bits.fcn := M_XRD
   io.imem.req.bits.typ := MT_WU

   io.ctl.dmem_val   := cs_mem_en.toBool
   io.ctl.dmem_fcn   := cs_mem_fcn
   io.ctl.dmem_typ   := cs_msk_sel


   //-------------------------------
   // Exception Handling
   // catch exceptions, eret in execute stage
   // address exceptions/eret in WB stage

   val wb_exception = RegReset(Bool(false))
   val wb_exc_cause = Reg(UInt(width = 5))
   
   
   // executing ERET when traps are enabled causes illegal istruction exception
   val exc_illegal = (!cs_inst_val) ||
                     (cs_eret.toBool && io.dat.status.et)
   
   val exc_priv    = cs_privileged.toBool && !io.dat.status.s
    
   exe_exception  := ( cs_syscall.toBool || 
                       exc_illegal ||
                       exc_priv ||
                       cs_eret.toBool // i'm cheating here, treating eret like an exception (same thing re: pc_sel)
                     ) && 
                     !io.ctl.exe_kill // clear exceptions behind us in the pipeline
                 
   wb_exception   := exe_exception

   wb_exc_cause   := Mux(exc_illegal, EXCEPTION_ILLEGAL,
                     Mux(exc_priv,    EXCEPTION_PRIVILEGED,
                     Mux(cs_syscall.toBool,  EXCEPTION_SYSCALL,
                     Mux(cs_eret.toBool   ,  EXC_RETURN, // let's cheat, and treat "eret" like an exception
                                      UInt(0,5)))))


   take_evec        := wb_exception
   io.ctl.eret      := wb_exception && (wb_exc_cause === EXC_RETURN)
   io.ctl.exception := wb_exception && (wb_exc_cause != EXC_RETURN)
   io.ctl.exc_cause := wb_exc_cause
   
}

}
