//**************************************************************************
// RISCV Processor 5-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2012 Jan 13
//
// TODO refactor stall, kill, fencei, flush signals. They're more confusing than they need to be.

package sodor.stage5

import chisel3._
import chisel3.util._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket.{CSR, CSRFile, Causes}
import freechips.rocketchip.tile.CoreInterrupts

import Constants._
import sodor.common._

class DatToCtlIo(implicit val conf: SodorCoreParams) extends Bundle()
{
   val dec_inst    = Output(UInt(conf.xprlen.W))
   val dec_valid   = Output(Bool())
   val exe_br_eq   = Output(Bool())
   val exe_br_lt   = Output(Bool())
   val exe_br_ltu  = Output(Bool())
   val exe_br_type = Output(UInt(4.W))
   val exe_inst_misaligned = Output(Bool())

   val mem_ctrl_dmem_val = Output(Bool())
   val mem_data_misaligned = Output(Bool())
   val mem_store = Output(Bool())

   val csr_eret = Output(Bool())
   val csr_interrupt = Output(Bool())
}

class DpathIo(implicit val p: Parameters, val conf: SodorCoreParams) extends Bundle
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

   //**********************************
   // Exception handling values (all read during mem_stage)
   val mem_tval_data_ma = Wire(UInt(conf.xprlen.W))
   val mem_tval_inst_ma = Wire(UInt(conf.xprlen.W))

   //**********************************

   //**********************************
   // Pipeline State Registers

   // Instruction Fetch State
   val if_reg_pc             = RegInit(io.reset_vector)

   // Instruction Decode State
   val dec_reg_valid         = RegInit(false.B)
   val dec_reg_inst          = RegInit(BUBBLE)
   val dec_reg_pc            = RegInit(0.asUInt(conf.xprlen.W))

   // Execute State
   val exe_reg_valid         = RegInit(false.B)
   val exe_reg_inst          = RegInit(BUBBLE)
   val exe_reg_pc            = RegInit(0.asUInt(conf.xprlen.W))
   val exe_reg_wbaddr        = Reg(UInt(5.W))
   val exe_reg_rs1_addr      = Reg(UInt(5.W))
   val exe_reg_rs2_addr      = Reg(UInt(5.W))
   val exe_reg_op1_data      = Reg(UInt(conf.xprlen.W))
   val exe_reg_op2_data      = Reg(UInt(conf.xprlen.W))
   val exe_reg_rs2_data      = Reg(UInt(conf.xprlen.W))
   val exe_reg_ctrl_br_type  = RegInit(BR_N)
   val exe_reg_ctrl_op2_sel  = Reg(UInt())
   val exe_reg_ctrl_alu_fun  = Reg(UInt())
   val exe_reg_ctrl_wb_sel   = Reg(UInt())
   val exe_reg_ctrl_rf_wen   = RegInit(false.B)
   val exe_reg_ctrl_mem_val  = RegInit(false.B)
   val exe_reg_ctrl_mem_fcn  = RegInit(M_X)
   val exe_reg_ctrl_mem_typ  = RegInit(MT_X)
   val exe_reg_ctrl_csr_cmd  = RegInit(CSR.N)

   // Memory State
   val mem_reg_valid         = RegInit(false.B)
   val mem_reg_pc            = Reg(UInt(conf.xprlen.W))
   val mem_reg_inst          = Reg(UInt(conf.xprlen.W))
   val mem_reg_alu_out       = Reg(Bits())
   val mem_reg_wbaddr        = Reg(UInt())
   val mem_reg_rs1_addr      = Reg(UInt())
   val mem_reg_rs2_addr      = Reg(UInt())
   val mem_reg_op1_data      = Reg(UInt(conf.xprlen.W))
   val mem_reg_op2_data      = Reg(UInt(conf.xprlen.W))
   val mem_reg_rs2_data      = Reg(UInt(conf.xprlen.W))
   val mem_reg_ctrl_rf_wen   = RegInit(false.B)
   val mem_reg_ctrl_mem_val  = RegInit(false.B)
   val mem_reg_ctrl_mem_fcn  = RegInit(M_X)
   val mem_reg_ctrl_mem_typ  = RegInit(MT_X)
   val mem_reg_ctrl_wb_sel   = Reg(UInt())
   val mem_reg_ctrl_csr_cmd  = RegInit(CSR.N)

   // Writeback State
   val wb_reg_valid          = RegInit(false.B)
   val wb_reg_wbaddr         = Reg(UInt())
   val wb_reg_wbdata         = Reg(UInt(conf.xprlen.W))
   val wb_reg_ctrl_rf_wen    = RegInit(false.B)


   //**********************************
   // Instruction Fetch Stage
   val if_pc_next          = Wire(UInt(32.W))
   val exe_brjmp_target    = Wire(UInt(32.W))
   val exe_jump_reg_target = Wire(UInt(32.W))
   val exception_target    = Wire(UInt(32.W))

   // Instruction fetch buffer
   val if_buffer_in = Wire(new DecoupledIO(new MemResp(conf.xprlen)))
   if_buffer_in.bits := io.imem.resp.bits
   if_buffer_in.valid := io.imem.resp.valid
   assert(!(if_buffer_in.valid && !if_buffer_in.ready), "Instruction backlog")

   val if_buffer_out = Queue(if_buffer_in, entries = 1, pipe = false, flow = true)
   if_buffer_out.ready := !io.ctl.dec_stall && !io.ctl.full_stall

   // Instruction PC buffer
   val if_pc_buffer_in = Wire(new DecoupledIO(UInt(conf.xprlen.W)))
   if_pc_buffer_in.bits := if_reg_pc
   if_pc_buffer_in.valid := if_buffer_in.valid

   val if_pc_buffer_out = Queue(if_pc_buffer_in, entries = 1, pipe = false, flow = true)
   if_pc_buffer_out.ready := if_buffer_out.ready

   // Instruction fetch kill flag buffer
   val if_reg_killed = RegInit(false.B)
   when ((io.ctl.pipeline_kill || io.ctl.if_kill) && !if_buffer_out.fire)
   {
      if_reg_killed := true.B
   }
   when (if_reg_killed && if_buffer_out.fire)
   {
      if_reg_killed := false.B
   }

   // Do not change the PC again if the instruction is killed in previous cycles (when the PC has changed)
   when ((if_buffer_in.fire && !if_reg_killed) || io.ctl.if_kill || io.ctl.pipeline_kill)
   {
      if_reg_pc := if_pc_next
   }

   val if_pc_plus4 = (if_reg_pc + 4.asUInt(conf.xprlen.W))

   if_pc_next := Mux(io.ctl.exe_pc_sel === PC_4,      if_pc_plus4,
                 Mux(io.ctl.exe_pc_sel === PC_BRJMP,  exe_brjmp_target,
                 Mux(io.ctl.exe_pc_sel === PC_JALR,   exe_jump_reg_target,
                 /*Mux(io.ctl.exe_pc_sel === PC_EXC*/ exception_target)))

   // for a fencei, refetch the if_pc (assuming no stall, no branch, and no exception)
   when (io.ctl.fencei && io.ctl.exe_pc_sel === PC_4 &&
         !io.ctl.dec_stall && !io.ctl.full_stall && !io.ctl.pipeline_kill)
   {
      if_pc_next := if_reg_pc
   }

   // Instruction Memory
   io.imem.req.valid := if_buffer_in.ready
   io.imem.req.bits.fcn := M_XRD
   io.imem.req.bits.typ := MT_WU
   io.imem.req.bits.addr := if_reg_pc

   when (io.ctl.pipeline_kill)
   {
      dec_reg_valid := false.B
      dec_reg_inst := BUBBLE
   }
   .elsewhen (!io.ctl.dec_stall && !io.ctl.full_stall)
   {
      when (io.ctl.if_kill || if_reg_killed)
      {
         dec_reg_valid := false.B
         dec_reg_inst := BUBBLE
      }
      .elsewhen (if_buffer_out.valid)
      {
         dec_reg_valid := true.B
         dec_reg_inst := if_buffer_out.bits.data
      }
      .otherwise
      {
         dec_reg_valid := false.B
         dec_reg_inst := BUBBLE
      }

      dec_reg_pc := if_pc_buffer_out.bits
   }


   //**********************************
   // Decode Stage
   val dec_rs1_addr = dec_reg_inst(19, 15)
   val dec_rs2_addr = dec_reg_inst(24, 20)
   val dec_wbaddr   = dec_reg_inst(11, 7)


   // Register File
   val regfile = Module(new RegisterFile())
   regfile.io.rs1_addr := dec_rs1_addr
   regfile.io.rs2_addr := dec_rs2_addr
   val rf_rs1_data = regfile.io.rs1_data
   val rf_rs2_data = regfile.io.rs2_data
   regfile.io.waddr := wb_reg_wbaddr
   regfile.io.wdata := wb_reg_wbdata
   regfile.io.wen   := wb_reg_ctrl_rf_wen

   //// DebugModule
   regfile.io.dm_addr := io.ddpath.addr
   io.ddpath.rdata := regfile.io.dm_rdata
   regfile.io.dm_en := io.ddpath.validreq
   regfile.io.dm_wdata := io.ddpath.wdata
   ///

   // immediates
   val imm_itype  = dec_reg_inst(31,20)
   val imm_stype  = Cat(dec_reg_inst(31,25), dec_reg_inst(11,7))
   val imm_sbtype = Cat(dec_reg_inst(31), dec_reg_inst(7), dec_reg_inst(30, 25), dec_reg_inst(11,8))
   val imm_utype  = dec_reg_inst(31, 12)
   val imm_ujtype = Cat(dec_reg_inst(31), dec_reg_inst(19,12), dec_reg_inst(20), dec_reg_inst(30,21))

   val imm_z = Cat(Fill(27,0.U), dec_reg_inst(19,15))

   // sign-extend immediates
   val imm_itype_sext  = Cat(Fill(20,imm_itype(11)), imm_itype)
   val imm_stype_sext  = Cat(Fill(20,imm_stype(11)), imm_stype)
   val imm_sbtype_sext = Cat(Fill(19,imm_sbtype(11)), imm_sbtype, 0.U)
   val imm_utype_sext  = Cat(imm_utype, Fill(12,0.U))
   val imm_ujtype_sext = Cat(Fill(11,imm_ujtype(19)), imm_ujtype, 0.U)

   // Operand 2 Mux
   val dec_alu_op2 = MuxCase(0.U, Array(
               (io.ctl.op2_sel === OP2_RS2)    -> rf_rs2_data,
               (io.ctl.op2_sel === OP2_ITYPE)  -> imm_itype_sext,
               (io.ctl.op2_sel === OP2_STYPE)  -> imm_stype_sext,
               (io.ctl.op2_sel === OP2_SBTYPE) -> imm_sbtype_sext,
               (io.ctl.op2_sel === OP2_UTYPE)  -> imm_utype_sext,
               (io.ctl.op2_sel === OP2_UJTYPE) -> imm_ujtype_sext
               )).asUInt()



   // Bypass Muxes
   val exe_alu_out  = Wire(UInt(conf.xprlen.W))
   val mem_wbdata   = Wire(UInt(conf.xprlen.W))

   val dec_op1_data = Wire(UInt(conf.xprlen.W))
   val dec_op2_data = Wire(UInt(conf.xprlen.W))
   val dec_rs2_data = Wire(UInt(conf.xprlen.W))

   if (USE_FULL_BYPASSING)
   {
      // roll the OP1 mux into the bypass mux logic
      dec_op1_data := MuxCase(rf_rs1_data, Array(
                           ((io.ctl.op1_sel === OP1_IMZ)) -> imm_z,
                           ((io.ctl.op1_sel === OP1_PC)) -> dec_reg_pc,
                           ((exe_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr =/= 0.U) && exe_reg_ctrl_rf_wen) -> exe_alu_out,
                           ((mem_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr =/= 0.U) && mem_reg_ctrl_rf_wen) -> mem_wbdata,
                           ((wb_reg_wbaddr  === dec_rs1_addr) && (dec_rs1_addr =/= 0.U) &&  wb_reg_ctrl_rf_wen) -> wb_reg_wbdata
                           ))

      dec_op2_data := MuxCase(dec_alu_op2, Array(
                           ((exe_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && exe_reg_ctrl_rf_wen && (io.ctl.op2_sel === OP2_RS2)) -> exe_alu_out,
                           ((mem_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && mem_reg_ctrl_rf_wen && (io.ctl.op2_sel === OP2_RS2)) -> mem_wbdata,
                           ((wb_reg_wbaddr  === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) &&  wb_reg_ctrl_rf_wen && (io.ctl.op2_sel === OP2_RS2)) -> wb_reg_wbdata
                           ))

      dec_rs2_data := MuxCase(rf_rs2_data, Array(
                           ((exe_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && exe_reg_ctrl_rf_wen) -> exe_alu_out,
                           ((mem_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && mem_reg_ctrl_rf_wen) -> mem_wbdata,
                           ((wb_reg_wbaddr  === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) &&  wb_reg_ctrl_rf_wen) -> wb_reg_wbdata
                           ))
   }
   else
   {
      // Rely only on control interlocking to resolve hazards
      dec_op1_data := MuxCase(rf_rs1_data, Array(
                          ((io.ctl.op1_sel === OP1_IMZ)) -> imm_z,
                          ((io.ctl.op1_sel === OP1_PC))  -> dec_reg_pc
                          ))
      dec_rs2_data := rf_rs2_data
      dec_op2_data := dec_alu_op2
   }


   when ((io.ctl.dec_stall && !io.ctl.full_stall) || io.ctl.pipeline_kill)
   {
      // (kill exe stage)
      // insert NOP (bubble) into Execute stage on front-end stall (e.g., hazard clearing)
      exe_reg_valid         := false.B
      exe_reg_inst          := BUBBLE
      exe_reg_wbaddr        := 0.U
      exe_reg_ctrl_rf_wen   := false.B
      exe_reg_ctrl_mem_val  := false.B
      exe_reg_ctrl_mem_fcn  := M_X
      exe_reg_ctrl_csr_cmd  := CSR.N
      exe_reg_ctrl_br_type  := BR_N
   }
   .elsewhen(!io.ctl.dec_stall && !io.ctl.full_stall)
   {
      // no stalling...
      exe_reg_pc            := dec_reg_pc
      exe_reg_rs1_addr      := dec_rs1_addr
      exe_reg_rs2_addr      := dec_rs2_addr
      exe_reg_op1_data      := dec_op1_data
      exe_reg_op2_data      := dec_op2_data
      exe_reg_rs2_data      := dec_rs2_data
      exe_reg_ctrl_op2_sel  := io.ctl.op2_sel
      exe_reg_ctrl_alu_fun  := io.ctl.alu_fun
      exe_reg_ctrl_wb_sel   := io.ctl.wb_sel

      when (io.ctl.dec_kill)
      {
         exe_reg_valid         := false.B
         exe_reg_inst          := BUBBLE
         exe_reg_wbaddr        := 0.U
         exe_reg_ctrl_rf_wen   := false.B
         exe_reg_ctrl_mem_val  := false.B
         exe_reg_ctrl_mem_fcn  := M_X
         exe_reg_ctrl_csr_cmd  := CSR.N
         exe_reg_ctrl_br_type  := BR_N
      }
      .otherwise
      {
         exe_reg_valid         := dec_reg_valid
         exe_reg_inst          := dec_reg_inst
         exe_reg_wbaddr        := dec_wbaddr
         exe_reg_ctrl_rf_wen   := io.ctl.rf_wen
         exe_reg_ctrl_mem_val  := io.ctl.mem_val
         exe_reg_ctrl_mem_fcn  := io.ctl.mem_fcn
         exe_reg_ctrl_mem_typ  := io.ctl.mem_typ
         exe_reg_ctrl_csr_cmd  := io.ctl.csr_cmd
         exe_reg_ctrl_br_type  := io.ctl.br_type
      }
   }

   //**********************************
   // Execute Stage

   val exe_alu_op1 = exe_reg_op1_data.asUInt()
   val exe_alu_op2 = exe_reg_op2_data.asUInt()

   // ALU
   val alu_shamt     = exe_alu_op2(4,0).asUInt()
   val exe_adder_out = (exe_alu_op1 + exe_alu_op2)(conf.xprlen-1,0)

   //only for debug purposes right now until debug() works
   exe_alu_out := MuxCase(exe_reg_inst.asUInt(), Array(
                  (exe_reg_ctrl_alu_fun === ALU_ADD)  -> exe_adder_out,
                  (exe_reg_ctrl_alu_fun === ALU_SUB)  -> (exe_alu_op1 - exe_alu_op2).asUInt(),
                  (exe_reg_ctrl_alu_fun === ALU_AND)  -> (exe_alu_op1 & exe_alu_op2).asUInt(),
                  (exe_reg_ctrl_alu_fun === ALU_OR)   -> (exe_alu_op1 | exe_alu_op2).asUInt(),
                  (exe_reg_ctrl_alu_fun === ALU_XOR)  -> (exe_alu_op1 ^ exe_alu_op2).asUInt(),
                  (exe_reg_ctrl_alu_fun === ALU_SLT)  -> (exe_alu_op1.asSInt() < exe_alu_op2.asSInt()).asUInt(),
                  (exe_reg_ctrl_alu_fun === ALU_SLTU) -> (exe_alu_op1 < exe_alu_op2).asUInt(),
                  (exe_reg_ctrl_alu_fun === ALU_SLL)  -> ((exe_alu_op1 << alu_shamt)(conf.xprlen-1, 0)).asUInt(),
                  (exe_reg_ctrl_alu_fun === ALU_SRA)  -> (exe_alu_op1.asSInt() >> alu_shamt).asUInt(),
                  (exe_reg_ctrl_alu_fun === ALU_SRL)  -> (exe_alu_op1 >> alu_shamt).asUInt(),
                  (exe_reg_ctrl_alu_fun === ALU_COPY_1)-> exe_alu_op1,
                  (exe_reg_ctrl_alu_fun === ALU_COPY_2)-> exe_alu_op2
                  ))

   // Branch/Jump Target Calculation
   val brjmp_offset    = exe_reg_op2_data
   exe_brjmp_target    := exe_reg_pc + brjmp_offset
   exe_jump_reg_target := exe_adder_out & ~1.U(conf.xprlen.W)

   // Instruction misalign detection
   // In control path, instruction misalignment exception is always raised in the next cycle once the misaligned instruction reaches
   // execution stage, regardless whether the pipeline stalls or not
   io.dat.exe_inst_misaligned := (exe_brjmp_target(1, 0).orR    && io.ctl.exe_pc_sel === PC_BRJMP) ||
                                 (exe_jump_reg_target(1, 0).orR && io.ctl.exe_pc_sel === PC_JALR)
   mem_tval_inst_ma := RegNext(Mux(io.ctl.exe_pc_sel === PC_BRJMP, exe_brjmp_target, exe_jump_reg_target))

   val exe_pc_plus4    = (exe_reg_pc + 4.U)(conf.xprlen-1,0)

   when (io.ctl.pipeline_kill)
   {
      mem_reg_valid         := false.B
      mem_reg_inst          := BUBBLE
      mem_reg_ctrl_rf_wen   := false.B
      mem_reg_ctrl_mem_val  := false.B
      mem_reg_ctrl_csr_cmd  := false.B
   }
   .elsewhen (!io.ctl.full_stall)
   {
      mem_reg_valid         := exe_reg_valid
      mem_reg_pc            := exe_reg_pc
      mem_reg_inst          := exe_reg_inst
      mem_reg_alu_out       := Mux((exe_reg_ctrl_wb_sel === WB_PC4), exe_pc_plus4, exe_alu_out)
      mem_reg_wbaddr        := exe_reg_wbaddr
      mem_reg_rs1_addr      := exe_reg_rs1_addr
      mem_reg_rs2_addr      := exe_reg_rs2_addr
      mem_reg_op1_data      := exe_reg_op1_data
      mem_reg_op2_data      := exe_reg_op2_data
      mem_reg_rs2_data      := exe_reg_rs2_data
      mem_reg_ctrl_rf_wen   := exe_reg_ctrl_rf_wen
      mem_reg_ctrl_mem_val  := exe_reg_ctrl_mem_val
      mem_reg_ctrl_mem_fcn  := exe_reg_ctrl_mem_fcn
      mem_reg_ctrl_mem_typ  := exe_reg_ctrl_mem_typ
      mem_reg_ctrl_wb_sel   := exe_reg_ctrl_wb_sel
      mem_reg_ctrl_csr_cmd  := exe_reg_ctrl_csr_cmd
   }

   //**********************************
   // Memory Stage

   // Control Status Registers
   // The CSRFile can redirect the PC so it's easiest to put this in Execute for now.
   val csr = Module(new CSRFile(perfEventSets=CSREvents.events))
   csr.io := DontCare
   csr.io.decode(0).inst := mem_reg_inst
   csr.io.rw.addr   := mem_reg_inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
   csr.io.rw.wdata  := mem_reg_alu_out
   csr.io.rw.cmd    := mem_reg_ctrl_csr_cmd

   csr.io.retire    := wb_reg_valid
   csr.io.exception := io.ctl.mem_exception
   csr.io.pc        := mem_reg_pc
   exception_target := csr.io.evec

   csr.io.tval := MuxCase(0.U, Array(
                  (io.ctl.mem_exception_cause === Causes.illegal_instruction.U) -> RegNext(exe_reg_inst),
                  (io.ctl.mem_exception_cause === Causes.misaligned_fetch.U)    -> mem_tval_inst_ma,
                  (io.ctl.mem_exception_cause === Causes.misaligned_store.U)    -> mem_tval_data_ma,
                  (io.ctl.mem_exception_cause === Causes.misaligned_load.U)     -> mem_tval_data_ma,
                  ))

   // Interrupt rising edge detector (output trap signal for one cycle on rising edge)
   val reg_interrupt_handled = RegNext(csr.io.interrupt, false.B)
   val interrupt_edge = csr.io.interrupt && !reg_interrupt_handled

   csr.io.interrupts := io.interrupt
   csr.io.hartid := io.hartid
   io.dat.csr_interrupt := interrupt_edge
   csr.io.cause := Mux(io.ctl.mem_exception, io.ctl.mem_exception_cause, csr.io.interrupt_cause)
   csr.io.ungated_clock := clock

   io.dat.csr_eret := csr.io.eret
   // TODO replay? stall?

   // Add your own uarch counters here!
   csr.io.counters.foreach(_.inc := false.B)


   // Data misalignment detection
   // For example, if type is 3 (word), the mask is ~(0b111 << (3 - 1)) = ~0b100 = 0b011.
   val misaligned_mask = Wire(UInt(3.W))
   misaligned_mask := ~(7.U(3.W) << (mem_reg_ctrl_mem_typ - 1.U)(1, 0))
   io.dat.mem_data_misaligned := (misaligned_mask & mem_reg_alu_out.asUInt.apply(2, 0)).orR && mem_reg_ctrl_mem_val
   io.dat.mem_store := mem_reg_ctrl_mem_fcn === M_XWR
   mem_tval_data_ma := mem_reg_alu_out.asUInt

   // WB Mux
   mem_wbdata := MuxCase(mem_reg_alu_out, Array(
                  (mem_reg_ctrl_wb_sel === WB_ALU) -> mem_reg_alu_out,
                  (mem_reg_ctrl_wb_sel === WB_PC4) -> mem_reg_alu_out,
                  (mem_reg_ctrl_wb_sel === WB_MEM) -> io.dmem.resp.bits.data,
                  (mem_reg_ctrl_wb_sel === WB_CSR) -> csr.io.rw.rdata
                  ))


   //**********************************
   // Writeback Stage

   when (!io.ctl.full_stall)
   {
      wb_reg_valid         := mem_reg_valid && !io.ctl.mem_exception && !interrupt_edge
      wb_reg_wbaddr        := mem_reg_wbaddr
      wb_reg_wbdata        := mem_wbdata
      wb_reg_ctrl_rf_wen   := Mux(io.ctl.mem_exception || interrupt_edge, false.B, mem_reg_ctrl_rf_wen)
   }
   .otherwise
   {
      wb_reg_valid         := false.B
      wb_reg_ctrl_rf_wen   := false.B
   }



   //**********************************
   // External Signals

   // datapath to controlpath outputs
   io.dat.dec_valid  := dec_reg_valid
   io.dat.dec_inst   := dec_reg_inst
   io.dat.exe_br_eq  := (exe_reg_op1_data === exe_reg_rs2_data)
   io.dat.exe_br_lt  := (exe_reg_op1_data.asSInt() < exe_reg_rs2_data.asSInt())
   io.dat.exe_br_ltu := (exe_reg_op1_data.asUInt() < exe_reg_rs2_data.asUInt())
   io.dat.exe_br_type:= exe_reg_ctrl_br_type

   io.dat.mem_ctrl_dmem_val := mem_reg_ctrl_mem_val

   // datapath to data memory outputs
   io.dmem.req.valid     := mem_reg_ctrl_mem_val && !io.dat.mem_data_misaligned
   io.dmem.req.bits.addr := mem_reg_alu_out.asUInt()
   io.dmem.req.bits.fcn  := mem_reg_ctrl_mem_fcn
   io.dmem.req.bits.typ  := mem_reg_ctrl_mem_typ
   io.dmem.req.bits.data := mem_reg_rs2_data

   val wb_reg_inst = RegNext(mem_reg_inst)

   printf("Cyc= %d [%d] pc=[%x] W[r%d=%x][%d] Op1=[r%d][%x] Op2=[r%d][%x] inst=[%x] %c%c%c DASM(%x)\n",
      csr.io.time(31,0),
      csr.io.retire,
      RegNext(mem_reg_pc),
      wb_reg_wbaddr,
      wb_reg_wbdata,
      wb_reg_ctrl_rf_wen,
      RegNext(mem_reg_rs1_addr),
      RegNext(mem_reg_op1_data),
      RegNext(mem_reg_rs2_addr),
      RegNext(mem_reg_op2_data),
      wb_reg_inst,
      MuxCase(Str(" "), Seq(
         io.ctl.pipeline_kill -> Str("K"),
         io.ctl.full_stall -> Str("F"),
         io.ctl.dec_stall -> Str("S"))),
      MuxLookup(io.ctl.exe_pc_sel, Str("?"), Seq(
         PC_BRJMP -> Str("B"),
         PC_JALR -> Str("R"),
         PC_EXC -> Str("E"),
         PC_4 -> Str(" "))),
      Mux(csr.io.exception, Str("X"), Str(" ")),
      wb_reg_inst)
}
