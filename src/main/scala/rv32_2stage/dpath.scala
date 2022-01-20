//**************************************************************************
// RISCV Processor 2-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2012 Jan 13

package sodor.stage2

import chisel3._
import chisel3.util._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket.{CSRFile, Causes}
import freechips.rocketchip.tile.CoreInterrupts

import Constants._
import sodor.common._

class DatToCtlIo(implicit val conf: SodorCoreParams) extends Bundle()
{
   val if_valid_resp = Output(Bool())
   val inst  = Output(UInt(32.W))
   val br_eq = Output(Bool())
   val br_lt = Output(Bool())
   val br_ltu= Output(Bool())
   val inst_misaligned = Output(Bool())
   val data_misaligned = Output(Bool())
   val mem_store = Output(Bool())
   val csr_eret = Output(Bool())
   val csr_interrupt = Output(Bool())
}

class DpathIo(implicit val p: Parameters, val conf: SodorCoreParams) extends Bundle()
{
   val ddpath = Flipped(new DebugDPath())
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val ctl  = Flipped(new CtlToDatIo())
   val dat  = new DatToCtlIo()
   val interrupt = Input(new CoreInterrupts())
   val hartid = Input(UInt())
   val reset_vector = Input(UInt())
}

class DatPath(implicit val p: Parameters, val conf: SodorCoreParams) extends Module
{
   val io = IO(new DpathIo())
   io := DontCare

   // Exception handling values
   val tval_data_ma = Wire(UInt(conf.xprlen.W))
   val tval_inst_ma = Wire(UInt(conf.xprlen.W))

   //**********************************
   // Pipeline State Registers
   val if_reg_pc = RegInit(io.reset_vector)

   val exe_reg_pc       = RegInit(0.asUInt(conf.xprlen.W))
   val exe_reg_pc_plus4 = RegInit(0.asUInt(conf.xprlen.W))
   val exe_reg_inst     = RegInit(BUBBLE)
   val exe_reg_valid    = RegInit(false.B)

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

   val if_pc_plus4 = (if_reg_pc + 4.asUInt(conf.xprlen.W))

   if_pc_next := MuxCase(if_pc_plus4, Array(
                  (io.ctl.pc_sel === PC_4)  -> if_pc_plus4,
                  (io.ctl.pc_sel === PC_BR) -> exe_br_target,
                  (io.ctl.pc_sel === PC_J ) -> exe_jmp_target,
                  (io.ctl.pc_sel === PC_JR) -> exe_jump_reg_target,
                  (io.ctl.pc_sel === PC_EXC)-> exception_target
                  ))

   // Instruction memory buffer; if the core is stalled and a multi-cycle request arrives, save it in the buffer and supply it to the pipeline once
   // the execution is resumed
   val if_inst_buffer = RegInit(0.U(32.W))
   val if_inst_buffer_valid = RegInit(false.B)
   when (io.ctl.stall) {
      when (io.imem.resp.valid) {
         if_inst_buffer_valid := true.B
         if_inst_buffer := io.imem.resp.bits.data
      }
   } .otherwise {
      // If resumed, clear the buffer
      if_inst_buffer := 0.U(32.W)
      if_inst_buffer_valid := false.B
   }

   // Connect CPath valid instruction fetch response to prevent livelock when fetch have multi-cycle delay
   io.dat.if_valid_resp := if_inst_buffer_valid || io.imem.resp.valid

   //Instruction Memory
   io.imem.req.valid    := !if_inst_buffer_valid
   io.imem.req.bits.fcn := M_XRD
   io.imem.req.bits.typ := MT_WU
   io.imem.req.bits.addr := if_reg_pc
   val if_inst = Mux(if_inst_buffer_valid, if_inst_buffer, io.imem.resp.bits.data)

   when(io.ctl.stall)
   {
      exe_reg_inst := exe_reg_inst
      exe_reg_pc   := exe_reg_pc
   }
   .elsewhen(io.ctl.if_kill)
   {
      exe_reg_inst := BUBBLE
      exe_reg_pc   := 0.U
      exe_reg_valid := false.B
   }
   .otherwise
   {
      exe_reg_inst := if_inst
      exe_reg_pc   := if_reg_pc
      exe_reg_valid := true.B
   }

   exe_reg_pc_plus4 := if_pc_plus4

   //**********************************
   // Execute Stage
   val exe_rs1_addr = exe_reg_inst(RS1_MSB, RS1_LSB)
   val exe_rs2_addr = exe_reg_inst(RS2_MSB, RS2_LSB)
   val exe_wbaddr   = exe_reg_inst(RD_MSB,  RD_LSB)

   val exe_wbdata = Wire(UInt(conf.xprlen.W))
   val exe_wben = io.ctl.rf_wen && !io.ctl.exception

   // Register File
   val regfile = Mem(32, UInt(conf.xprlen.W))

   //// DebugModule
   io.ddpath.rdata := regfile(io.ddpath.addr)
   when(io.ddpath.validreq){
      regfile(io.ddpath.addr) := io.ddpath.wdata
   }
   ///


   when (exe_wben && (exe_wbaddr =/= 0.U))
   {
      regfile(exe_wbaddr) := exe_wbdata
   }

   val exe_rs1_data = Mux((exe_rs1_addr =/= 0.U), regfile(exe_rs1_addr), 0.U)
   val exe_rs2_data = Mux((exe_rs2_addr =/= 0.U), regfile(exe_rs2_addr), 0.U)


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
               )).asUInt()

   val exe_alu_op2 = MuxCase(0.U, Array(
               (io.ctl.op2_sel === OP2_RS2) -> exe_rs2_data,
               (io.ctl.op2_sel === OP2_PC)  -> exe_reg_pc,
               (io.ctl.op2_sel === OP2_IMI) -> imm_i_sext,
               (io.ctl.op2_sel === OP2_IMS) -> imm_s_sext
               )).asUInt()


   // ALU
   val exe_alu_out   = Wire(UInt(conf.xprlen.W))

   val alu_shamt = exe_alu_op2(4,0).asUInt()

   exe_alu_out := MuxCase(0.U, Array(
                  (io.ctl.alu_fun === ALU_ADD)  -> (exe_alu_op1 + exe_alu_op2).asUInt(),
                  (io.ctl.alu_fun === ALU_SUB)  -> (exe_alu_op1 - exe_alu_op2).asUInt(),
                  (io.ctl.alu_fun === ALU_AND)  -> (exe_alu_op1 & exe_alu_op2).asUInt(),
                  (io.ctl.alu_fun === ALU_OR)   -> (exe_alu_op1 | exe_alu_op2).asUInt(),
                  (io.ctl.alu_fun === ALU_XOR)  -> (exe_alu_op1 ^ exe_alu_op2).asUInt(),
                  (io.ctl.alu_fun === ALU_SLT)  -> (exe_alu_op1.asSInt() < exe_alu_op2.asSInt()).asUInt(),
                  (io.ctl.alu_fun === ALU_SLTU) -> (exe_alu_op1 < exe_alu_op2).asUInt(),
                  (io.ctl.alu_fun === ALU_SLL)  -> ((exe_alu_op1 << alu_shamt)(conf.xprlen-1, 0)).asUInt(),
                  (io.ctl.alu_fun === ALU_SRA)  -> (exe_alu_op1.asSInt() >> alu_shamt).asUInt(),
                  (io.ctl.alu_fun === ALU_SRL)  -> (exe_alu_op1 >> alu_shamt).asUInt(),
                  (io.ctl.alu_fun === ALU_COPY1)-> exe_alu_op1
                  ))

   // Branch/Jump Target Calculation
   exe_br_target       := exe_reg_pc + imm_b_sext
   exe_jmp_target      := exe_reg_pc + imm_j_sext
   exe_jump_reg_target := (exe_rs1_data.asUInt() + imm_i_sext.asUInt()) & ~1.U(conf.xprlen.W)

   // Instruction misalignment detection
   // In control path, instruction misalignment exception is always raised in the next cycle once the misaligned instruction reaches
   // execution stage, regardless whether the pipeline stalls or not
   io.dat.inst_misaligned :=  (exe_br_target(1, 0).orR       && io.ctl.pc_sel_no_xept === PC_BR) ||
                              (exe_jmp_target(1, 0).orR      && io.ctl.pc_sel_no_xept === PC_J)  ||
                              (exe_jump_reg_target(1, 0).orR && io.ctl.pc_sel_no_xept === PC_JR)
   tval_inst_ma := MuxCase(0.U, Array(
                  (io.ctl.pc_sel_no_xept === PC_BR) -> exe_br_target,
                  (io.ctl.pc_sel_no_xept === PC_J)  -> exe_jmp_target,
                  (io.ctl.pc_sel_no_xept === PC_JR) -> exe_jump_reg_target
                  ))

   // Control Status Registers
   val csr = Module(new CSRFile(perfEventSets=CSREvents.events))
   csr.io := DontCare
   csr.io.decode(0).inst  := exe_reg_inst
   csr.io.rw.addr  := exe_reg_inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
   csr.io.rw.cmd   := io.ctl.csr_cmd
   csr.io.rw.wdata := exe_alu_out
   val csr_out = csr.io.rw.rdata

   csr.io.retire    := exe_reg_valid && !(io.ctl.stall || io.ctl.exception)
   csr.io.exception := io.ctl.exception
   csr.io.pc        := exe_reg_pc
   exception_target := csr.io.evec

   csr.io.tval := MuxCase(0.U, Array(
                  (io.ctl.exception_cause === Causes.illegal_instruction.U)     -> exe_reg_inst,
                  (io.ctl.exception_cause === Causes.misaligned_fetch.U)  -> tval_inst_ma,
                  (io.ctl.exception_cause === Causes.misaligned_store.U) -> tval_data_ma,
                  (io.ctl.exception_cause === Causes.misaligned_load.U)  -> tval_data_ma,
                  ))

   // Interrupt rising edge detector (output trap signal for one cycle on rising edge)
   val reg_interrupt_handled = RegInit(false.B)
   when (!io.ctl.stall) {
      reg_interrupt_handled := csr.io.interrupt
   }
   val interrupt_edge = csr.io.interrupt && !reg_interrupt_handled

   csr.io.interrupts := io.interrupt
   csr.io.hartid := io.hartid
   io.dat.csr_interrupt := interrupt_edge
   csr.io.cause := Mux(io.ctl.exception, io.ctl.exception_cause, csr.io.interrupt_cause)
   csr.io.ungated_clock := clock

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
   io.dat.br_lt  := (exe_rs1_data.asSInt() < exe_rs2_data.asSInt())
   io.dat.br_ltu := (exe_rs1_data.asUInt() < exe_rs2_data.asUInt())

   // Data misalignment detection
   // For example, if type is 3 (word), the mask is ~(0b111 << (3 - 1)) = ~0b100 = 0b011.
   val misaligned_mask = Wire(UInt(3.W))
   misaligned_mask := ~(7.U(3.W) << (io.ctl.mem_typ - 1.U)(1, 0))
   io.dat.data_misaligned := (misaligned_mask & exe_alu_out.asUInt.apply(2, 0)).orR && io.ctl.mem_val
   io.dat.mem_store := io.ctl.mem_fcn === M_XWR
   tval_data_ma := exe_alu_out.asUInt

   // datapath to data memory outputs
   io.dmem.req.bits.addr := exe_alu_out
   io.dmem.req.bits.data := exe_rs2_data.asUInt()


   // Printout
   printf("Cyc= %d [%d] pc=[%x] W[r%d=%x][%d] Op1=[r%d][%x] Op2=[r%d][%x] inst=[%x] %c%c%c DASM(%x)\n",
      csr.io.time(31,0),
      csr.io.retire,
      exe_reg_pc,
      exe_wbaddr,
      exe_wbdata,
      exe_wben,
      exe_rs1_addr,
      exe_alu_op1,
      exe_rs2_addr,
      exe_alu_op2,
      exe_reg_inst,
      MuxCase(Str(" "), Seq(
         io.ctl.stall -> Str("S"),
         io.ctl.if_kill -> Str("K"))),
      MuxLookup(io.ctl.pc_sel, Str("?"), Seq(
         PC_BR -> Str("B"),
         PC_J -> Str("J"),
         PC_JR -> Str("R"),
         PC_EXC -> Str("E"),
         PC_4 -> Str(" "))),
      Mux(csr.io.exception, Str("X"), Str(" ")),
      exe_reg_inst)

}
