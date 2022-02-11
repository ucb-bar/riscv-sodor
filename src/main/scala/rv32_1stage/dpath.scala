//**************************************************************************
// RISCV Processor 1-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2012 Jan 11

package sodor.stage1

import chisel3._
import chisel3.util._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket.{CSRFile, Causes}
import freechips.rocketchip.tile.CoreInterrupts

import Constants._
import sodor.common._

class DatToCtlIo(implicit val conf: SodorCoreParams) extends Bundle()
{
   val inst   = Output(UInt(32.W))
   val imiss  = Output(Bool())
   val br_eq  = Output(Bool())
   val br_lt  = Output(Bool())
   val br_ltu = Output(Bool())
   val csr_eret = Output(Bool())
   val csr_interrupt = Output(Bool())
   val inst_misaligned = Output(Bool())
   val mem_address_low = Output(UInt(3.W))
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

   // Interrupt kill
   val interrupt_edge = Wire(Bool())

   // Instruction Fetch
   val pc_next          = Wire(UInt(32.W))
   val pc_plus4         = Wire(UInt(32.W))
   val br_target        = Wire(UInt(32.W))
   val jmp_target       = Wire(UInt(32.W))
   val jump_reg_target  = Wire(UInt(32.W))
   val exception_target = Wire(UInt(32.W))

   // PC Register
   pc_next := MuxCase(pc_plus4, Array(
                  (io.ctl.pc_sel === PC_4)   -> pc_plus4,
                  (io.ctl.pc_sel === PC_BR)  -> br_target,
                  (io.ctl.pc_sel === PC_J )  -> jmp_target,
                  (io.ctl.pc_sel === PC_JR)  -> jump_reg_target,
                  (io.ctl.pc_sel === PC_EXC) -> exception_target
                  ))

   val pc_reg = RegInit(io.reset_vector)

   when (!io.ctl.stall)
   {
      pc_reg := pc_next
   }

   pc_plus4 := (pc_reg + 4.asUInt(conf.xprlen.W))


   // Instruction memory buffer to store instruction during multicycle data request
   io.dat.imiss := (io.imem.req.valid && !io.imem.resp.valid)
   val reg_dmiss = RegNext(io.ctl.dmiss, false.B)
   val if_inst_buffer = RegInit(0.U(32.W))
   when (io.imem.resp.valid) {
      assert(!reg_dmiss, "instruction arrived during data miss")
      if_inst_buffer := io.imem.resp.bits.data
   }

   io.imem.req.bits.fcn := M_XRD
   io.imem.req.bits.typ := MT_WU
   io.imem.req.bits.addr := pc_reg
   io.imem.req.valid := !reg_dmiss
   val inst = Mux(reg_dmiss, if_inst_buffer, io.imem.resp.bits.data)

   // Instruction misalign detection
   // In control path, instruction misalignment exception is always raised in the next cycle once the misaligned instruction reaches
   // execution stage, regardless whether the pipeline stalls or not
   io.dat.inst_misaligned :=  (br_target(1, 0).orR       && io.ctl.pc_sel_no_xept === PC_BR) ||
                              (jmp_target(1, 0).orR      && io.ctl.pc_sel_no_xept === PC_J)  ||
                              (jump_reg_target(1, 0).orR && io.ctl.pc_sel_no_xept === PC_JR)
   tval_inst_ma := MuxCase(0.U, Array(
                     (io.ctl.pc_sel_no_xept === PC_BR) -> br_target,
                     (io.ctl.pc_sel_no_xept === PC_J)  -> jmp_target,
                     (io.ctl.pc_sel_no_xept === PC_JR) -> jump_reg_target
                     ))

   // Decode
   val rs1_addr = inst(RS1_MSB, RS1_LSB)
   val rs2_addr = inst(RS2_MSB, RS2_LSB)
   val wb_addr  = inst(RD_MSB,  RD_LSB)

   val wb_data = Wire(UInt(conf.xprlen.W))
   val wb_wen = io.ctl.rf_wen && !io.ctl.exception && !interrupt_edge

   // Register File
   val regfile = Mem(32, UInt(conf.xprlen.W))

   when (wb_wen && (wb_addr =/= 0.U))
   {
      regfile(wb_addr) := wb_data
   }

   //// DebugModule
   io.ddpath.rdata := regfile(io.ddpath.addr)
   when(io.ddpath.validreq){
      regfile(io.ddpath.addr) := io.ddpath.wdata
   }
   ///

   val rs1_data = Mux((rs1_addr =/= 0.U), regfile(rs1_addr), 0.asUInt(conf.xprlen.W))
   val rs2_data = Mux((rs2_addr =/= 0.U), regfile(rs2_addr), 0.asUInt(conf.xprlen.W))


   // immediates
   val imm_i = inst(31, 20)
   val imm_s = Cat(inst(31, 25), inst(11,7))
   val imm_b = Cat(inst(31), inst(7), inst(30,25), inst(11,8))
   val imm_u = inst(31, 12)
   val imm_j = Cat(inst(31), inst(19,12), inst(20), inst(30,21))
   val imm_z = Cat(Fill(27,0.U), inst(19,15))

   // sign-extend immediates
   val imm_i_sext = Cat(Fill(20,imm_i(11)), imm_i)
   val imm_s_sext = Cat(Fill(20,imm_s(11)), imm_s)
   val imm_b_sext = Cat(Fill(19,imm_b(11)), imm_b, 0.U)
   val imm_u_sext = Cat(imm_u, Fill(12,0.U))
   val imm_j_sext = Cat(Fill(11,imm_j(19)), imm_j, 0.U)


   val alu_op1 = MuxCase(0.U, Array(
               (io.ctl.op1_sel === OP1_RS1) -> rs1_data,
               (io.ctl.op1_sel === OP1_IMU) -> imm_u_sext,
               (io.ctl.op1_sel === OP1_IMZ) -> imm_z
               )).asUInt()

   val alu_op2 = MuxCase(0.U, Array(
               (io.ctl.op2_sel === OP2_RS2) -> rs2_data,
               (io.ctl.op2_sel === OP2_PC)  -> pc_reg,
               (io.ctl.op2_sel === OP2_IMI) -> imm_i_sext,
               (io.ctl.op2_sel === OP2_IMS) -> imm_s_sext
               )).asUInt()



   // ALU
   val alu_out   = Wire(UInt(conf.xprlen.W))

   val alu_shamt = alu_op2(4,0).asUInt()

   alu_out := MuxCase(0.U, Array(
                  (io.ctl.alu_fun === ALU_ADD)  -> (alu_op1 + alu_op2).asUInt(),
                  (io.ctl.alu_fun === ALU_SUB)  -> (alu_op1 - alu_op2).asUInt(),
                  (io.ctl.alu_fun === ALU_AND)  -> (alu_op1 & alu_op2).asUInt(),
                  (io.ctl.alu_fun === ALU_OR)   -> (alu_op1 | alu_op2).asUInt(),
                  (io.ctl.alu_fun === ALU_XOR)  -> (alu_op1 ^ alu_op2).asUInt(),
                  (io.ctl.alu_fun === ALU_SLT)  -> (alu_op1.asSInt() < alu_op2.asSInt()).asUInt(),
                  (io.ctl.alu_fun === ALU_SLTU) -> (alu_op1 < alu_op2).asUInt(),
                  (io.ctl.alu_fun === ALU_SLL)  -> ((alu_op1 << alu_shamt)(conf.xprlen-1, 0)).asUInt(),
                  (io.ctl.alu_fun === ALU_SRA)  -> (alu_op1.asSInt() >> alu_shamt).asUInt(),
                  (io.ctl.alu_fun === ALU_SRL)  -> (alu_op1 >> alu_shamt).asUInt(),
                  (io.ctl.alu_fun === ALU_COPY1)-> alu_op1
                  ))

   // Branch/Jump Target Calculation
   br_target       := pc_reg + imm_b_sext
   jmp_target      := pc_reg + imm_j_sext
   jump_reg_target := (rs1_data.asUInt() + imm_i_sext.asUInt()) & ~1.U(conf.xprlen.W)

   // Control Status Registers
   val csr = Module(new CSRFile(perfEventSets=CSREvents.events))
   csr.io := DontCare
   csr.io.decode(0).inst := inst
   csr.io.rw.addr   := inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
   csr.io.rw.cmd    := io.ctl.csr_cmd
   csr.io.rw.wdata  := alu_out

   csr.io.retire    := !(io.ctl.stall || io.ctl.exception)
   csr.io.exception := io.ctl.exception
   csr.io.pc        := pc_reg
   exception_target := csr.io.evec

   csr.io.tval := MuxCase(0.U, Array(
                  (io.ctl.exception_cause === Causes.illegal_instruction.U)     -> inst,
                  (io.ctl.exception_cause === Causes.misaligned_fetch.U)  -> tval_inst_ma,
                  (io.ctl.exception_cause === Causes.misaligned_store.U) -> tval_data_ma,
                  (io.ctl.exception_cause === Causes.misaligned_load.U)  -> tval_data_ma,
                  ))

   // Interrupt rising edge detector (output trap signal for one cycle on rising edge)
   val reg_interrupt_edge = RegInit(false.B)
   when (!io.ctl.stall) {
      reg_interrupt_edge := csr.io.interrupt
   }
   interrupt_edge := csr.io.interrupt && !reg_interrupt_edge

   io.dat.csr_eret := csr.io.eret

   csr.io.interrupts := io.interrupt
   csr.io.hartid := io.hartid
   io.dat.csr_interrupt := interrupt_edge
   csr.io.cause := Mux(io.ctl.exception, io.ctl.exception_cause, csr.io.interrupt_cause)
   csr.io.ungated_clock := clock

   // Add your own uarch counters here!
   csr.io.counters.foreach(_.inc := false.B)

   // WB Mux
   wb_data := MuxCase(alu_out, Array(
                  (io.ctl.wb_sel === WB_ALU) -> alu_out,
                  (io.ctl.wb_sel === WB_MEM) -> io.dmem.resp.bits.data,
                  (io.ctl.wb_sel === WB_PC4) -> pc_plus4,
                  (io.ctl.wb_sel === WB_CSR) -> csr.io.rw.rdata
                  ))


   // datapath to controlpath outputs
   io.dat.inst   := inst
   io.dat.br_eq  := (rs1_data === rs2_data)
   io.dat.br_lt  := (rs1_data.asSInt() < rs2_data.asSInt())
   io.dat.br_ltu := (rs1_data.asUInt() < rs2_data.asUInt())


   // datapath to data memory outputs
   io.dmem.req.bits.addr := alu_out
   io.dmem.req.bits.data := rs2_data.asUInt()

   io.dat.mem_address_low := alu_out(2, 0)
   tval_data_ma := alu_out

   // Printout
   // pass output through the spike-dasm binary (found in riscv-tools) to turn
   // the DASM(%x) into a disassembly string.
   printf("Cyc= %d [%d] pc=[%x] W[r%d=%x][%d] Op1=[r%d][%x] Op2=[r%d][%x] inst=[%x] %c%c%c DASM(%x)\n",
      csr.io.time(31,0),
      csr.io.retire,
      pc_reg,
      wb_addr,
      wb_data,
      wb_wen,
      rs1_addr,
      alu_op1,
      rs2_addr,
      alu_op2,
      inst,
      Mux(io.ctl.stall, Str("S"), Str(" ")),
      MuxLookup(io.ctl.pc_sel, Str("?"), Seq(
         PC_BR -> Str("B"),
         PC_J -> Str("J"),
         PC_JR -> Str("R"),
         PC_EXC -> Str("E"),
         PC_4 -> Str(" "))),
      Mux(csr.io.exception, Str("X"), Str(" ")),
      inst)


   if (PRINT_COMMIT_LOG)
   {
      when (!io.ctl.stall)
      {
         // use "sed" to parse out "@@@" from the other printf code above.
         val rd = inst(RD_MSB,RD_LSB)
         when (io.ctl.rf_wen && rd =/= 0.U)
         {
            printf("@@@ 0x%x (0x%x) x%d 0x%x\n", pc_reg, inst, rd, Cat(Fill(32,wb_data(31)),wb_data))
         }
         .otherwise
         {
            printf("@@@ 0x%x (0x%x)\n", pc_reg, inst)
         }
      }
   }
}
