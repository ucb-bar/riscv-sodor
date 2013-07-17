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
   val stall    = Bool(OUTPUT)
   val if_kill  = Bool(OUTPUT) 
   val pc_sel   = UFix(OUTPUT, 3)
   val op1_sel  = UFix(OUTPUT, 1)
   val op2_sel  = UFix(OUTPUT, 2)
   val alu_fun  = UFix(OUTPUT, 5)
   val wb_sel   = UFix(OUTPUT, 2)
   val wa_sel   = Bool(OUTPUT) 
   val rf_wen   = Bool(OUTPUT) 
   val pcr_fcn  = UFix(OUTPUT, 2) 
}

class CpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val dat  = new DatToCtlIo().flip()
   val ctl  = new CtlToDatIo()
}


class CtlPath(implicit conf: SodorConfiguration) extends Mod
{
  val io = new CpathIo();

   val csignals = 
      ListLookup(io.dat.inst,                                   
                             List(N,  BR_N , OP1_RS1, OP2_IMI, ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
               Array(       /*  val |  BR  |  op1   |  op2   | ALU     |  wb   | wa   | rf   | mem  | mem  | pcr  */
                            /* inst | type |   sel  |   sel  |  fcn    |  sel  | sel  | wen  |  en  |  wr  | fcn  */
                  LW      -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_ADD , WB_MEM, WA_RD, REN_1, MEN_1, M_XRD, PCR_N),
                  SW      -> List(Y, BR_N  , OP1_RS1, OP2_IMB, ALU_ADD , WB_X  , WA_X , REN_0, MEN_1, M_XWR, PCR_N),
                  LUI     -> List(Y, BR_N  , OP1_X  , OP2_UI , ALU_COPY2,WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  
                  ADDI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_ADD , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  ANDI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_AND , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  ORI     -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_OR  , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  XORI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_XOR , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SLTI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_SLT , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SLTIU   -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_SLTU, WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SLLI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_SLL , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SRAI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_SRA , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SRLI    -> List(Y, BR_N  , OP1_RS1, OP2_IMI, ALU_SRL , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  
                  SLL     -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_SLL , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  ADD     -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_ADD , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SUB     -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_SUB , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SLT     -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_SLT , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SLTU    -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_SLTU, WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  riscvAND-> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_AND , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  riscvOR -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_OR  , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  riscvXOR-> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_XOR , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SRA     -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_SRA , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SRL     -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_SRL , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  AUIPC   -> List(Y, BR_N  , OP1_PC , OP2_UI , ALU_ADD , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  
                  J       -> List(Y, BR_J  , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  JAL     -> List(Y, BR_J  , OP1_X  , OP2_X  , ALU_X   , WB_PC4, WA_RA, REN_1, MEN_0, M_X  , PCR_N),
                  JALR_C  -> List(Y, BR_JR , OP1_X  , OP2_X  , ALU_X   , WB_PC4, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  JALR_R  -> List(Y, BR_JR , OP1_X  , OP2_X  , ALU_X   , WB_PC4, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  JALR_J  -> List(Y, BR_JR , OP1_X  , OP2_X  , ALU_X   , WB_PC4, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  BEQ     -> List(Y, BR_EQ , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  BNE     -> List(Y, BR_NE , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  BGE     -> List(Y, BR_GE , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  BGEU    -> List(Y, BR_GEU, OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  BLT     -> List(Y, BR_LT , OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  BLTU    -> List(Y, BR_LTU, OP1_X  , OP2_X  , ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  MTPCR   -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_COPY2,WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_T),
                  MFPCR   -> List(Y, BR_N  , OP1_RS1, OP2_RS2, ALU_X   , WB_PCR, WA_RD, REN_1, MEN_0, M_X  , PCR_F)
                  ));

   // Put these control signals in variables
   val cs_val_inst :: cs_br_type :: cs_op1_sel :: cs_op2_sel :: cs_alu_fun :: cs_wb_sel :: cs_wa_sel :: cs_rf_wen :: cs_mem_en :: cs_mem_fcn :: cs_pcr_fcn :: Nil = csignals;

 
   // Branch Logic   
   val ctrl_pc_sel
      = Lookup(cs_br_type, UFix(0, 3), 
            Array(   BR_N  -> PC_4, 
                     BR_NE -> Mux(!io.dat.br_eq,  PC_BR, PC_4),
                     BR_EQ -> Mux( io.dat.br_eq,  PC_BR, PC_4),
                     BR_GE -> Mux(!io.dat.br_lt,  PC_BR, PC_4),
                     BR_GEU-> Mux(!io.dat.br_ltu, PC_BR, PC_4),
                     BR_LT -> Mux( io.dat.br_lt,  PC_BR, PC_4),
                     BR_LTU-> Mux( io.dat.br_ltu, PC_BR, PC_4),
                     BR_J  -> PC_J,
                     BR_JR -> PC_JR
                     ));


   // stall entire pipeline on I$ or D$ miss
   val stall = !io.imem.resp.valid || !((cs_mem_en.toBool && io.dmem.resp.valid) || !cs_mem_en)
   
   val ifkill = !(ctrl_pc_sel === PC_4) 
   
   io.ctl.stall      := stall
   io.ctl.if_kill    := ifkill
   io.ctl.pc_sel     := ctrl_pc_sel
   io.ctl.op1_sel    := cs_op1_sel
   io.ctl.op2_sel    := cs_op2_sel
   io.ctl.alu_fun    := cs_alu_fun
   io.ctl.wb_sel     := cs_wb_sel
   io.ctl.wa_sel     := cs_wa_sel.toBool
   io.ctl.rf_wen     := Mux(stall, Bool(false), cs_rf_wen.toBool)
   io.ctl.pcr_fcn    := Mux(stall, PCR_N,       cs_pcr_fcn)
   
   io.imem.req.valid    := Bool(true)
   io.imem.req.bits.fcn := M_XRD
   io.imem.req.bits.typ := MT_WU

   io.dmem.req.valid    := cs_mem_en.toBool
   io.dmem.req.bits.fcn := cs_mem_fcn
   io.dmem.req.bits.typ := MT_WU // XXX for now, later add support for sub mem ops
   
}

}
