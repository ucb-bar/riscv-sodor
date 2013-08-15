//**************************************************************************
// RISCV Processor 1-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2012 Jan 11

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
   val status = new Status().asOutput()
}

class DpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val host  = new HTIFIO()
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val ctl  = new CtlToDatIo().flip()
   val dat  = new DatToCtlIo()
}

class DatPath(implicit conf: SodorConfiguration) extends Module
{
   val io = new DpathIo()
   
   
   // Instruction Fetch
   val pc_next          = UInt()
   val pc_plus4         = UInt()
   val branch_target    = UInt()
   val jump_target      = UInt()
   val jump_reg_target  = UInt()
   val exception_target = UInt()
 
   // PC Register
   val pc_reg = Reg(init=UInt(START_ADDR, conf.xprlen))

   when (!io.ctl.stall) 
   {
      pc_reg := pc_next
   }

   pc_plus4 := (pc_reg + UInt(4, conf.xprlen))               

   pc_next := MuxCase(pc_plus4, Array(
                  (io.ctl.pc_sel === PC_4)   -> pc_plus4,
                  (io.ctl.pc_sel === PC_BR)  -> branch_target,
                  (io.ctl.pc_sel === PC_J )  -> jump_target,
                  (io.ctl.pc_sel === PC_JR)  -> jump_reg_target,
                  (io.ctl.pc_sel === PC_EXC) -> exception_target
                  ))
   
   io.imem.req.bits.addr := pc_reg
   val inst = Mux(io.imem.resp.valid, io.imem.resp.bits.data, BUBBLE) 
                 
   
   // Decode
   val rs1_addr = inst(26, 22).toUInt
   val rs2_addr = inst(21, 17).toUInt
   val X1       = UInt(1)
   val wb_addr  = Mux(io.ctl.wa_sel, inst(31, 27).toUInt,
                                     X1)
   
   val wb_data = Bits(width = conf.xprlen)
 
   // Register File
   val regfile = Mem(Bits(width = conf.xprlen), 32)

   when (io.ctl.rf_wen && (wb_addr != UInt(0)))
   {
      regfile(wb_addr) := wb_data
   }

   val rs1_data = Mux((rs1_addr != UInt(0)), regfile(rs1_addr), UInt(0, conf.xprlen))
   val rs2_data = Mux((rs2_addr != UInt(0)), regfile(rs2_addr), UInt(0, conf.xprlen))
   
   
   // immediates
   val imm_btype = Cat(inst(31,27), inst(16,10))
   val imm_itype = inst(21,10)
   val imm_utype = Cat(inst(26,7), Fill(Bits(0,1), 12))
   val imm_jtype = inst(31,7)

   // sign-extend immediates
   val imm_itype_sext = Cat(Fill(imm_itype(11), 20), imm_itype)
   val imm_btype_sext = Cat(Fill(imm_btype(11), 20), imm_btype)
   val imm_jtype_sext = Cat(Fill(imm_jtype(24),  7), imm_jtype)

   
   // Operand Muxes
   val alu_op1 = MuxCase(UInt(0), Array(
               (io.ctl.op1_sel === OP1_RS1) -> rs1_data,
               (io.ctl.op1_sel === OP1_PC)  -> pc_reg
               )).toUInt
   
   val alu_op2 = MuxCase(UInt(0), Array(
               (io.ctl.op2_sel === OP2_RS2) -> rs2_data,
               (io.ctl.op2_sel === OP2_IMI) -> imm_itype_sext,
               (io.ctl.op2_sel === OP2_IMB) -> imm_btype_sext,
               (io.ctl.op2_sel === OP2_UI)  -> imm_utype
               )).toUInt
  

   // ALU
   val alu_out   = UInt(width = conf.xprlen)
   
   val alu_shamt = alu_op2(4,0).toUInt
   
   alu_out := MuxCase(UInt(0), Array(
                  (io.ctl.alu_fun === ALU_ADD)  -> (alu_op1 + alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_SUB)  -> (alu_op1 - alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_AND)  -> (alu_op1 & alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_OR)   -> (alu_op1 | alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_XOR)  -> (alu_op1 ^ alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_SLT)  -> (alu_op1.toSInt < alu_op2.toSInt).toUInt,
                  (io.ctl.alu_fun === ALU_SLTU) -> (alu_op1 < alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_SLL)  -> ((alu_op1 << alu_shamt)(conf.xprlen-1, 0)).toUInt,
                  (io.ctl.alu_fun === ALU_SRA)  -> (alu_op1.toSInt >> alu_shamt).toUInt,
                  (io.ctl.alu_fun === ALU_SRL)  -> (alu_op1 >> alu_shamt).toUInt,
                  (io.ctl.alu_fun === ALU_COPY2)-> alu_op2 
                  ))

   // Branch/Jump Target Calculation
   val simm12_sh1  = Cat(imm_btype_sext, UInt(0,1)) 
   branch_target   := pc_reg + simm12_sh1.toUInt
   jump_target     := pc_reg + Cat(imm_jtype_sext(conf.xprlen-1,0), UInt(0, 1)).toUInt
   jump_reg_target := (rs1_data.toUInt + imm_itype_sext.toUInt)
                                  
   
   // Privileged Co-processor Registers
   val pcr = Module(new PCR())
   pcr.io.host <> io.host
   pcr.io.r.addr := rs1_addr
   pcr.io.r.en   := io.ctl.pcr_fcn != PCR_N
   val pcr_out = pcr.io.r.data
   pcr.io.w.addr := rs1_addr
   pcr.io.w.en   := (io.ctl.pcr_fcn === PCR_T) || (io.ctl.pcr_fcn === PCR_S) || (io.ctl.pcr_fcn === PCR_C)
   pcr.io.w.data := Mux(io.ctl.pcr_fcn === PCR_S, pcr.io.r.data | alu_out,
                    Mux(io.ctl.pcr_fcn === PCR_C, pcr.io.r.data & ~alu_out,
                                                  alu_out))
   
   io.dat.status := pcr.io.status
   pcr.io.exception := io.ctl.exception
   pcr.io.cause  := io.ctl.exc_cause
   pcr.io.eret   := io.ctl.eret
   pcr.io.pc     := pc_reg
   exception_target := pcr.io.evec

   // Time Stamp Counter & Retired Instruction Counter 
   val tsc_reg = Reg(init=UInt(0, conf.xprlen))
   tsc_reg := tsc_reg + UInt(1)

   val irt_reg = Reg(init=UInt(0, conf.xprlen))
   when (!io.ctl.stall && !io.ctl.exception) { irt_reg := irt_reg + UInt(1) }

  

   // WB Mux
   wb_data := MuxCase(alu_out, Array(
                  (io.ctl.wb_sel === WB_ALU) -> alu_out,
                  (io.ctl.wb_sel === WB_MEM) -> io.dmem.resp.bits.data, 
                  (io.ctl.wb_sel === WB_PC4) -> pc_plus4,
                  (io.ctl.wb_sel === WB_PCR) -> pcr_out,
                  (io.ctl.wb_sel === WB_TSC) -> tsc_reg,
                  (io.ctl.wb_sel === WB_IRT) -> irt_reg
                  )).toSInt()
                                  

   // datapath to controlpath outputs
   io.dat.inst   := inst
   io.dat.br_eq  := (rs1_data === rs2_data)
   io.dat.br_lt  := (rs1_data.toSInt < rs2_data.toSInt) 
   io.dat.br_ltu := (rs1_data.toUInt < rs2_data.toUInt)
   
   
   // datapath to data memory outputs
   io.dmem.req.bits.addr  := alu_out
   io.dmem.req.bits.data := rs2_data.toUInt 
   
   
   // Printout
   printf("Cyc= %d PC= 0x%x %s %s%s Op1=[0x%x] Op2=[0x%x] W[%s,%d= 0x%x] %s Mem[%s %d: 0x%x]\n"
      , tsc_reg(31,0)
      , pc_reg
      , Disassemble(inst)
      , Mux(io.ctl.stall, Str("stall"), Str("     "))
      , Mux(io.ctl.pc_sel  === UInt(1), Str("BR"),
         Mux(io.ctl.pc_sel === UInt(2), Str("J "),
         Mux(io.ctl.pc_sel === UInt(3), Str("JR"),
         Mux(io.ctl.pc_sel === UInt(4), Str("EX"),
         Mux(io.ctl.pc_sel === UInt(0), Str("  "), Str("??"))))))
      , alu_op1
      , alu_op2
      , Mux(io.ctl.rf_wen, Str("W"), Str("_"))
      , wb_addr
      , wb_data
      , Mux(io.ctl.exception, Str("EXC"), Str("   "))
      , Mux(io.ctl.debug_dmem_val, Str("V"), Str("_"))
      , io.ctl.debug_dmem_typ
      , io.dmem.resp.bits.data
      )
}

 
}
