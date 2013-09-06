//**************************************************************************
// RISCV Processor 3-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2013 Jun 29
//
// This concerns the "backend" datapath for the Z-Scale 3-stage processor.  The
// frontend is separated out (since the front-end is relatively decoupled, and
// because the designer may wish to swap out different front-ends for different
// performance/area tradeoffs).
//
// Thus, this file covers the Execute and Writeback stages on the 3-stage.

package Sodor
{

import Chisel._
import Node._

import Constants._
import Common._
import Common.Constants._

class DatToCtlIo extends Bundle() 
{
   val br_eq  = Bool(OUTPUT)
   val br_lt  = Bool(OUTPUT)
   val br_ltu = Bool(OUTPUT)
   val status = new Status().asOutput
}

class DpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val host  = new HTIFIO()
   val imem = new FrontEndCpuIO().flip()
   val dmem = new MemPortIo(conf.xprlen)
   val ctl  = new CtrlSignals().asInput
   val dat  = new DatToCtlIo()
}

class DatPath(implicit conf: SodorConfiguration) extends Module 
{
   val io = new DpathIo()


   //**********************************
   // Pipeline State Registers

   val wb_reg_ctrl     = Reg(outType = new CtrlSignals)
   val wb_reg_alu      = Reg(outType = Bits(width=conf.xprlen))
   val wb_reg_rs1_addr = Reg(outType = UInt(width=log2Up(32))) // needed for PCR
   val wb_reg_wbaddr   = Reg(outType = UInt(width=log2Up(32)))
   
   val wb_hazard_stall = Bool() // hazard detected, stall in IF/EXE required

   //**********************************
   // Instruction Fetch Stage
   val exe_brjmp_target    = UInt()
   val exe_jump_reg_target = UInt()
   val exception_target    = UInt()

   io.imem.resp.ready := !wb_hazard_stall // stall IF if we detect a WB->EXE hazard

   val if_pc_next = Mux(io.ctl.pc_sel === PC_EXC,   exception_target,
                    Mux(io.ctl.pc_sel === PC_BRJMP, exe_brjmp_target,
                                                    exe_jump_reg_target)) // PC_JR

   io.imem.req.bits.pc := if_pc_next

   
   //**********************************
   // Execute Stage
   val exe_valid = io.imem.resp.valid
   val exe_inst  = io.imem.resp.bits.inst
   val exe_pc    = io.imem.resp.bits.pc
   
   // Decode
   val exe_rs1_addr = exe_inst(26, 22).toUInt
   val exe_rs2_addr = exe_inst(21, 17).toUInt
   val exe_wbaddr   = Mux(io.ctl.wa_sel, exe_inst(31, 27).toUInt,
                                         RA)
   val wb_wbdata    = Bits(width = conf.xprlen)
 


   // Hazard Stall Logic 
   wb_hazard_stall := ((wb_reg_wbaddr === exe_rs1_addr) && (exe_rs1_addr != UInt(0)) && wb_reg_ctrl.rf_wen && !wb_reg_ctrl.bypassable) || 
                      ((wb_reg_wbaddr === exe_rs2_addr) && (exe_rs2_addr != UInt(0)) && wb_reg_ctrl.rf_wen && !wb_reg_ctrl.bypassable)


   // Register File
   val regfile = Mem(Bits(width = conf.xprlen), 32)

   when (wb_reg_ctrl.rf_wen && (wb_reg_wbaddr != UInt(0)))
   {
      regfile(wb_reg_wbaddr) := wb_wbdata
   }

   val rf_rs1_data = Mux((exe_rs1_addr != UInt(0)), regfile(exe_rs1_addr), UInt(0, conf.xprlen))
   val rf_rs2_data = Mux((exe_rs2_addr != UInt(0)), regfile(exe_rs2_addr), UInt(0, conf.xprlen))
   
   
   // immediates
   val imm_btype = Cat(exe_inst(31,27), exe_inst(16,10))
   val imm_itype = exe_inst(21,10)
   val imm_utype = Cat(exe_inst(26,7), Fill(Bits(0,1),12))
   val imm_jtype = exe_inst(31,7)

   // sign-extend immediates
   val imm_itype_sext = Cat(Fill(imm_itype(11), 20), imm_itype)
   val imm_btype_sext = Cat(Fill(imm_btype(11), 20), imm_btype)
   val imm_jtype_sext = Cat(Fill(imm_jtype(24),  7), imm_jtype)

   
   // Bypass Muxes
   // bypass early for branch condition checking, and to prevent needing 3 bypass muxes
   val exe_rs1_data = MuxCase(rf_rs1_data, Array(
                           ((wb_reg_wbaddr === exe_rs1_addr) && (exe_rs1_addr != UInt(0)) && wb_reg_ctrl.rf_wen && wb_reg_ctrl.bypassable) -> wb_reg_alu)
                        )
   val exe_rs2_data = MuxCase(rf_rs2_data, Array(
                           ((wb_reg_wbaddr === exe_rs2_addr) && (exe_rs2_addr != UInt(0)) && wb_reg_ctrl.rf_wen && wb_reg_ctrl.bypassable) -> wb_reg_alu)
                        )
   

   // Operand Muxes
   val exe_alu_op1 = MuxCase(UInt(0), Array(
               (io.ctl.op1_sel === OP1_RS1) -> exe_rs1_data,
               (io.ctl.op1_sel === OP1_PC)  -> exe_pc
               )).toUInt
   
   val exe_alu_op2 = MuxCase(UInt(0), Array(
               (io.ctl.op2_sel === OP2_RS2) -> exe_rs2_data,
               (io.ctl.op2_sel === OP2_IMI) -> imm_itype_sext,
               (io.ctl.op2_sel === OP2_IMB) -> imm_btype_sext,
               (io.ctl.op2_sel === OP2_UI)  -> imm_utype,
               (io.ctl.op2_sel === OP2_4)   -> UInt(4)
               )).toUInt
  
        
   // ALU
   val alu = Module(new ALU())

      alu.io.in1 := exe_alu_op1
      alu.io.in2 := exe_alu_op2
      alu.io.fn  := io.ctl.alu_fun

   val exe_alu_out = alu.io.out

   // Branch/Jump Target Calculation
   val imm_brjmp = Mux(io.ctl.brjmp_sel, imm_jtype_sext, imm_btype_sext)
   exe_brjmp_target := exe_pc + Cat(imm_brjmp(conf.xprlen-2,0), UInt(0,1)).toUInt
   exe_jump_reg_target := alu.io.adder_out 


   // datapath to controlpath outputs
   io.dat.br_eq  := (exe_rs1_data === exe_rs2_data)
   io.dat.br_lt  := (exe_rs1_data.toSInt < exe_rs2_data.toSInt) 
   io.dat.br_ltu := (exe_rs1_data.toUInt < exe_rs2_data.toUInt)
                                  

   // execute to wb registers
   wb_reg_ctrl :=  io.ctl
   when (wb_hazard_stall || io.ctl.exe_kill)
   {
      wb_reg_ctrl.rf_wen    := Bool(false)
      wb_reg_ctrl.pcr_fcn   := PCR_N
      wb_reg_ctrl.dmem_val  := Bool(false)
      wb_reg_ctrl.exception := Bool(false)
      wb_reg_ctrl.eret      := Bool(false)
   }

   wb_reg_alu := exe_alu_out
   wb_reg_rs1_addr := exe_rs1_addr
   wb_reg_wbaddr := exe_wbaddr
     
   
   // datapath to data memory outputs
   io.dmem.req.valid     := io.ctl.dmem_val
   io.dmem.req.bits.fcn  := io.ctl.dmem_fcn
   io.dmem.req.bits.typ  := io.ctl.dmem_typ
   io.dmem.req.bits.addr := exe_alu_out
   io.dmem.req.bits.data := exe_rs2_data
                                 
   //**********************************
   // Writeback Stage
                                  
   
   // Privileged Co-processor Registers
   val pcr = Module(new PCR())
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
   pcr.io.pc     := exe_pc - UInt(4)  // note: I'm relying on two facts: IF predicts PC+4, 
                                          // and the "mispredictd" PC isn't cleared, so we 
                                          // can still read exe_pc to get our own PC.
   exception_target := pcr.io.evec

   pcr.io.badvaddr_wen := Bool(false)    // the PCR we're using is admittedly 
   pcr.io.vec_irq_aux_wen := Bool(false) // more featured than necessary


   // Time Stamp Counter & Retired Instruction Counter 
   val tsc_reg = Reg(init=UInt(0, conf.xprlen))
   tsc_reg := tsc_reg + UInt(1)

   // TODO properly figure out how to measure retired inst count
   val irt_reg = Reg(init=UInt(0, conf.xprlen))
   when (exe_valid && !io.ctl.exception && !wb_hazard_stall) { irt_reg := irt_reg + UInt(1) }


   // WB Mux                                                                   
   // Note: I'm relying on the fact that the EXE stage is holding the instruction behind our JR
   // assumes we always predict PC+4, and we don't clear the "mispredicted" PC when we jump
   wb_wbdata := MuxCase(wb_reg_alu, Array(
                  (wb_reg_ctrl.wb_sel === WB_ALU) -> wb_reg_alu,
                  (wb_reg_ctrl.wb_sel === WB_MEM) -> io.dmem.resp.bits.data, 
                  (wb_reg_ctrl.wb_sel === WB_PC4) -> exe_pc,
                  (wb_reg_ctrl.wb_sel === WB_PCR) -> pcr_out,
                  (wb_reg_ctrl.wb_sel === WB_TSC) -> tsc_reg,
                  (wb_reg_ctrl.wb_sel === WB_IRT) -> irt_reg
                  )).toSInt()
                                
   
   //**********************************
   // Printout

   printf("Cyc=%d %s PC=(0x%x,0x%x,0x%x) [%s,%s,%s] Wb: %s %s %s Op1=[0x%x] Op2=[0x%x] W[%s,%d= 0x%x] [%s,%d]\n"
      , tsc_reg(23,0)
      , Mux(wb_hazard_stall, Str("HAZ"), Str("   "))
      , io.imem.debug.if_pc(19,0)
      , exe_pc(19,0)
      , Mux(Reg(next=wb_hazard_stall), UInt(0), Reg(next=exe_pc)(19,0))
      , Disassemble(io.imem.debug.if_inst, true)
      , Disassemble(exe_inst, true)
      , Mux(Reg(next=wb_hazard_stall || io.ctl.exe_kill), Disassemble(BUBBLE, true), Disassemble(Reg(next=exe_inst), true))
      , Mux(Reg(next=wb_hazard_stall || io.ctl.exe_kill), Disassemble(BUBBLE), Disassemble(Reg(next=exe_inst)))
      , Mux(wb_hazard_stall, Str("HAZ"), Str("   "))
      , Mux(io.ctl.pc_sel  === UInt(1), Str("Br/J"),
        Mux(io.ctl.pc_sel === UInt(2), Str(" JR "),
        Mux(io.ctl.pc_sel === UInt(3), Str("XPCT"),
        Mux(io.ctl.pc_sel === UInt(0), Str("   "), Str(" ?? ")))))
      , exe_alu_op1
      , exe_alu_op2
      , Mux(wb_reg_ctrl.rf_wen, Str("W"), Str("_"))
      , wb_reg_wbaddr
      , wb_wbdata
      , Mux(io.ctl.exception, Str("E"), Str("_"))
      , io.ctl.exc_cause
//      , Mux(io.ctl.dmem_val, Str("V"), Str("_"))
//      , io.dmem.req.bits.data
      )

   //**********************************
   // Handle Reset

   when (this.reset)
   {
      wb_reg_ctrl.rf_wen    := Bool(false)
      wb_reg_ctrl.pcr_fcn   := PCR_N
      wb_reg_ctrl.dmem_val  := Bool(false)
      wb_reg_ctrl.exception := Bool(false)
      wb_reg_ctrl.eret      := Bool(false)
   }
 
}

 
}
