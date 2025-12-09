//**************************************************************************
// RISCV Processor Instruction Fetch Stage
//--------------------------------------------------------------------------
// 
// Priyansh Saha
// 2025 Dec 7

package sodor.stage5

import chisel3._
import chisel3.util._

import sodor.stage5.Constants._
import sodor.common._

class InstructionFetchStageIo(implicit val conf: SodorCoreParams) extends Bundle
{
   // Control inputs
   val reset_vector = Input(UInt(conf.xprlen.W))
   val pc_sel = Input(UInt(2.W))
   val if_kill = Input(Bool())
   val pipeline_kill = Input(Bool())
   val dec_stall = Input(Bool())
   val full_stall = Input(Bool())
   val fencei = Input(Bool())
   
   // Target addresses from execute stage
   val brjmp_target = Input(UInt(conf.xprlen.W))
   val jalr_target = Input(UInt(conf.xprlen.W))
   val exc_target = Input(UInt(conf.xprlen.W))
   
   // Instruction memory interface
   val imem = new MemPortIo(conf.xprlen)
   
   // Outputs to decode stage
   val dec_inst = Output(UInt(conf.xprlen.W))
   val dec_pc = Output(UInt(conf.xprlen.W))
   val dec_valid = Output(Bool())
}

class InstructionFetchStage(implicit val conf: SodorCoreParams) extends Module
{
   val io = IO(new InstructionFetchStageIo())
   
   //**********************************
   // Pipeline State Registers
   
   // Instruction Fetch State
   val if_reg_pc = RegInit(io.reset_vector)
   
   //**********************************
   // Instruction Fetch Stage
   val if_pc_next = Wire(UInt(32.W))
   
   // Instruction fetch buffer
   val if_buffer_in = Wire(new DecoupledIO(new MemResp(conf.xprlen)))
   if_buffer_in.bits := io.imem.resp.bits
   if_buffer_in.valid := io.imem.resp.valid
   assert(!(if_buffer_in.valid && !if_buffer_in.ready), "Instruction backlog")
   
   val if_buffer_out = Queue(if_buffer_in, entries = 1, pipe = false, flow = true)
   if_buffer_out.ready := !io.dec_stall && !io.full_stall
   
   // Instruction PC buffer
   val if_pc_buffer_in = Wire(new DecoupledIO(UInt(conf.xprlen.W)))
   if_pc_buffer_in.bits := if_reg_pc
   if_pc_buffer_in.valid := if_buffer_in.valid
   
   val if_pc_buffer_out = Queue(if_pc_buffer_in, entries = 1, pipe = false, flow = true)
   if_pc_buffer_out.ready := if_buffer_out.ready
   
   // Instruction fetch kill flag buffer
   val if_reg_killed = RegInit(false.B)
   when ((io.pipeline_kill || io.if_kill) && !if_buffer_out.fire)
   {
      if_reg_killed := true.B
   }
   when (if_reg_killed && if_buffer_out.fire)
   {
      if_reg_killed := false.B
   }
   
   // Do not change the PC again if the instruction is killed in previous cycles (when the PC has changed)
   when ((if_buffer_in.fire && !if_reg_killed) || io.if_kill || io.pipeline_kill)
   {
      if_reg_pc := if_pc_next
   }
   
   val if_pc_plus4 = (if_reg_pc + 4.asUInt(conf.xprlen.W))
   
   if_pc_next := Mux(io.pc_sel === PC_4,      if_pc_plus4,
                 Mux(io.pc_sel === PC_BRJMP,  io.brjmp_target,
                 Mux(io.pc_sel === PC_JALR,   io.jalr_target,
                 /*Mux(io.pc_sel === PC_EXC*/ io.exc_target)))
   
   // for a fencei, refetch the if_pc (assuming no stall, no branch, and no exception)
   when (io.fencei && io.pc_sel === PC_4 &&
         !io.dec_stall && !io.full_stall && !io.pipeline_kill)
   {
      if_pc_next := if_reg_pc
   }
   
   // Instruction Memory
   io.imem.req.valid := if_buffer_in.ready
   io.imem.req.bits.fcn := M_XRD
   io.imem.req.bits.typ := MT_WU
   io.imem.req.bits.addr := if_reg_pc
   io.imem.req.bits.data := 0.U
   
   // Outputs to decode stage
   io.dec_valid := Mux(io.pipeline_kill, false.B,
                   Mux(io.if_kill || if_reg_killed, false.B,
                   if_buffer_out.valid))
   
   io.dec_inst := Mux(io.pipeline_kill, BUBBLE,
                  Mux(io.if_kill || if_reg_killed, BUBBLE,
                  Mux(if_buffer_out.valid, if_buffer_out.bits.data, BUBBLE)))
   
   io.dec_pc := if_pc_buffer_out.bits
}
