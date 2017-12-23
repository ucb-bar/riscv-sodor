//**************************************************************************
// RISCV Processor 2-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2012 Jan 13

package Sodor
{

import chisel3._
import chisel3.util._
import config._
import Constants._
import Common._
import Common.Constants._

class DatToCtlIo(implicit p: Parameters) extends Bundle() 
{
   val inst  = Output(UInt(p(xprlen).W))
   val br_eq = Output(Bool())
   val br_lt = Output(Bool())
   val br_ltu= Output(Bool())
   val csr_eret = Output(Bool())
   override def cloneType = { new DatToCtlIo().asInstanceOf[this.type] }
}

class DpathIo(implicit p: Parameters) extends Bundle() 
{
   val ddpath = Flipped(new DebugDPath())
   val imem = new MemPortIo(p(xprlen))
   val dmem = new MemPortIo(p(xprlen))
   val ctl  = Flipped(new CtlToDatIo())
   val dat  = new DatToCtlIo()
}

class DatPath(implicit p: Parameters) extends Module
{
   val io = IO(new DpathIo())
   //Initialize IO
   io.dmem.req.bits := new MemReq(p(xprlen)).fromBits(0.U)
   io.imem.req.bits := new MemReq(p(xprlen)).fromBits(0.U)
   io.imem.req.valid := false.B
   io.dmem.req.valid := false.B
   io.imem.resp.ready := true.B
   io.dmem.resp.ready := true.B
   val xlen = p(xprlen)
   //**********************************
   // Pipeline State Registers
   val if_reg_pc = Reg(init = START_ADDR) 
   
   val exe_reg_pc       = Reg(init=0.asUInt(xlen.W))
   val exe_reg_pc_plus4 = Reg(init=0.asUInt(xlen.W))
   val exe_reg_inst     = Reg(init=BUBBLE)
   
   //**********************************
   // Instruction Fetch Stage
   val if_pc_next          = Wire(UInt(32.W))
   val exe_br_target       = Wire(UInt(32.W))
   val exe_jmp_target      = Wire(UInt(32.W))
   val exe_jump_reg_target = Wire(UInt(32.W))
   val exception_target    = Wire(UInt(32.W))
 
   when (!io.ctl.stall)
   {
      if_reg_pc := if_pc_next
   }

   val if_pc_plus4 = (if_reg_pc + 4.asUInt(xlen.W))               

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
      exe_reg_pc   := 0.U
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
   
   val exe_wbdata = Wire(UInt(xlen.W))
 
   // Register File
   val regfile = Mem(UInt(xlen.W),32)

   //// DebugModule
   io.ddpath.rdata := regfile(io.ddpath.addr)
   when(io.ddpath.validreq){
      regfile(io.ddpath.addr) := io.ddpath.wdata
   }
   ///


   when (io.ctl.rf_wen && (exe_wbaddr != 0.U) && !io.ctl.illegal)
   {
      regfile(exe_wbaddr) := exe_wbdata
   }

   val exe_rs1_data = Mux((exe_rs1_addr != 0.U), regfile(exe_rs1_addr), 0.U)
   val exe_rs2_data = Mux((exe_rs2_addr != 0.U), regfile(exe_rs2_addr), 0.U)
   
   
   // immediates
   val imm_i = exe_reg_inst(31, 20) 
   val imm_s = Cat(exe_reg_inst(31, 25), exe_reg_inst(11,7))
   val imm_b = Cat(exe_reg_inst(31), exe_reg_inst(7), exe_reg_inst(30,25), exe_reg_inst(11,8))
   val imm_u = exe_reg_inst(31, 12)
   val imm_j = Cat(exe_reg_inst(31), exe_reg_inst(19,12), exe_reg_inst(20), exe_reg_inst(30,21))
   val imm_z = Cat(Fill(27,0.U), exe_reg_inst(19,15))

   // sign-extend immediates
   val imm_i_sext = Cat(Fill(20,imm_i(11)), imm_i)
   val imm_s_sext = Cat(Fill(20,imm_s(11)), imm_s)
   val imm_b_sext = Cat(Fill(19,imm_b(11)), imm_b, 0.U)
   val imm_u_sext = Cat(imm_u, Fill(12,0.U))
   val imm_j_sext = Cat(Fill(11,imm_j(19)), imm_j, 0.U)
   
   
   val exe_alu_op1 = MuxCase(0.U, Array(
               (io.ctl.op1_sel === OP1_RS1) -> exe_rs1_data,
               (io.ctl.op1_sel === OP1_IMU) -> imm_u_sext,
               (io.ctl.op1_sel === OP1_IMZ) -> imm_z
               )).toUInt
   
   val exe_alu_op2 = MuxCase(0.U, Array(
               (io.ctl.op2_sel === OP2_RS2) -> exe_rs2_data,
               (io.ctl.op2_sel === OP2_PC)  -> exe_reg_pc,
               (io.ctl.op2_sel === OP2_IMI) -> imm_i_sext,
               (io.ctl.op2_sel === OP2_IMS) -> imm_s_sext
               )).toUInt
  

   // ALU
   val exe_alu_out   = Wire(UInt(xlen.W))
   
   val alu_shamt = exe_alu_op2(4,0).toUInt
   
   exe_alu_out := MuxCase(0.U, Array(
                  (io.ctl.alu_fun === ALU_ADD)  -> (exe_alu_op1 + exe_alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_SUB)  -> (exe_alu_op1 - exe_alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_AND)  -> (exe_alu_op1 & exe_alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_OR)   -> (exe_alu_op1 | exe_alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_XOR)  -> (exe_alu_op1 ^ exe_alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_SLT)  -> (exe_alu_op1.toSInt < exe_alu_op2.toSInt).toUInt,
                  (io.ctl.alu_fun === ALU_SLTU) -> (exe_alu_op1 < exe_alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_SLL)  -> ((exe_alu_op1 << alu_shamt)(xlen-1, 0)).toUInt,
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
   csr.io.decode.csr  := exe_reg_inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
   csr.io.rw.cmd   := io.ctl.csr_cmd
   csr.io.rw.wdata := exe_alu_out
   val csr_out = csr.io.rw.rdata

   csr.io.retire    := !io.ctl.stall && !io.ctl.if_kill
   csr.io.illegal := io.ctl.illegal
   csr.io.pc        := exe_reg_pc
   exception_target := csr.io.evec
                    
   io.dat.csr_eret := csr.io.eret
        
   // Add your own uarch counters here!
   csr.io.counters.foreach(_.inc := false.B)

 
   // WB Mux
   exe_wbdata := MuxCase(exe_alu_out, Array(
                  (io.ctl.wb_sel === WB_ALU) -> exe_alu_out,
                  (io.ctl.wb_sel === WB_MEM) -> io.dmem.resp.bits.data, 
                  (io.ctl.wb_sel === WB_PC4) -> exe_reg_pc_plus4,
                  (io.ctl.wb_sel === WB_CSR) -> csr_out
                  ))
                                  

   // datapath to controlpath outputs
   io.dat.inst   := exe_reg_inst
   io.dat.br_eq  := (exe_rs1_data === exe_rs2_data)
   io.dat.br_lt  := (exe_rs1_data.toSInt < exe_rs2_data.toSInt) 
   io.dat.br_ltu := (exe_rs1_data.toUInt < exe_rs2_data.toUInt)
   
   
   // datapath to data memory outputs
   io.dmem.req.bits.addr := exe_alu_out
   io.dmem.req.bits.data := exe_rs2_data.toUInt 
         

   // Time Stamp Counter & Retired Instruction Counter 
   val tsc_reg = Reg(init=0.asUInt(xlen.W))
   tsc_reg := tsc_reg + 1.U

   val irt_reg = Reg(init=0.asUInt(xlen.W))
   when (!io.ctl.stall && !io.ctl.if_kill) { irt_reg := irt_reg + 1.U }
        
   // Printout
   printf("Cyc= %d Op1=[0x%x] Op2=[0x%x] W[%c,%d= 0x%x] PC= (0x%x,0x%x) [%x,%x] %c%c%c Exe: DASM(%x)\n"
      , tsc_reg(31,0)
      , exe_alu_op1
      , exe_alu_op2
      , Mux(io.ctl.rf_wen, Str("W"), Str("_"))
      , exe_wbaddr
      , exe_wbdata
      , if_reg_pc
      , exe_reg_pc
      , if_inst(6,0)
      , exe_reg_inst(6,0)
      , Mux(io.ctl.stall, Str("S"), Str(" ")) //stall -> S
      , Mux(io.ctl.if_kill, Str("K"), Str(" ")) // Kill -> K
      , Mux(io.ctl.pc_sel  === UInt(1), Str("B"), // BR -> B
         Mux(io.ctl.pc_sel === UInt(2), Str("J"),
         Mux(io.ctl.pc_sel === UInt(3), Str("R"),  // JR -> R
         Mux(io.ctl.pc_sel === UInt(4), Str("E"),  // EX -> E
         Mux(io.ctl.pc_sel === UInt(0), Str(" "), Str("?"))))))
      , exe_reg_inst
      )
}

 
}
