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

   val wb_reg_valid    = Reg(init=Bool(false))
   val wb_reg_ctrl     = Reg(outType = new CtrlSignals)
   val wb_reg_alu      = Reg(outType = Bits(width=conf.xprlen))
   val wb_reg_csr_addr = Reg(outType = UInt(width=12)) 
   val wb_reg_wbaddr   = Reg(outType = UInt(width=log2Up(32)))
   
   val wb_hazard_stall = Bool() // hazard detected, stall in IF/EXE required

   //**********************************
   // Instruction Fetch Stage
   val exe_brjmp_target    = UInt()
   val exe_jump_reg_target = UInt()
   val exception_target    = UInt()

   io.imem.resp.ready := !wb_hazard_stall // stall IF if we detect a WB->EXE hazard

   // if front-end mispredicted, tell it which PC to take
   val take_pc = Mux(io.ctl.pc_sel === PC_EXC,   exception_target,
                 Mux(io.ctl.pc_sel === PC_JR,    exe_jump_reg_target,
                                                 exe_brjmp_target)) // PC_BR or PC_J
                                                    
   io.imem.req.bits.pc := take_pc

   
   //**********************************
   // Execute Stage
   val exe_valid = io.imem.resp.valid
   val exe_inst  = io.imem.resp.bits.inst
   val exe_pc    = io.imem.resp.bits.pc
   
   // Decode
   val exe_rs1_addr = exe_inst(RS1_MSB, RS1_LSB)
   val exe_rs2_addr = exe_inst(RS2_MSB, RS2_LSB)
   val exe_wbaddr   = exe_inst(RD_MSB,  RD_LSB)
                       
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
   val imm_i = exe_inst(31, 20) 
   val imm_s = Cat(exe_inst(31, 25), exe_inst(11,7))
   val imm_b = Cat(exe_inst(31), exe_inst(7), exe_inst(30,25), exe_inst(11,8))
   val imm_u = Cat(exe_inst(31, 12), Fill(UInt(0), 12))
   val imm_j = Cat(exe_inst(31), exe_inst(19,12), exe_inst(20), exe_inst(30,21))
   val imm_z = exe_inst(19,15)

   // sign-extend immediates
   val imm_i_sext = Cat(Fill(imm_i(11), 20), imm_i)
   val imm_s_sext = Cat(Fill(imm_s(11), 20), imm_s)
   val imm_b_sext = Cat(Fill(imm_b(11), 19), imm_b, UInt(0))
   val imm_j_sext = Cat(Fill(imm_j(19), 11), imm_j, UInt(0))
 
   
   // Bypass Muxes
   // bypass early for branch condition checking, and to prevent needing 3 bypass muxes
   val exe_rs1_data = MuxCase(rf_rs1_data, Array(
                           ((wb_reg_wbaddr === exe_rs1_addr) && (exe_rs1_addr != UInt(0)) && wb_reg_ctrl.rf_wen && wb_reg_ctrl.bypassable) -> wb_reg_alu)
                        )
   val exe_rs2_data = MuxCase(rf_rs2_data, Array(
                           ((wb_reg_wbaddr === exe_rs2_addr) && (exe_rs2_addr != UInt(0)) && wb_reg_ctrl.rf_wen && wb_reg_ctrl.bypassable) -> wb_reg_alu)
                        )
   

   // Operand Muxes
   val exe_alu_op1 = Mux(io.ctl.op1_sel === OP1_IMZ, imm_z,
                     Mux(io.ctl.op1_sel === OP1_IMU, imm_u,
                                                     exe_rs1_data)).toUInt
   
   val exe_alu_op2 = Mux(io.ctl.op2_sel === OP2_IMI, imm_i_sext,
                     Mux(io.ctl.op2_sel === OP2_PC,  exe_pc,
                     Mux(io.ctl.op2_sel === OP2_IMS, imm_s_sext,
                                                     exe_rs2_data))).toUInt
  
        
   // ALU
   val alu = Module(new ALU())

      alu.io.in1 := exe_alu_op1
      alu.io.in2 := exe_alu_op2
      alu.io.fn  := io.ctl.alu_fun

   val exe_alu_out = alu.io.out

   // Branch/Jump Target Calculation
   val imm_brjmp = Mux(io.ctl.brjmp_sel, imm_j_sext, imm_b_sext)
   exe_brjmp_target := exe_pc + imm_brjmp
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
      wb_reg_ctrl.csr_cmd   := CSR.N
      wb_reg_ctrl.dmem_val  := Bool(false)
      wb_reg_ctrl.exception := Bool(false)
      wb_reg_ctrl.sret      := Bool(false)
   }

   wb_reg_alu      := exe_alu_out
   wb_reg_wbaddr   := exe_wbaddr
     
   
   // datapath to data memory outputs
   io.dmem.req.valid     := io.ctl.dmem_val
   io.dmem.req.bits.fcn  := io.ctl.dmem_fcn
   io.dmem.req.bits.typ  := io.ctl.dmem_typ
   io.dmem.req.bits.addr := exe_alu_out
   io.dmem.req.bits.data := exe_rs2_data
                                 
   //**********************************
   // Writeback Stage
   
   wb_reg_valid    := exe_valid && !wb_hazard_stall 
   wb_reg_csr_addr := exe_inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
 
   // Control Status Registers
   val csr = Module(new CSRFile())
   val csr_cmd = wb_reg_ctrl.csr_cmd
   csr.io.host <> io.host
   csr.io.rw.addr   := wb_reg_csr_addr
   csr.io.rw.wdata  := Mux(csr_cmd=== CSR.S, csr.io.rw.rdata |  wb_reg_alu,
                       Mux(csr_cmd=== CSR.C, csr.io.rw.rdata & ~wb_reg_alu,
                                             wb_reg_alu))
   csr.io.rw.cmd    := csr_cmd 
   val csr_out = csr.io.rw.rdata

   csr.io.retire    := wb_reg_valid
   csr.io.exception := wb_reg_ctrl.exception
   csr.io.cause     := wb_reg_ctrl.exc_cause
   csr.io.sret      := wb_reg_ctrl.sret
   csr.io.pc        := exe_pc - UInt(4)
   exception_target := csr.io.evec
   io.dat.status    := csr.io.status

   // Add your own uarch counters here!
   csr.io.uarch_counters.foreach(_ := Bool(false))

   // WB Mux                                                                   
   // Note: I'm relying on the fact that the EXE stage is holding the
   // instruction behind our jal, which assumes we always predict PC+4, and we
   // don't clear the "mispredicted" PC when we jump.
   require (PREDICT_PCP4==true)

   wb_wbdata := MuxCase(wb_reg_alu, Array(
                  (wb_reg_ctrl.wb_sel === WB_ALU) -> wb_reg_alu,
                  (wb_reg_ctrl.wb_sel === WB_MEM) -> io.dmem.resp.bits.data, 
                  (wb_reg_ctrl.wb_sel === WB_PC4) -> exe_pc,
                  (wb_reg_ctrl.wb_sel === WB_CSR) -> csr_out
                  )).toSInt()
                                
   
   //**********************************
   // Printout

   val tsc_reg = Reg(init=UInt(0, conf.xprlen))
   val irt_reg = Reg(init=UInt(0, conf.xprlen))
   tsc_reg := tsc_reg + UInt(1)
   when (wb_reg_valid) { irt_reg := irt_reg + UInt(1) }

   val debug_wb_pc = UInt(width=64)
   debug_wb_pc := Mux(Reg(next=wb_hazard_stall), UInt(0), Reg(next=exe_pc))
   val debug_wb_inst = Reg(next=Mux((wb_hazard_stall || io.ctl.exe_kill || !exe_valid), BUBBLE, exe_inst))
   printf("Cyc=%d %s PC=(0x%x,0x%x,0x%x) [%s,%s,%s] Wb: %s %s %s Op1=[0x%x] Op2=[0x%x] W[%s,%d= 0x%x] [%s,%d] %d\n"
      , tsc_reg(23,0)
      , Mux(wb_hazard_stall, Str("HAZ"), Str("   "))
      , io.imem.debug.if_pc(19,0)
      , exe_pc(19,0)
      , Mux(Reg(next=wb_hazard_stall), UInt(0), Reg(next=exe_pc)(19,0))
      , Disassemble(io.imem.debug.if_inst, true)
      , Disassemble(Mux(exe_valid, exe_inst, BUBBLE), true)
      , Disassemble(debug_wb_inst, true)
      , Disassemble(debug_wb_inst)
      , Mux(wb_hazard_stall, Str("HAZ"), Str("   "))
      , Mux(io.ctl.pc_sel === UInt(1), Str("Br/J"),
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
      , irt_reg(11,0)
      )

   // for debugging, print out the commit information.
   // can be compared against the riscv-isa-run Spike ISA simulator's commit logger.
   // use "sed" to parse out "@@@" from the other printf code above.
   if (PRINT_COMMIT_LOG)
   {
      when (wb_reg_valid)
      {
         val rd = debug_wb_inst(RD_MSB,RD_LSB)
         when (wb_reg_ctrl.rf_wen && rd != UInt(0))
         {
            printf("@@@ 0x%x (0x%x) x%d 0x%x\n", debug_wb_pc, debug_wb_inst, rd, Cat(Fill(wb_wbdata(31),32),wb_wbdata))
         }
         .otherwise
         {
            printf("@@@ 0x%x (0x%x)\n", debug_wb_pc, debug_wb_inst)
         }
      }
   }



   //**********************************
   // Handle Reset

   when (this.reset)
   {
      wb_reg_ctrl.rf_wen    := Bool(false)
      wb_reg_ctrl.csr_cmd   := CSR.N
      wb_reg_ctrl.dmem_val  := Bool(false)
      wb_reg_ctrl.exception := Bool(false)
      wb_reg_ctrl.sret      := Bool(false)
   }
 
}

 
}
