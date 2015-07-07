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
   val csr_cmd    = UInt(OUTPUT, CSR.SZ)
   val fencei     = Bool(OUTPUT)    // pipeline is executing a fencei

   val pipeline_kill = Bool(OUTPUT) // an exception occurred (detected in mem stage).
                                    // Kill the entire pipeline disregard stalls
                                    // and kill if,dec,exe stages. 
   val mem_exception = Bool(OUTPUT) // tell the CSR that decode detected an exception
   val mem_exc_cause = UInt(OUTPUT, 32) 
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
                             List(N, BR_N  , OP1_X , OP2_X    , OEN_0, OEN_0, ALU_X   , WB_X  ,  REN_0, MEN_0, M_X  , MT_X, CSR.N, N),
               Array(       /* val  |  BR  |  op1  |   op2     |  R1  |  R2  |  ALU    |  wb   | rf   | mem  | mem  | mask | csr | fence.i */
                            /* inst | type |   sel |    sel    |  oen |  oen |   fcn   |  sel  | wen  |  en  |  wr  | type | cmd |         */
                  LW     -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_W, CSR.N, N),
                  LB     -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_B, CSR.N, N),
                  LBU    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_BU,CSR.N, N),
                  LH     -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_H, CSR.N, N),
                  LHU    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_MEM, REN_1, MEN_1, M_XRD, MT_HU,CSR.N, N),
                  SW     -> List(Y, BR_N  , OP1_RS1, OP2_STYPE , OEN_1, OEN_1, ALU_ADD , WB_X  , REN_0, MEN_1, M_XWR, MT_W, CSR.N, N),
                  SB     -> List(Y, BR_N  , OP1_RS1, OP2_STYPE , OEN_1, OEN_1, ALU_ADD , WB_X  , REN_0, MEN_1, M_XWR, MT_B, CSR.N, N),
                  SH     -> List(Y, BR_N  , OP1_RS1, OP2_STYPE , OEN_1, OEN_1, ALU_ADD , WB_X  , REN_0, MEN_1, M_XWR, MT_H, CSR.N, N),

                  AUIPC  -> List(Y, BR_N  , OP1_PC , OP2_UTYPE , OEN_0, OEN_0, ALU_ADD   ,WB_ALU,REN_1, MEN_0, M_X , MT_X,  CSR.N, N),
                  LUI    -> List(Y, BR_N  , OP1_X  , OP2_UTYPE , OEN_0, OEN_0, ALU_COPY_2,WB_ALU,REN_1, MEN_0, M_X , MT_X,  CSR.N, N),

                  ADDI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_ADD , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  ANDI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_AND , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  ORI    -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_OR  , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  XORI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_XOR , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SLTI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SLT , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SLTIU  -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SLTU, WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SLLI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SLL , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SRAI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SRA , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SRLI   -> List(Y, BR_N  , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_SRL , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),

                  SLL    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SLL , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  ADD    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_ADD , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SUB    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SUB , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SLT    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SLT , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SLTU   -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SLTU, WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  AND    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_AND , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  OR     -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_OR  , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  XOR    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_XOR , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SRA    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SRA , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  SRL    -> List(Y, BR_N  , OP1_RS1, OP2_RS2   , OEN_1, OEN_1, ALU_SRL , WB_ALU, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),

                  JAL    -> List(Y, BR_J  , OP1_RS1, OP2_UJTYPE, OEN_0, OEN_0, ALU_X   , WB_PC4, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  JALR   -> List(Y, BR_JR , OP1_RS1, OP2_ITYPE , OEN_1, OEN_0, ALU_X   , WB_PC4, REN_1, MEN_0, M_X  , MT_X, CSR.N, N),
                  BEQ    -> List(Y, BR_EQ , OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N),
                  BNE    -> List(Y, BR_NE , OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N),
                  BGE    -> List(Y, BR_GE , OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N),
                  BGEU   -> List(Y, BR_GEU, OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N),
                  BLT    -> List(Y, BR_LT , OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N),
                  BLTU   -> List(Y, BR_LTU, OP1_RS1, OP2_SBTYPE, OEN_1, OEN_1, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N),

                  CSRRWI -> List(Y, BR_N  , OP1_IMZ, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.W, N),
                  CSRRSI -> List(Y, BR_N  , OP1_IMZ, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.S, N),
                  CSRRW  -> List(Y, BR_N  , OP1_RS1, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.W, N),
                  CSRRS  -> List(Y, BR_N  , OP1_RS1, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.S, N),
                  CSRRC  -> List(Y, BR_N  , OP1_RS1, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.C, N),
                  CSRRCI -> List(Y, BR_N  , OP1_IMZ, OP2_X     , OEN_1, OEN_1, ALU_COPY_1,WB_CSR,REN_1, MEN_0, M_X  , MT_X, CSR.C, N),

                  SCALL  -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.I, N),
                  SRET   -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.I, N),
                  MRTS   -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.I, N),
                  SBREAK -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.I, N),
                  WFI    -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, N), // implemented as a NOP

                  FENCE_I-> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_0, M_X  , MT_X, CSR.N, Y),
                  // kill pipeline and refetch instructions since the pipeline will be holding stall instructions.
                  FENCE  -> List(Y, BR_N  , OP1_X  , OP2_X     , OEN_0, OEN_0, ALU_X   , WB_X  , REN_0, MEN_1, M_X  , MT_X, CSR.N, N)
                  // we are already sequentially consistent, so no need to honor the fence instruction
                  ))

   // Put these control signals in variables
   val (cs_val_inst: Bool) :: cs_br_type :: cs_op1_sel :: cs_op2_sel :: (cs_rs1_oen: Bool) :: (cs_rs2_oen: Bool) :: cs0 = csignals
   val cs_alu_fun :: cs_wb_sel :: (cs_rf_wen: Bool) :: (cs_mem_en: Bool) :: cs_mem_fcn :: cs_msk_sel :: cs_csr_cmd :: (cs_fencei: Bool) :: Nil = cs0


   // Branch Logic
   val ctrl_exe_pc_sel = Mux(io.ctl.pipeline_kill         , PC_EXC,
                         Mux(io.dat.exe_br_type === BR_N  , PC_4,
                         Mux(io.dat.exe_br_type === BR_NE , Mux(!io.dat.exe_br_eq,  PC_BRJMP, PC_4),
                         Mux(io.dat.exe_br_type === BR_EQ , Mux( io.dat.exe_br_eq,  PC_BRJMP, PC_4),
                         Mux(io.dat.exe_br_type === BR_GE , Mux(!io.dat.exe_br_lt,  PC_BRJMP, PC_4),
                         Mux(io.dat.exe_br_type === BR_GEU, Mux(!io.dat.exe_br_ltu, PC_BRJMP, PC_4),
                         Mux(io.dat.exe_br_type === BR_LT , Mux( io.dat.exe_br_lt,  PC_BRJMP, PC_4),
                         Mux(io.dat.exe_br_type === BR_LTU, Mux( io.dat.exe_br_ltu, PC_BRJMP, PC_4),
                         Mux(io.dat.exe_br_type === BR_J  , PC_BRJMP,
                         Mux(io.dat.exe_br_type === BR_JR , PC_JALR,
                                                            PC_4
                     ))))))))))

   val ifkill  = (ctrl_exe_pc_sel != PC_4) || !io.imem.resp.valid || cs_fencei || Reg(next=cs_fencei)
   val deckill = (ctrl_exe_pc_sel != PC_4)

   // Exception Handling ---------------------

   io.ctl.pipeline_kill := io.ctl.mem_exception || io.dat.csr_eret || io.dat.csr_xcpt
   
   val exc_illegal = (!cs_val_inst && io.imem.resp.valid) 
 
   val dec_exception = exc_illegal || io.dat.csr_interrupt
   val dec_exc_cause = Mux(io.dat.csr_interrupt, io.dat.csr_interrupt_cause, 
                                                 UInt(Common.Causes.illegal_instruction))

   // Stall Signal Logic --------------------
   val stall   = Bool()

   val dec_rs1_addr = io.dat.dec_inst(19, 15)
   val dec_rs2_addr = io.dat.dec_inst(24, 20)
   val dec_wbaddr   = io.dat.dec_inst(11, 7)
   val dec_rs1_oen  = Mux(deckill, Bool(false), cs_rs1_oen)
   val dec_rs2_oen  = Mux(deckill, Bool(false), cs_rs2_oen)

   val exe_reg_wbaddr      = Reg(UInt())
   val mem_reg_wbaddr      = Reg(UInt())
   val wb_reg_wbaddr       = Reg(UInt())
   val exe_reg_ctrl_rf_wen = Reg(init=Bool(false))
   val mem_reg_ctrl_rf_wen = Reg(init=Bool(false))
   val wb_reg_ctrl_rf_wen  = Reg(init=Bool(false))
   val exe_reg_exception   = Reg(init=Bool(false))

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
         exe_reg_exception   := Bool(false)
      }
      .otherwise
      {
         exe_reg_wbaddr      := dec_wbaddr
         exe_reg_ctrl_rf_wen := cs_rf_wen
         exe_reg_is_csr      := cs_csr_cmd != CSR.N && cs_csr_cmd != CSR.I
         exe_reg_exception   := dec_exception
      }
   }
   .elsewhen (stall && !full_stall)
   {
      // kill exe stage
      exe_reg_wbaddr      := UInt(0)
      exe_reg_ctrl_rf_wen := Bool(false)
      exe_reg_is_csr      := Bool(false)
      exe_reg_exception   := Bool(false)
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
               (exe_reg_is_csr)
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
   
   // we need to stall IF while fencei goes through DEC and EXE, as there may
   // be a store we need to wait to clear in MEM.
   io.ctl.fencei     := cs_fencei || Reg(next=cs_fencei) 
 
   io.ctl.mem_exception := Reg(next=exe_reg_exception)
   io.ctl.mem_exc_cause := Reg(next=Reg(next=dec_exc_cause))
                                    
    
   // convert CSR instructions with raddr1 == 0 to read-only CSR commands
   val rs1_addr = io.dat.dec_inst(RS1_MSB, RS1_LSB)
   val csr_ren = (cs_csr_cmd === CSR.S || cs_csr_cmd === CSR.C) && rs1_addr === UInt(0)
   io.ctl.csr_cmd := Mux(csr_ren, CSR.R, cs_csr_cmd)

   io.imem.req.valid := Bool(true)
   io.imem.req.bits.fcn := M_XRD
   io.imem.req.bits.typ := MT_WU
   io.ctl.mem_val    := cs_mem_en
   io.ctl.mem_fcn    := cs_mem_fcn
   io.ctl.mem_typ   := cs_msk_sel

}

}
