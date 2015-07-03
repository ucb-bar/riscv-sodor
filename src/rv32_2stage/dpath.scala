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

class DatToCtlIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val inst  = Bits(OUTPUT, 32)
   val br_eq = Bool(OUTPUT)
   val br_lt = Bool(OUTPUT)
   val br_ltu= Bool(OUTPUT)
   val csr_eret = Bool(OUTPUT)
   val csr_interrupt = Bool(OUTPUT)
   val csr_xcpt = Bool(OUTPUT)
   val csr_interrupt_cause = UInt(OUTPUT, conf.xprlen)
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
   val if_reg_pc = Reg(init=UInt(START_ADDR, conf.xprlen))
   
   val exe_reg_pc       = Reg(init=UInt(0, conf.xprlen))
   val exe_reg_pc_plus4 = Reg(init=UInt(0, conf.xprlen))
   val exe_reg_inst     = Reg(init=BUBBLE)
   
   //**********************************
   // Instruction Fetch Stage
   val if_pc_next          = UInt()
   val exe_br_target       = UInt()
   val exe_jmp_target      = UInt()
   val exe_jump_reg_target = UInt()
   val exception_target    = UInt()
 
   when (!io.ctl.stall)
   {
      if_reg_pc := if_pc_next
   }

   val if_pc_plus4 = (if_reg_pc + UInt(4, conf.xprlen))               

   if_pc_next := MuxCase(if_pc_plus4, Array(
                  (io.ctl.pc_sel === PC_4)  -> if_pc_plus4,
                  (io.ctl.pc_sel === PC_BR) -> exe_br_target,
                  (io.ctl.pc_sel === PC_J ) -> exe_jmp_target,
                  (io.ctl.pc_sel === PC_JR) -> exe_jump_reg_target,
                  (io.ctl.pc_sel === PC_EXC)-> exception_target
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
   val exe_rs1_addr = exe_reg_inst(RS1_MSB, RS1_LSB)
   val exe_rs2_addr = exe_reg_inst(RS2_MSB, RS2_LSB)
   val exe_wbaddr   = exe_reg_inst(RD_MSB,  RD_LSB)
   
   val exe_wbdata = Bits(width = conf.xprlen)
 
   // Register File
   val regfile = Mem(Bits(width = conf.xprlen), 32)

   when (io.ctl.rf_wen && (exe_wbaddr != UInt(0)) && !io.dat.csr_xcpt)
   {
      regfile(exe_wbaddr) := exe_wbdata
   }

   val exe_rs1_data = Mux((exe_rs1_addr != UInt(0)), regfile(exe_rs1_addr), UInt(0, conf.xprlen))
   val exe_rs2_data = Mux((exe_rs2_addr != UInt(0)), regfile(exe_rs2_addr), UInt(0, conf.xprlen))
   
   
   // immediates
   val imm_i = exe_reg_inst(31, 20) 
   val imm_s = Cat(exe_reg_inst(31, 25), exe_reg_inst(11,7))
   val imm_b = Cat(exe_reg_inst(31), exe_reg_inst(7), exe_reg_inst(30,25), exe_reg_inst(11,8))
   val imm_u = exe_reg_inst(31, 12)
   val imm_j = Cat(exe_reg_inst(31), exe_reg_inst(19,12), exe_reg_inst(20), exe_reg_inst(30,21))
   val imm_z = Cat(Fill(UInt(0), 27), exe_reg_inst(19,15))

   // sign-extend immediates
   val imm_i_sext = Cat(Fill(imm_i(11), 20), imm_i)
   val imm_s_sext = Cat(Fill(imm_s(11), 20), imm_s)
   val imm_b_sext = Cat(Fill(imm_b(11), 19), imm_b, UInt(0))
   val imm_u_sext = Cat(imm_u, Fill(UInt(0), 12))
   val imm_j_sext = Cat(Fill(imm_j(19), 11), imm_j, UInt(0))
   
   
   val exe_alu_op1 = MuxCase(UInt(0), Array(
               (io.ctl.op1_sel === OP1_RS1) -> exe_rs1_data,
               (io.ctl.op1_sel === OP1_IMU) -> imm_u_sext,
               (io.ctl.op1_sel === OP1_IMZ) -> imm_z
               )).toUInt
   
   val exe_alu_op2 = MuxCase(UInt(0), Array(
               (io.ctl.op2_sel === OP2_RS2) -> exe_rs2_data,
               (io.ctl.op2_sel === OP2_PC)  -> exe_reg_pc,
               (io.ctl.op2_sel === OP2_IMI) -> imm_i_sext,
               (io.ctl.op2_sel === OP2_IMS) -> imm_s_sext
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
                  (io.ctl.alu_fun === ALU_COPY1)-> exe_alu_op1 
                  ))

   // Branch/Jump Target Calculation
   exe_br_target       := exe_reg_pc + imm_b_sext
   exe_jmp_target      := exe_reg_pc + imm_j_sext
   exe_jump_reg_target := (exe_rs1_data.toUInt + imm_i_sext.toUInt)
   

   // Control Status Registers
   val csr = Module(new CSRFile())
   csr.io.host <> io.host
   csr.io.rw.addr  := exe_reg_inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
   csr.io.rw.cmd   := io.ctl.csr_cmd
   csr.io.rw.wdata := exe_alu_out
   val csr_out = csr.io.rw.rdata

   csr.io.retire    := !io.ctl.stall // TODO verify this works properly
   csr.io.exception := io.ctl.exception
   csr.io.cause     := io.ctl.exc_cause
   csr.io.pc        := exe_reg_pc
   exception_target := csr.io.evec
                    
   io.dat.csr_eret := csr.io.eret
   io.dat.csr_xcpt := csr.io.csr_xcpt
   io.dat.csr_interrupt := csr.io.interrupt
   io.dat.csr_interrupt_cause := csr.io.interrupt_cause
   // TODO replay? stall?
        
   // Add your own uarch counters here!
   csr.io.uarch_counters.foreach(_ := Bool(false))

 
   // WB Mux
   exe_wbdata := MuxCase(exe_alu_out, Array(
                  (io.ctl.wb_sel === WB_ALU) -> exe_alu_out,
                  (io.ctl.wb_sel === WB_MEM) -> io.dmem.resp.bits.data, 
                  (io.ctl.wb_sel === WB_PC4) -> exe_reg_pc_plus4,
                  (io.ctl.wb_sel === WB_CSR) -> csr_out
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
   val tsc_reg = Reg(init=UInt(0, conf.xprlen))
   tsc_reg := tsc_reg + UInt(1)

   val irt_reg = Reg(init=UInt(0, conf.xprlen))
   when (!io.ctl.stall && !io.ctl.if_kill) { irt_reg := irt_reg + UInt(1) }
        
   
   // Printout
   // TODO: provide a way to provide a disassembly on just the opcode.
   // left as "n/a" for now.
   printf("Cyc= %d Op1=[0x%x] Op2=[0x%x] W[%s,%d= 0x%x] PC= (0x%x,0x%x) [%x,%x] %s%s%s Exe: DASM(%x)\n"
      , tsc_reg(31,0)
      , exe_alu_op1
      , exe_alu_op2
      , Mux(io.ctl.rf_wen, Str("W"), Str("_"))
      , exe_wbaddr
      , exe_wbdata
      , if_reg_pc
      , exe_reg_pc
//      , Disassemble(if_inst, true)
//      , Disassemble(exe_reg_inst, true)
      , if_inst(6,0)
      , exe_reg_inst(6,0)
      , Mux(io.ctl.stall, Str("stall"), Str("     "))
      , Mux(io.ctl.if_kill, Str("KILL"), Str("     "))
      , Mux(io.ctl.pc_sel  === UInt(1), Str("BR"),
         Mux(io.ctl.pc_sel === UInt(2), Str("J "),
         Mux(io.ctl.pc_sel === UInt(3), Str("JR"),
         Mux(io.ctl.pc_sel === UInt(4), Str("EX"),
         Mux(io.ctl.pc_sel === UInt(0), Str("  "), Str("??"))))))
      , exe_reg_inst
      )
}

 
}
