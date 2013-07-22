//**************************************************************************
// RISCV Processor 2-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2012 Jan 13

package Sodor
{

import Chisel._
import Node._

import Constants._
import Common._
import Common.Constants._

class DatToCtlIo extends Bundle() 
{
   val inst  = Bits(OUTPUT, 32)
   val br_eq = Bool(OUTPUT)
   val br_lt = Bool(OUTPUT)
   val br_ltu= Bool(OUTPUT)
}

class DpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val host = new HTIFIO()
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val ctl  = new CtlToDatIo().flip()
   val dat  = new DatToCtlIo()
}

class DatPath(implicit conf: SodorConfiguration) extends Module
{
   val io = new DpathIo()
   
   //**********************************
   // Pipeline State Registers
   val if_reg_pc = RegReset(UInt(START_ADDR, conf.xprlen))
   
   val exe_reg_pc       = RegReset(UInt(0, conf.xprlen))
   val exe_reg_pc_plus4 = RegReset(UInt(0, conf.xprlen))
   val exe_reg_inst     = RegReset(BUBBLE)
   
   //**********************************
   // Instruction Fetch Stage
   val if_pc_next          = UInt()
   val exe_branch_target   = UInt()
   val exe_jump_target     = UInt()
   val exe_jump_reg_target = UInt()
 
   when (!io.ctl.stall)
   {
      if_reg_pc := if_pc_next
   }

   val if_pc_plus4 = (if_reg_pc + UInt(4, conf.xprlen))               

   if_pc_next := MuxCase(if_pc_plus4, Array(
                  (io.ctl.pc_sel === PC_4)  -> if_pc_plus4,
                  (io.ctl.pc_sel === PC_BR) -> exe_branch_target,
                  (io.ctl.pc_sel === PC_J ) -> exe_jump_target,
                  (io.ctl.pc_sel === PC_JR) -> exe_jump_reg_target
                  ))
   
   //Instruction Memory
   io.imem.req.bits.addr := if_reg_pc
   val if_inst = io.imem.resp.bits.data
                 
   when(io.ctl.stall) 
   {
      exe_reg_inst := exe_reg_inst
      exe_reg_pc   := exe_reg_pc
   }
   .elsewhen(io.ctl.if_kill) 
   {
      exe_reg_inst := BUBBLE
      exe_reg_pc   := UInt(0)
   } 
   .otherwise 
   {
      exe_reg_inst := if_inst
      exe_reg_pc   := if_reg_pc
   }

   exe_reg_pc_plus4 := if_pc_plus4
   
   //**********************************
   // Execute Stage
   val exe_rs1_addr = exe_reg_inst(26, 22).toUInt
   val exe_rs2_addr = exe_reg_inst(21, 17).toUInt
   val exe_wbaddr  = Mux(io.ctl.wa_sel, exe_reg_inst(31, 27).toUInt,
                                        RA)
   
   val exe_wbdata = Bits(width = conf.xprlen)
 
   // Register File
   val regfile = Mem(32, Bits(width = conf.xprlen))

   when (io.ctl.rf_wen && (exe_wbaddr != UInt(0)))
   {
      regfile(exe_wbaddr) := exe_wbdata
   }

   val exe_rs1_data = Mux((exe_rs1_addr != UInt(0)), regfile(exe_rs1_addr), UInt(0, conf.xprlen))
   val exe_rs2_data = Mux((exe_rs2_addr != UInt(0)), regfile(exe_rs2_addr), UInt(0, conf.xprlen))
   
   
   // immediates
   val imm_i12 = exe_reg_inst(21,10)
   val imm_b12 = Cat(exe_reg_inst(31,27), exe_reg_inst(16,10))
   val imm_ui  = Cat(exe_reg_inst(26,7), Fill(Bits(0,1), 12))
   val imm_jmp = exe_reg_inst(31,7)

   // sign-extend immediates
   val imm_i12_sext = Cat(Fill(imm_i12(11), 20), imm_i12)
   val imm_b12_sext = Cat(Fill(imm_b12(11), 20), imm_b12)
   val imm_j25_sext = Cat(Fill(imm_jmp(24),  7), imm_jmp)
   
   
   val exe_alu_op1 = MuxCase(UInt(0), Array(
               (io.ctl.op1_sel === OP1_RS1) -> exe_rs1_data,
               (io.ctl.op1_sel === OP1_PC)  -> exe_reg_pc
               )).toUInt
   
   val exe_alu_op2 = MuxCase(UInt(0), Array(
               (io.ctl.op2_sel === OP2_RS2) -> exe_rs2_data,
               (io.ctl.op2_sel === OP2_IMI) -> imm_i12_sext,
               (io.ctl.op2_sel === OP2_IMB) -> imm_b12_sext,
               (io.ctl.op2_sel === OP2_UI)  -> imm_ui
               )).toUInt
  

   // ALU
   val exe_alu_out   = UInt(width = conf.xprlen)
   
   val alu_shamt = exe_alu_op2(4,0).toUInt
   
   exe_alu_out := MuxCase(UInt(0), Array(
                  (io.ctl.alu_fun === ALU_ADD)  -> (exe_alu_op1 + exe_alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_SUB)  -> (exe_alu_op1 - exe_alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_AND)  -> (exe_alu_op1 & exe_alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_OR)   -> (exe_alu_op1 | exe_alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_XOR)  -> (exe_alu_op1 ^ exe_alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_SLT)  -> (exe_alu_op1.toSInt < exe_alu_op2.toSInt).toUInt,
                  (io.ctl.alu_fun === ALU_SLTU) -> (exe_alu_op1 < exe_alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_SLL)  -> ((exe_alu_op1 << alu_shamt)(conf.xprlen-1, 0)).toUInt,
                  (io.ctl.alu_fun === ALU_SRA)  -> (exe_alu_op1.toSInt >> alu_shamt).toUInt,
                  (io.ctl.alu_fun === ALU_SRL)  -> (exe_alu_op1 >> alu_shamt).toUInt,
                  (io.ctl.alu_fun === ALU_COPY2)-> exe_alu_op2 
                  ))

   // Branch/Jump Target Calculation
   val simm12_sh1 = Cat(imm_b12_sext, UInt(0,1)) 
   exe_branch_target   := exe_reg_pc + simm12_sh1.toUInt
   exe_jump_target     := exe_reg_pc + Cat(imm_j25_sext(conf.xprlen-1,0), UInt(0, 1)).toUInt
   exe_jump_reg_target := (exe_rs1_data.toUInt + imm_i12_sext.toUInt)
                                  
   

   // Co-processor Registers
   val pcr = Module(new PCR())
   pcr.io.host <> io.host
   pcr.io.r.addr := exe_rs1_addr
   pcr.io.r.en   := io.ctl.pcr_fcn != PCR_N
   val pcr_out = pcr.io.r.data
   pcr.io.w.addr := exe_rs1_addr
   pcr.io.w.en   := io.ctl.pcr_fcn === PCR_T
   pcr.io.w.data := exe_rs2_data

 
   // WB Mux
   exe_wbdata := MuxCase(exe_alu_out, Array(
                  (io.ctl.wb_sel === WB_ALU) -> exe_alu_out,
                  (io.ctl.wb_sel === WB_MEM) -> io.dmem.resp.bits.data, 
                  (io.ctl.wb_sel === WB_PC4) -> exe_reg_pc_plus4,
                  (io.ctl.wb_sel === WB_PCR) -> pcr_out
                  )).toSInt()
                                  

   // datapath to controlpath outputs
   io.dat.inst   := exe_reg_inst.toSInt
   io.dat.br_eq  := (exe_rs1_data === exe_rs2_data)
   io.dat.br_lt  := (exe_rs1_data.toSInt < exe_rs2_data.toSInt) 
   io.dat.br_ltu := (exe_rs1_data.toUInt < exe_rs2_data.toUInt)
   
   
   // datapath to data memory outputs
   io.dmem.req.bits.addr := exe_alu_out
   io.dmem.req.bits.data := exe_rs2_data.toUInt 
         

   // Time Stamp Counter & Retired Instruction Counter 
   val tsc_reg = RegReset(UInt(0, conf.xprlen))
   tsc_reg := tsc_reg + UInt(1)

   val irt_reg = RegReset(UInt(0, conf.xprlen))
   when (!io.ctl.stall && !io.ctl.if_kill) { irt_reg := irt_reg + UInt(1) }

        
   
   // Printout
   printf("Cyc= %d PC= (0x%x,0x%x) [%s,%s] Exe: %s %s%s%s Op1=[0x%x] Op2=[0x%x] W[%s,%d= 0x%x]\n"
      , tsc_reg(31,0)
      , if_reg_pc
      , exe_reg_pc
      , Disassemble(if_inst, true)
      , Disassemble(exe_reg_inst, true)
      , Disassemble(exe_reg_inst)
      , Mux(io.ctl.stall, Str("stall"), Str("     "))
      , Mux(io.ctl.if_kill, Str("KILL"), Str("     "))
      , Mux(io.ctl.pc_sel  === UInt(1), Str("BR"),
         Mux(io.ctl.pc_sel === UInt(2), Str("J "),
         Mux(io.ctl.pc_sel === UInt(3), Str("JR"),
         Mux(io.ctl.pc_sel === UInt(4), Str("EX"),
         Mux(io.ctl.pc_sel === UInt(0), Str("  "), Str("??"))))))
      , exe_alu_op1
      , exe_alu_op2
      , Mux(io.ctl.rf_wen, Str("W"), Str("_"))
      , exe_wbaddr
      , exe_wbdata
      )
 
}

 
}
