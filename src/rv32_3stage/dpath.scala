//**************************************************************************
// RISCV Processor 3-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2013 Jn 29

package Sodor
{

import Chisel._
import Node._

import Constants._
import Common._
import Common.Constants._

class DatToCtlIo extends Bundle() 
{
   val inst   = Bits(OUTPUT, 32)
   val br_eq  = Bool(OUTPUT)
   val br_lt  = Bool(OUTPUT)
   val br_ltu = Bool(OUTPUT)
   val status = new Status().asOutput
}

class DpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val host  = new HTIFIO()
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val ctl  = new CtrlSignals().asInput
   val dat  = new DatToCtlIo()
}

class DatPath(implicit conf: SodorConfiguration) extends Mod 
{
   val io = new DpathIo()


   //**********************************
   // Pipeline State Registers
   val if_reg_pc = RegReset(UFix(START_ADDR, conf.xprlen))
    
   val exe_reg_pc   = RegReset(UFix(0, conf.xprlen))
   val exe_reg_inst = RegReset(BUBBLE)

   val wb_reg_ctrl     = Reg(new CtrlSignals)
   val wb_reg_alu      = Reg(Bits(width = conf.xprlen))
   val wb_reg_sdata    = Reg(Bits(width = conf.xprlen))
   val wb_reg_rs1_addr = Reg(UFix(width = log2Up(32))) // needed for PCR
   val wb_reg_wbaddr   = Reg(UFix(width = log2Up(32)))
   
   val hazard_stall = Bool() // hazard detected, stall in IF/EXE required

   //**********************************
   // Instruction Fetch Stage
   val if_pc_next          = UFix()
   val exe_brjmp_target    = UFix()
   val exe_jump_reg_target = UFix()
   val exception_target    = UFix()
 

   // if hazard stall, freeze value
   // if if_stall, freeze value UNLESS branch
   when (hazard_stall)
   {
      if_reg_pc := if_reg_pc
   }
   .elsewhen (io.ctl.if_stall && io.ctl.pc_sel === PC_4)
   {
      if_reg_pc := if_reg_pc
   }
   .otherwise
   {
      if_reg_pc := if_pc_next
   }


   val if_pc_plus4 = (if_reg_pc + UFix(4, conf.xprlen))               

   if_pc_next := MuxCase(if_pc_plus4, Array(
                  (io.ctl.pc_sel === PC_EXC)   -> exception_target,
                  (io.ctl.pc_sel === PC_4)     -> if_pc_plus4,
                  (io.ctl.pc_sel === PC_BRJMP) -> exe_brjmp_target,
                  (io.ctl.pc_sel === PC_JR)    -> exe_jump_reg_target
                  ))
   
   // TODO use if_pc_next, make IMEM sequential,
   io.imem.req.bits.addr := if_reg_pc
   val if_inst = io.imem.resp.bits.data


   when (hazard_stall)
   {
      exe_reg_inst := exe_reg_inst
      exe_reg_pc   := exe_reg_pc
   }
   .elsewhen (io.ctl.if_stall || io.ctl.if_kill)
   {
      exe_reg_inst := BUBBLE
      exe_reg_pc   := if_reg_pc
   }
   .otherwise
   {  
      exe_reg_inst := if_inst
      exe_reg_pc   := if_reg_pc
   }

   
   
   //**********************************
   // Execute Stage
   
   // Decode
   val exe_rs1_addr = exe_reg_inst(26, 22).toUFix
   val exe_rs2_addr = exe_reg_inst(21, 17).toUFix
   val exe_wbaddr   = Mux(io.ctl.wa_sel, exe_reg_inst(31, 27).toUFix,
                                         RA)
   val wb_wbdata    = Bits(width = conf.xprlen)
 


   // Hazard Stall Logic 
   hazard_stall := ((wb_reg_wbaddr === exe_rs1_addr) && (exe_rs1_addr != UFix(0)) && wb_reg_ctrl.rf_wen && !wb_reg_ctrl.bypassable) || 
                   ((wb_reg_wbaddr === exe_rs2_addr) && (exe_rs2_addr != UFix(0)) && wb_reg_ctrl.rf_wen && !wb_reg_ctrl.bypassable)


   // Register File
   val regfile = Mem(32, Bits(width = conf.xprlen))

   when (wb_reg_ctrl.rf_wen && (wb_reg_wbaddr != UFix(0)))
   {
      regfile(wb_reg_wbaddr) := wb_wbdata
   }

   val rf_rs1_data = Mux((exe_rs1_addr != UFix(0)), regfile(exe_rs1_addr), UFix(0, conf.xprlen))
   val rf_rs2_data = Mux((exe_rs2_addr != UFix(0)), regfile(exe_rs2_addr), UFix(0, conf.xprlen))
   
   
   // immediates
   val imm_btype = Cat(exe_reg_inst(31,27), exe_reg_inst(16,10))
   val imm_itype = exe_reg_inst(21,10)
   val imm_utype = Cat(exe_reg_inst(26,7), Fill(Bits(0,1),12))
   val imm_jtype = exe_reg_inst(31,7)

   // sign-extend immediates
   val imm_itype_sext = Cat(Fill(imm_itype(11), 20), imm_itype)
   val imm_btype_sext = Cat(Fill(imm_btype(11), 20), imm_btype)
   val imm_jtype_sext = Cat(Fill(imm_jtype(24),  7), imm_jtype)

   
   // Bypass Muxes
   // bypass early for branch condition checking, and to prevent needing 3 bypass muxes
   val exe_rs1_data = MuxCase(rf_rs1_data, Array(
                           ((wb_reg_wbaddr === exe_rs1_addr) && (exe_rs1_addr != UFix(0)) && wb_reg_ctrl.rf_wen && wb_reg_ctrl.bypassable) -> wb_reg_alu)
                        )
   val exe_rs2_data = MuxCase(rf_rs2_data, Array(
                           ((wb_reg_wbaddr === exe_rs2_addr) && (exe_rs2_addr != UFix(0)) && wb_reg_ctrl.rf_wen && wb_reg_ctrl.bypassable) -> wb_reg_alu)
                        )
   

   // Operand Muxes
   val exe_alu_op1 = MuxCase(UFix(0), Array(
               (io.ctl.op1_sel === OP1_RS1) -> exe_rs1_data,
               (io.ctl.op1_sel === OP1_PC)  -> exe_reg_pc
               )).toUFix
   
   val exe_alu_op2 = MuxCase(UFix(0), Array(
               (io.ctl.op2_sel === OP2_RS2) -> exe_rs2_data,
               (io.ctl.op2_sel === OP2_IMI) -> imm_itype_sext,
               (io.ctl.op2_sel === OP2_IMB) -> imm_btype_sext,
               (io.ctl.op2_sel === OP2_UI)  -> imm_utype,
               (io.ctl.op2_sel === OP2_4)   -> UFix(4)
               )).toUFix
  
        
   // ALU
   val alu = Mod(new ALU())

      alu.io.in1 := exe_alu_op1
      alu.io.in2 := exe_alu_op2
      alu.io.fn  := io.ctl.alu_fun
      //alu.io.dw  := DW_32  

   val exe_alu_out = alu.io.out

   // Branch/Jump Target Calculation
   val imm_brjmp = Mux(io.ctl.brjmp_sel, imm_jtype_sext, imm_btype_sext)
   exe_brjmp_target := exe_reg_pc + Cat(imm_brjmp(conf.xprlen-2,0), UFix(0,1)).toUFix
   exe_jump_reg_target := alu.io.adder_out 

   val exe_pc_plus4 = exe_reg_pc + UFix(4)

   // datapath to controlpath outputs
   io.dat.inst   := exe_reg_inst
   io.dat.br_eq  := (exe_rs1_data === exe_rs2_data)
   io.dat.br_lt  := (exe_rs1_data.toFix < exe_rs2_data.toFix) 
   io.dat.br_ltu := (exe_rs1_data.toUFix < exe_rs2_data.toUFix)
                                  

   // execute to wb registers
   wb_reg_ctrl :=  io.ctl
   when (hazard_stall || io.ctl.exe_kill)
   {
      wb_reg_ctrl.rf_wen    := Bool(false)
      wb_reg_ctrl.pcr_fcn   := PCR_N
      wb_reg_ctrl.dmem_val  := Bool(false)
      wb_reg_ctrl.exception := Bool(false)
      wb_reg_ctrl.eret      := Bool(false)
   }

   wb_reg_alu := exe_alu_out
   wb_reg_sdata := exe_rs2_data
   wb_reg_rs1_addr := exe_rs1_addr
   wb_reg_wbaddr := exe_wbaddr

   //**********************************
   // Writeback Stage
                                  
   
   // Privileged Co-processor Registers
   val pcr = Mod(new PCR())
   pcr.io.host <> io.host
   pcr.io.r.addr := wb_reg_rs1_addr
   pcr.io.r.en   := wb_reg_ctrl.pcr_fcn != PCR_N
   val pcr_out = pcr.io.r.data
   pcr.io.w.addr := wb_reg_rs1_addr
   pcr.io.w.en   := (wb_reg_ctrl.pcr_fcn === PCR_T) || (wb_reg_ctrl.pcr_fcn === PCR_S) || (wb_reg_ctrl.pcr_fcn === PCR_C)
   pcr.io.w.data := Mux(wb_reg_ctrl.pcr_fcn === PCR_S, pcr.io.r.data |  wb_reg_alu,
                    Mux(wb_reg_ctrl.pcr_fcn === PCR_C, pcr.io.r.data & ~wb_reg_alu,
                                                  wb_reg_alu))
   
   io.dat.status := pcr.io.status
   pcr.io.exception := io.ctl.exception
   pcr.io.cause  :=    io.ctl.exc_cause
   pcr.io.eret   :=    io.ctl.eret
   pcr.io.pc     := exe_reg_pc - UFix(4)  // note: I'm relying on two facts: IF predicts PC+4, 
                                          // and the "mispredictd" PC isn't cleared, so we 
                                          // can still read exe_reg_pc to get our own PC.
   exception_target := pcr.io.evec

   // Time Stamp Counter & Retired Instruction Counter 
   val tsc_reg = RegReset(UFix(0, conf.xprlen))
   tsc_reg := tsc_reg + UFix(1)

   // TODO properly figure out how to measure retired inst count
   val irt_reg = RegReset(UFix(0, conf.xprlen))
   when (!io.ctl.if_stall && !io.ctl.exception && !hazard_stall) { irt_reg := irt_reg + UFix(1) }


   // WB Mux                                                                   
   // Note: I'm relying on the fact that the EXE stage is holding the instruction behind our JR
   // assumes we always predict PC+4, and we don't clear the "mispredicted" PC when we jump
   wb_wbdata := MuxCase(wb_reg_alu, Array(
                  (wb_reg_ctrl.wb_sel === WB_ALU) -> wb_reg_alu,
                  (wb_reg_ctrl.wb_sel === WB_MEM) -> io.dmem.resp.bits.data, 
                  (wb_reg_ctrl.wb_sel === WB_PC4) -> exe_reg_pc,
                  (wb_reg_ctrl.wb_sel === WB_PCR) -> pcr_out,
                  (wb_reg_ctrl.wb_sel === WB_TSC) -> tsc_reg,
                  (wb_reg_ctrl.wb_sel === WB_IRT) -> irt_reg
                  )).toFix()
                                 
   
   // datapath to data memory outputs
   // TODO make synchronous memory
   io.dmem.req.valid     := wb_reg_ctrl.dmem_val
   io.dmem.req.bits.fcn  := wb_reg_ctrl.dmem_fcn
   io.dmem.req.bits.typ  := wb_reg_ctrl.dmem_typ
   io.dmem.req.bits.addr := wb_reg_alu.toUFix 
   io.dmem.req.bits.data := wb_reg_sdata
   
   
   //**********************************
   // Printout

   printf("Cyc=%d %s PC=(0x%x,0x%x,0x%x) [%s,%s,%s] Wb: %s %s %s %s Op1=[0x%x] Op2=[0x%x] W[%s,%d= 0x%x] [%s,%d]\n"
      , tsc_reg(23,0)
      , Mux(hazard_stall, Str("HAZ"), Str("   "))
      , if_reg_pc(19,0)
      , exe_reg_pc(19,0)
      , Mux(RegUpdate(hazard_stall), UFix(0), RegUpdate(exe_reg_pc)(19,0))
      , Disassemble(if_inst, true)
      , Disassemble(exe_reg_inst, true)
      , Mux(RegUpdate(hazard_stall || io.ctl.exe_kill), Disassemble(BUBBLE, true), Disassemble(RegUpdate(exe_reg_inst), true))
      , Mux(RegUpdate(hazard_stall || io.ctl.exe_kill), Disassemble(BUBBLE), Disassemble(RegUpdate(exe_reg_inst)))
      , Mux(io.ctl.if_stall, Str("stall"), Str("     "))
      , Mux(hazard_stall, Str("HAZ"), Str("   "))
      , Mux(io.ctl.pc_sel  === UFix(1), Str("Br/J"),
        Mux(io.ctl.pc_sel === UFix(2), Str(" JR "),
        Mux(io.ctl.pc_sel === UFix(3), Str("XPCT"),
        Mux(io.ctl.pc_sel === UFix(0), Str("   "), Str(" ?? ")))))
      , exe_alu_op1
      , exe_alu_op2
      , Mux(wb_reg_ctrl.rf_wen, Str("W"), Str("_"))
      , wb_reg_wbaddr
      , wb_wbdata
      , Mux(io.ctl.exception, Str("E"), Str("_"))
      , io.ctl.exc_cause
      )

   //**********************************
   // Handle Reset

   when (reset.toBool)
   {
      wb_reg_ctrl.rf_wen    := Bool(false)
      wb_reg_ctrl.pcr_fcn   := PCR_N
      wb_reg_ctrl.dmem_val  := Bool(false)
      wb_reg_ctrl.exception := Bool(false)
      wb_reg_ctrl.eret      := Bool(false)
   }
 
}

 
}
