//**************************************************************************
// RISCV Processor 5-Stage Control Path
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2012 Jan 20
//
// Supports both a fully-bypassed datapath (with stalls for load-use), and a
// fully interlocked (no bypass) datapath that stalls for all hazards.

package Sodor
{

import Chisel._
import Node._

import Constants._
import Common._
import Common.Instructions._

class CtlToDatIo extends Bundle() 
{
   val dec_stall  = Bool(OUTPUT)    // stall IF/DEC stages (due to hazards)
   val full_stall = Bool(OUTPUT)    // stall entire pipeline (due to D$ misses)
   val exe_pc_sel = UFix(OUTPUT, 2)
   val br_type    = UFix(OUTPUT, 4)
   val if_kill    = Bool(OUTPUT) 
   val dec_kill   = Bool(OUTPUT) 
   val op1_sel    = UFix(OUTPUT, 1)
   val op2_sel    = UFix(OUTPUT, 3)
   val alu_fun    = UFix(OUTPUT, 4)
   val wb_sel     = UFix(OUTPUT, 2)
   val wa_sel     = Bool(OUTPUT) 
   val rf_wen     = Bool(OUTPUT) 
   val mem_val    = Bool(OUTPUT)
   val mem_fcn    = Bits(OUTPUT, 2)
   val pcr_fcn    = UFix(OUTPUT, 2)
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
{
  val io = new CpathIo()

   val csignals = 
      ListLookup(io.dat.dec_inst,                                   
                             List(N, BR_N  , OP1_X  , OP2_X    , OEN_0, OEN_0, ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
               Array(       /* val  |  BR  |  op1   |   op2    |  R1  |  R2  |  ALU    |  wb   | wa   | rf   | mem  | mem  | pcr  */
                            /* inst | type |   sel  |    sel   |  oen |  oen |   fcn   |  sel  | sel  | wen  |  en  |  wr  | wen  */
                  LW      -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_ADD , WB_MEM, WA_RD, REN_1, MEN_1, M_XRD, PCR_N),
                  SW      -> List(Y, BR_N  , OP1_RS1, OP2_BTYPE, OEN_1, OEN_1, ALU_ADD , WB_X  , WA_X , REN_0, MEN_1, M_XWR, PCR_N),
                  
                  AUIPC   -> List(Y, BR_N  , OP1_PC , OP2_LTYPE, OEN_0, OEN_0,ALU_ADD  ,WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  LUI     -> List(Y, BR_N  , OP1_X  , OP2_LTYPE, OEN_0, OEN_0,ALU_COPY_2,WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  
                  ADDI    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_ADD , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  ANDI    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_AND , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  ORI     -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_OR  , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  XORI    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_XOR , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SLTI    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_SLT , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SLTIU   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_SLTU, WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SLLI    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_SLL , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SRAI    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_SRA , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SRLI    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_SRL , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  
                  SLL     -> List(Y, BR_N  , OP1_RS1, OP2_RS2  , OEN_1, OEN_1, ALU_SLL , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  ADD     -> List(Y, BR_N  , OP1_RS1, OP2_RS2  , OEN_1, OEN_1, ALU_ADD , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SUB     -> List(Y, BR_N  , OP1_RS1, OP2_RS2  , OEN_1, OEN_1, ALU_SUB , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SLT     -> List(Y, BR_N  , OP1_RS1, OP2_RS2  , OEN_1, OEN_1, ALU_SLT , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SLTU    -> List(Y, BR_N  , OP1_RS1, OP2_RS2  , OEN_1, OEN_1, ALU_SLTU, WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  riscvAND-> List(Y, BR_N  , OP1_RS1, OP2_RS2  , OEN_1, OEN_1, ALU_AND , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  riscvOR -> List(Y, BR_N  , OP1_RS1, OP2_RS2  , OEN_1, OEN_1, ALU_OR  , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  riscvXOR-> List(Y, BR_N  , OP1_RS1, OP2_RS2  , OEN_1, OEN_1, ALU_XOR , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SRA     -> List(Y, BR_N  , OP1_RS1, OP2_RS2  , OEN_1, OEN_1, ALU_SRA , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  SRL     -> List(Y, BR_N  , OP1_RS1, OP2_RS2  , OEN_1, OEN_1, ALU_SRL , WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  
                  J       -> List(Y, BR_J  , OP1_RS1, OP2_JTYPE, OEN_0, OEN_0, ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  JAL     -> List(Y, BR_J  , OP1_RS1, OP2_JTYPE, OEN_0, OEN_0, ALU_X   , WB_PC4, WA_RA, REN_1, MEN_0, M_X  , PCR_N),
                  JALR_C  -> List(Y, BR_JR , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_X   , WB_PC4, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  JALR_R  -> List(Y, BR_JR , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_X   , WB_PC4, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  JALR_J  -> List(Y, BR_JR , OP1_RS1, OP2_ITYPE, OEN_1, OEN_0, ALU_X   , WB_PC4, WA_RD, REN_1, MEN_0, M_X  , PCR_N),
                  BEQ     -> List(Y, BR_EQ , OP1_RS1, OP2_BTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  BNE     -> List(Y, BR_NE , OP1_RS1, OP2_BTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  BGE     -> List(Y, BR_GE , OP1_RS1, OP2_BTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  BGEU    -> List(Y, BR_GEU, OP1_RS1, OP2_BTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  BLT     -> List(Y, BR_LT , OP1_RS1, OP2_BTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  BLTU    -> List(Y, BR_LTU, OP1_RS1, OP2_BTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , WA_X , REN_0, MEN_0, M_X  , PCR_N),
                  MTPCR   -> List(Y, BR_N  , OP1_RS1, OP2_RS2  , OEN_1, OEN_1, ALU_COPY_2,WB_ALU, WA_RD, REN_1, MEN_0, M_X  , PCR_T),
                  MFPCR   -> List(Y, BR_N  , OP1_RS1, OP2_RS2  , OEN_1, OEN_1, ALU_X   , WB_PCR, WA_RD, REN_1, MEN_0, M_X  , PCR_F)
                  ))

   // Put these control signals in variables
   val cs_val_inst :: cs_br_type :: cs_op1_sel :: cs_op2_sel :: cs_rs1_oen :: cs_rs2_oen :: cs_alu_fun :: cs_wb_sel :: cs_wa_sel :: cs_rf_wen :: cs_mem_en :: cs_mem_fcn :: cs_pcr_fcn :: Nil = csignals

 
   // Branch Logic   
   val ctrl_exe_pc_sel
      = Lookup(io.dat.exe_br_type, UFix(0, 3), 
            Array(   BR_N  -> PC_PLUS4, 
                     BR_NE -> Mux(!io.dat.exe_br_eq,  PC_BRJMP, PC_PLUS4),
                     BR_EQ -> Mux( io.dat.exe_br_eq,  PC_BRJMP, PC_PLUS4),
                     BR_GE -> Mux(!io.dat.exe_br_lt,  PC_BRJMP, PC_PLUS4),
                     BR_GEU-> Mux(!io.dat.exe_br_ltu, PC_BRJMP, PC_PLUS4),
                     BR_LT -> Mux( io.dat.exe_br_lt,  PC_BRJMP, PC_PLUS4),
                     BR_LTU-> Mux( io.dat.exe_br_ltu, PC_BRJMP, PC_PLUS4),
                     BR_J  -> PC_BRJMP,
                     BR_JR -> PC_JALR
                     ))

   val ifkill  = (ctrl_exe_pc_sel != PC_PLUS4) || !io.imem.resp.valid
   val deckill = (ctrl_exe_pc_sel != PC_PLUS4) 

   
   
   // Stall Signal Logic
   val stall   = Bool()
   
   val dec_rs1_addr = io.dat.dec_inst(26, 22).toUFix
   val dec_rs2_addr = io.dat.dec_inst(21, 17).toUFix
   val dec_wbaddr  = Mux(cs_wa_sel.toBool, io.dat.dec_inst(31, 27).toUFix, RA)
   val dec_rs1_oen = Mux(deckill, Bool(false), cs_rs1_oen.toBool)
   val dec_rs2_oen = Mux(deckill, Bool(false), cs_rs2_oen.toBool)

   val exe_reg_wbaddr      = Reg(UFix())
   val mem_reg_wbaddr      = Reg(UFix())
   val wb_reg_wbaddr       = Reg(UFix())
   val exe_reg_ctrl_rf_wen = RegReset(Bool(false))
   val mem_reg_ctrl_rf_wen = RegReset(Bool(false))
   val wb_reg_ctrl_rf_wen  = RegReset(Bool(false))
   
   // TODO rename stall==hazard_stall full_stall == cmiss_stall
   val full_stall = Bool()
   when (!stall && !full_stall)
   {
      when (deckill)
      {
         exe_reg_wbaddr      := UFix(0)
         exe_reg_ctrl_rf_wen := Bool(false)
      }
      .otherwise
      {
         exe_reg_wbaddr      := dec_wbaddr
         exe_reg_ctrl_rf_wen := cs_rf_wen.toBool   
      }
   }
   .elsewhen (stall && !full_stall)
   {
      // kill exe stage
      exe_reg_wbaddr      := UFix(0)
      exe_reg_ctrl_rf_wen := Bool(false)
   }
   
   mem_reg_wbaddr      := exe_reg_wbaddr
   wb_reg_wbaddr       := mem_reg_wbaddr
   mem_reg_ctrl_rf_wen := exe_reg_ctrl_rf_wen
   wb_reg_ctrl_rf_wen  := mem_reg_ctrl_rf_wen

   
   val exe_inst_is_load = RegReset(Bool(false))
   
   when (!full_stall)
   {
      exe_inst_is_load := cs_mem_en.toBool && (cs_mem_fcn === M_XRD)
   }


   // Stall signal stalls instruction fetch & decode stages, 
   // inserts NOP into execute stage,  and drains execute, memory, and writeback stages
   // stalls on I$ misses and on hazards
   if (USE_FULL_BYPASSING)
   {
      // stall for load-use hazard
      stall := ((exe_inst_is_load) && (exe_reg_wbaddr === dec_rs1_addr) && (exe_reg_wbaddr != UFix(0)) && dec_rs1_oen) ||
               ((exe_inst_is_load) && (exe_reg_wbaddr === dec_rs2_addr) && (exe_reg_wbaddr != UFix(0)) && dec_rs2_oen) 
   }
   else
   {
      // stall for all hazards
      stall := ((exe_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr != UFix(0)) && exe_reg_ctrl_rf_wen && dec_rs1_oen) ||
               ((mem_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr != UFix(0)) && mem_reg_ctrl_rf_wen && dec_rs1_oen) ||
               ((wb_reg_wbaddr  === dec_rs1_addr) && (dec_rs1_addr != UFix(0)) &&  wb_reg_ctrl_rf_wen && dec_rs1_oen) ||
               ((exe_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr != UFix(0)) && exe_reg_ctrl_rf_wen && dec_rs2_oen) ||
               ((mem_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr != UFix(0)) && mem_reg_ctrl_rf_wen && dec_rs2_oen) ||
               ((wb_reg_wbaddr  === dec_rs2_addr) && (dec_rs2_addr != UFix(0)) &&  wb_reg_ctrl_rf_wen && dec_rs2_oen) ||
               ((exe_inst_is_load) && (exe_reg_wbaddr === dec_rs1_addr) && (exe_reg_wbaddr != UFix(0)) && dec_rs1_oen) ||
               ((exe_inst_is_load) && (exe_reg_wbaddr === dec_rs2_addr) && (exe_reg_wbaddr != UFix(0)) && dec_rs2_oen) 
   }
  
   
   // stall full pipeline on D$ miss
   val dmem_val   = io.dat.mem_ctrl_dmem_val
   full_stall := !io.imem.resp.valid || !((dmem_val && io.dmem.resp.valid) || !dmem_val)
   
   
   io.ctl.dec_stall  := stall // stall if, dec stage (pipeline hazard)
   io.ctl.full_stall := full_stall // stall entire pipeline (cache miss)
   io.ctl.exe_pc_sel := ctrl_exe_pc_sel
   io.ctl.br_type    := cs_br_type
   io.ctl.if_kill    := ifkill
   io.ctl.dec_kill   := deckill
   io.ctl.op1_sel    := cs_op1_sel
   io.ctl.op2_sel    := cs_op2_sel
   io.ctl.alu_fun    := cs_alu_fun
   io.ctl.wb_sel     := cs_wb_sel
   io.ctl.wa_sel     := cs_wa_sel.toBool
   io.ctl.rf_wen     := cs_rf_wen.toBool
   io.ctl.pcr_fcn    := cs_pcr_fcn
   
   io.imem.req.valid := Bool(true)
   io.ctl.mem_val    := cs_mem_en.toBool
   io.ctl.mem_fcn    := cs_mem_fcn
   
}

}
