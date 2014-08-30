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
   val exe_pc_sel = UInt(OUTPUT, 2)
   val br_type    = UInt(OUTPUT, 4)
   val if_kill    = Bool(OUTPUT) 
   val dec_kill   = Bool(OUTPUT) 
   val op1_sel    = UInt(OUTPUT, 2)
   val op2_sel    = UInt(OUTPUT, 3)
   val alu_fun    = UInt(OUTPUT, 4)
   val wb_sel     = UInt(OUTPUT, 2)
   val rf_wen     = Bool(OUTPUT) 
   val mem_val    = Bool(OUTPUT)
   val mem_fcn    = Bits(OUTPUT, 2)
   val mem_typ    = Bits(OUTPUT, 3)
   val csr_cmd    = UInt(OUTPUT, 2)
}

class CpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val dat  = new DatToCtlIo().flip()
   val ctl  = new CtlToDatIo()
   override def clone = { new CpathIo().asInstanceOf[this.type] }
}


class CtlPath(implicit conf: SodorConfiguration) extends Module
{
  val io = new CpathIo()

   val csignals = 
      ListLookup(io.dat.dec_inst,                                  
                             List(N, BR_N  , OP1_X , OP2_X    , OEN_0, OEN_0, ALU_X   , WB_X  ,  REN_0, MEN_0, M_X   , MT_X,   CSR.N),
               Array(       /* val  |  BR  |  op1  |   op2     |  R1  |  R2  |  ALU    |  wb   | rf   | mem  | mem  | mask |  csr  */
                            /* inst | type |   sel |    sel    |  oen |  oen |   fcn   |  sel  | wen  |  en  |  wr  | type |  cmd  */
                  LW     -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_W, CSR.N),
                  LB     -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_B, CSR.N),
                  LBU    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_BU,CSR.N),
                  LH     -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_H, CSR.N),
                  LHU    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_HU,CSR.N),
                  SW     -> List(Y, BR_N  , OP1_RS1, OP2_STYPE , OEN_1, OEN_1, ALU_ADD , WB_X  , REN_0, MEN_1, M_XWR, MT_W, CSR.N),
                  SB     -> List(Y, BR_N  , OP1_RS1, OP2_STYPE , OEN_1, OEN_1, ALU_ADD , WB_X  , REN_0, MEN_1, M_XWR, MT_B, CSR.N),
                  SH     -> List(Y, BR_N  , OP1_RS1, OP2_STYPE , OEN_1, OEN_1, ALU_ADD , WB_X  , REN_0, MEN_1, M_XWR, MT_H, CSR.N),
                  
                  AUIPC  -> List(Y, BR_N  , OP1_PC , OP2_UTYPE , OEN_0, OEN_0, ALU_ADD   ,WB_ALU,REN_1, MEN_0, M_X , MT_X,  CSR.N),
                  LUI    -> List(Y, BR_N  , OP1_X  , OP2_UTYPE , OEN_0, OEN_0, ALU_COPY_2,WB_ALU,REN_1, MEN_0, M_X , MT_X,  CSR.N),
                 
                  ADDI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  ANDI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_AND , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  ORI    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_OR  , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  XORI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_XOR , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  SLTI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SLT , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  SLTIU  -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SLTU, WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  SLLI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SLL , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  SRAI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SRA , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  SRLI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SRL , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  
                  SLL    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SLL , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  ADD    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_ADD , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  SUB    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SUB , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  SLT    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SLT , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  SLTU   -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SLTU, WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  AND    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_AND , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  OR     -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_OR  , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  XOR    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_XOR , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  SRA    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SRA , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  SRL    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SRL , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                 
                  JAL    -> List(Y, BR_J  , OP1_RS1, OP2_UJTYPE, OEN_0, OEN_0, ALU_X   , WB_PC4, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  JALR   -> List(Y, BR_JR , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_X   , WB_PC4, REN_1, MEN_0, M_X  , MT_X, CSR.N),
                  BEQ    -> List(Y, BR_EQ , OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N),
                  BNE    -> List(Y, BR_NE , OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N),
                  BGE    -> List(Y, BR_GE , OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N),
                  BGEU   -> List(Y, BR_GEU, OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N),
                  BLT    -> List(Y, BR_LT , OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N),
                  BLTU   -> List(Y, BR_LTU, OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N),

                  CSRRWI -> List(Y, BR_N  , OP1_IMZ, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.W),
                  CSRRSI -> List(Y, BR_N  , OP1_IMZ, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.S),
                  CSRRW  -> List(Y, BR_N  , OP1_RS1, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.W),
                  CSRRS  -> List(Y, BR_N  , OP1_RS1, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.S),
                  CSRRC  -> List(Y, BR_N  , OP1_RS1, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.C),
                  CSRRCI -> List(Y, BR_N  , OP1_IMZ, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.C)
                  ))

   // Put these control signals in variables
   val (cs_val_inst: Bool) :: cs_br_type :: cs_op1_sel :: cs_op2_sel :: (cs_rs1_oen: Bool) :: (cs_rs2_oen: Bool) :: cs0 = csignals
   val cs_alu_fun :: cs_wb_sel :: (cs_rf_wen: Bool) :: (cs_mem_en: Bool) :: cs_mem_fcn :: cs_msk_sel :: cs_csr_cmd :: Nil = cs0

 
   // Branch Logic   
   val ctrl_exe_pc_sel
      = Lookup(io.dat.exe_br_type, UInt(0, 3), 
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
   
   val dec_rs1_addr = io.dat.dec_inst(19, 15)
   val dec_rs2_addr = io.dat.dec_inst(24, 20)
   val dec_wbaddr  = io.dat.dec_inst(11, 7)
   val dec_rs1_oen = Mux(deckill, Bool(false), cs_rs1_oen)
   val dec_rs2_oen = Mux(deckill, Bool(false), cs_rs2_oen)
   
   val exe_reg_wbaddr      = Reg(UInt())
   val mem_reg_wbaddr      = Reg(UInt())
   val wb_reg_wbaddr       = Reg(UInt())
   val exe_reg_ctrl_rf_wen = Reg(init=Bool(false))
   val mem_reg_ctrl_rf_wen = Reg(init=Bool(false))
   val wb_reg_ctrl_rf_wen  = Reg(init=Bool(false))

   val exe_reg_is_csr = Reg(init=Bool(false))
   
   // TODO rename stall==hazard_stall full_stall == cmiss_stall
   val full_stall = Bool()
   when (!stall && !full_stall)
   {
      when (deckill)
      {
         exe_reg_wbaddr      := UInt(0)
         exe_reg_ctrl_rf_wen := Bool(false)
         exe_reg_is_csr      := Bool(false)
      }
      .otherwise
      {
         exe_reg_wbaddr      := dec_wbaddr
         exe_reg_ctrl_rf_wen := cs_rf_wen
         exe_reg_is_csr      := cs_csr_cmd != CSR.N
      }
   }
   .elsewhen (stall && !full_stall)
   {
      // kill exe stage
      exe_reg_wbaddr      := UInt(0)
      exe_reg_ctrl_rf_wen := Bool(false)
      exe_reg_is_csr      := Bool(false)
   }
   
   mem_reg_wbaddr      := exe_reg_wbaddr
   wb_reg_wbaddr       := mem_reg_wbaddr
   mem_reg_ctrl_rf_wen := exe_reg_ctrl_rf_wen
   wb_reg_ctrl_rf_wen  := mem_reg_ctrl_rf_wen

   
   val exe_inst_is_load = Reg(init=Bool(false))
   
   when (!full_stall)
   {
      exe_inst_is_load := cs_mem_en && (cs_mem_fcn === M_XRD)
   }


   // Stall signal stalls instruction fetch & decode stages, 
   // inserts NOP into execute stage,  and drains execute, memory, and writeback stages
   // stalls on I$ misses and on hazards
   if (USE_FULL_BYPASSING)
   {
      // stall for load-use hazard
      stall := ((exe_inst_is_load) && (exe_reg_wbaddr === dec_rs1_addr) && (exe_reg_wbaddr != UInt(0)) && dec_rs1_oen) ||
               ((exe_inst_is_load) && (exe_reg_wbaddr === dec_rs2_addr) && (exe_reg_wbaddr != UInt(0)) && dec_rs2_oen) ||
               ((exe_reg_is_csr))
   }
   else
   {
      // stall for all hazards
      stall := ((exe_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr != UInt(0)) && exe_reg_ctrl_rf_wen && dec_rs1_oen) ||
               ((mem_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr != UInt(0)) && mem_reg_ctrl_rf_wen && dec_rs1_oen) ||
               ((wb_reg_wbaddr  === dec_rs1_addr) && (dec_rs1_addr != UInt(0)) &&  wb_reg_ctrl_rf_wen && dec_rs1_oen) ||
               ((exe_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr != UInt(0)) && exe_reg_ctrl_rf_wen && dec_rs2_oen) ||
               ((mem_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr != UInt(0)) && mem_reg_ctrl_rf_wen && dec_rs2_oen) ||
               ((wb_reg_wbaddr  === dec_rs2_addr) && (dec_rs2_addr != UInt(0)) &&  wb_reg_ctrl_rf_wen && dec_rs2_oen) ||
               ((exe_inst_is_load) && (exe_reg_wbaddr === dec_rs1_addr) && (exe_reg_wbaddr != UInt(0)) && dec_rs1_oen) ||
               ((exe_inst_is_load) && (exe_reg_wbaddr === dec_rs2_addr) && (exe_reg_wbaddr != UInt(0)) && dec_rs2_oen) ||
               ((exe_reg_is_csr))
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
   io.ctl.rf_wen     := cs_rf_wen
   io.ctl.csr_cmd    := cs_csr_cmd
   
   io.imem.req.valid := Bool(true)
   io.imem.req.bits.fcn := M_XRD
   io.imem.req.bits.typ := MT_WU
   io.ctl.mem_val    := cs_mem_en
   io.ctl.mem_fcn    := cs_mem_fcn
   io.ctl.mem_typ   := cs_msk_sel
   
}

}
