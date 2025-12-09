//**************************************************************************
// RISCV Processor 5-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2012 Jan 13
// 
// Priyansh Saha
// 2025 Dec 9
//
// TODO refactor stall, kill, fencei, flush signals. They're more confusing than they need to be.

package sodor.stage5

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.rocket.{CSR, CSRFile, Causes}
import freechips.rocketchip.rocket.CoreInterrupts

import sodor.stage5.Constants._
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
   val interrupt = Input(new CoreInterrupts(false))
   val hartid = Input(UInt())
   val reset_vector = Input(UInt())
}

class DatPath(implicit val p: Parameters, val conf: SodorCoreParams) extends Module
{
   val io = IO(new DpathIo())
   io := DontCare

   // Pipeline State Registers
   val dec_reg_valid         = RegInit(false.B) // assert when instruction in decode stage is valid and requires write back to reg file

   //**********************************
   // Initialize all stages

   val fetch = Module(new InstructionFetchStage())
   val decode = Module(new InstructionDecodeStage())
   val execute = Module(new ExecuteStage())
   val memory = Module(new MemoryStage())
   val writeback = Module(new WritebackStage())

   //**********************************
   // Instruction Fetch Stage

   // IF inputs
   fetch.io.reset_vector := io.reset_vector
   
   fetch.io.pc_sel := io.ctl.exe_pc_sel
   fetch.io.if_kill := io.ctl.if_kill
   fetch.io.pipeline_kill := io.ctl.pipeline_kill
   fetch.io.dec_stall := io.ctl.dec_stall
   fetch.io.full_stall := io.ctl.full_stall
   fetch.io.fencei := io.ctl.fencei
   fetch.io.imem <> io.imem

   // Update decode stage valid flag from fetch stage
   when (io.ctl.pipeline_kill)
   {
      dec_reg_valid := false.B
   }
   .elsewhen (!io.ctl.dec_stall && !io.ctl.full_stall)
   {
      dec_reg_valid := fetch.io.dec_valid
   }

   //**********************************
   // Instruction Decode Stage
   
   // IF -> ID 
   decode.io.fetch_inst := fetch.io.dec_inst
   decode.io.fetch_pc := fetch.io.dec_pc
   decode.io.fetch_valid := fetch.io.dec_valid
   
   // Control inputs
   decode.io.op1_sel := io.ctl.op1_sel
   decode.io.op2_sel := io.ctl.op2_sel
   decode.io.dec_kill := io.ctl.dec_kill
   decode.io.dec_stall := io.ctl.dec_stall
   decode.io.full_stall := io.ctl.full_stall
   decode.io.pipeline_kill := io.ctl.pipeline_kill
   
   writeback.io.full_stall := io.ctl.full_stall
   
   // Bypass inputs from writeback stage
   decode.io.wb_wbaddr := writeback.io.wb_wbaddr
   decode.io.wb_wbdata := writeback.io.wb_wbdata
   decode.io.wb_ctrl_rf_wen := writeback.io.wb_ctrl_rf_wen
   
   // Register file write interface (from writeback stage)
   decode.io.rf_waddr := writeback.io.wb_wbaddr
   decode.io.rf_wdata := writeback.io.wb_wbdata
   decode.io.rf_wen := writeback.io.wb_ctrl_rf_wen
   
   // Debug interface
   decode.io.ddpath <> io.ddpath

   //**********************************
   // Execute Stage
   
   // ID -> EX
   execute.io.dec_rs1_addr := decode.io.exe_rs1_addr
   execute.io.dec_rs2_addr := decode.io.exe_rs2_addr
   execute.io.dec_wbaddr := decode.io.dec_wbaddr
   execute.io.dec_op1_data := decode.io.exe_op1_data
   execute.io.dec_op2_data := decode.io.exe_op2_data
   execute.io.dec_rs2_data := decode.io.exe_rs2_data
   execute.io.dec_pc := decode.io.exe_pc
   execute.io.dec_valid := dec_reg_valid
   execute.io.dec_inst := decode.io.dec_inst_reg
   
   // Control inputs
   execute.io.alu_fun := io.ctl.alu_fun
   execute.io.wb_sel := io.ctl.wb_sel
   execute.io.br_type := io.ctl.br_type
   execute.io.rf_wen := io.ctl.rf_wen
   execute.io.mem_val := io.ctl.mem_val
   execute.io.mem_fcn := io.ctl.mem_fcn
   execute.io.mem_typ := io.ctl.mem_typ
   execute.io.csr_cmd := io.ctl.csr_cmd
   execute.io.op2_sel := io.ctl.op2_sel
   
   execute.io.dec_kill := io.ctl.dec_kill
   execute.io.dec_stall := io.ctl.dec_stall
   execute.io.full_stall := io.ctl.full_stall
   execute.io.pipeline_kill := io.ctl.pipeline_kill
   execute.io.exe_pc_sel := io.ctl.exe_pc_sel
   
   // Connect branch/jump targets to fetch stage
   fetch.io.brjmp_target := execute.io.brjmp_target
   fetch.io.jalr_target := execute.io.jalr_target
   
   // Connect bypass outputs to decode stage (already wired in decode stage instantiation)
   decode.io.exe_wbaddr := execute.io.exe_wbaddr
   decode.io.exe_alu_out := execute.io.exe_alu_out
   decode.io.exe_ctrl_rf_wen := execute.io.exe_ctrl_rf_wen

   //**********************************
   // Memory Stage

   // EX -> MEM
   memory.io.exe_alu_out := execute.io.mem_alu_out
   memory.io.exe_pc := execute.io.mem_pc
   memory.io.exe_inst := execute.io.mem_inst
   memory.io.exe_wbaddr := execute.io.mem_wbaddr
   memory.io.exe_rs1_addr := execute.io.mem_rs1_addr
   memory.io.exe_rs2_addr := execute.io.mem_rs2_addr
   memory.io.exe_op1_data := execute.io.mem_op1_data
   memory.io.exe_op2_data := execute.io.mem_op2_data
   memory.io.exe_rs2_data := execute.io.mem_rs2_data
   memory.io.exe_valid := execute.io.mem_valid
   
   // Control inputs
   memory.io.ctrl_rf_wen := execute.io.mem_ctrl_rf_wen
   memory.io.ctrl_mem_val := execute.io.mem_ctrl_mem_val
   memory.io.ctrl_mem_fcn := execute.io.mem_ctrl_mem_fcn
   memory.io.ctrl_mem_typ := execute.io.mem_ctrl_mem_typ
   memory.io.ctrl_wb_sel := execute.io.mem_ctrl_wb_sel
   memory.io.ctrl_csr_cmd := execute.io.mem_ctrl_csr_cmd
   
   memory.io.pipeline_kill := io.ctl.pipeline_kill
   memory.io.full_stall := io.ctl.full_stall
   
   // Exception inputs
   memory.io.mem_exception := io.ctl.mem_exception
   memory.io.mem_exception_cause := io.ctl.mem_exception_cause
   memory.io.mem_tval_inst_ma := execute.io.mem_tval_inst_ma
   
   // Interrupt and system inputs
   memory.io.interrupt := io.interrupt
   memory.io.hartid := io.hartid
   
   // Writeback valid input
   memory.io.wb_valid := writeback.io.wb_valid
   
   // Data memory interface
   memory.io.dmem <> io.dmem
   
   // Connect bypass outputs to decode stage
   decode.io.mem_wbaddr := memory.io.mem_wbaddr
   decode.io.mem_wbdata := memory.io.mem_wbdata
   decode.io.mem_ctrl_rf_wen := memory.io.mem_ctrl_rf_wen
   
   // Connect exception target to fetch stage
   fetch.io.exc_target := memory.io.exception_target

   //**********************************
   // Writeback Stage
   
   // Wire memory stage outputs to writeback stage inputs
   writeback.io.mem_wbaddr := memory.io.wb_wbaddr
   writeback.io.mem_wbdata := memory.io.wb_wbdata
   writeback.io.mem_valid := memory.io.wb_valid_out
   writeback.io.mem_ctrl_rf_wen := memory.io.wb_ctrl_rf_wen

   //**********************************
   // External Signals

   // datapath to controlpath outputs
   io.dat.dec_valid  := dec_reg_valid
   io.dat.dec_inst   := decode.io.dec_inst_reg
   io.dat.exe_br_eq  := execute.io.exe_br_eq
   io.dat.exe_br_lt  := execute.io.exe_br_lt
   io.dat.exe_br_ltu := execute.io.exe_br_ltu
   io.dat.exe_br_type:= execute.io.exe_br_type
   io.dat.exe_inst_misaligned := execute.io.exe_inst_misaligned

   io.dat.mem_ctrl_dmem_val := memory.io.mem_ctrl_dmem_val
   io.dat.mem_data_misaligned := memory.io.mem_data_misaligned
   io.dat.mem_store := memory.io.mem_store
   io.dat.csr_interrupt := memory.io.csr_interrupt
   io.dat.csr_eret := memory.io.csr_eret

   val wb_reg_inst = RegNext(memory.io.wb_wbdata)  // FIXME: This should be inst, not wbdata

   printf("Cyc= %d [%d] pc=[%x] W[r%d=%x][%d] Op1=[r%d][%x] Op2=[r%d][%x] inst=[%x] %c%c%c DASM(%x)\n",
      memory.io.csr_time(31,0),
      memory.io.csr_retire,
      RegNext(memory.io.exe_pc),
      writeback.io.wb_wbaddr,
      writeback.io.wb_wbdata,
      writeback.io.wb_ctrl_rf_wen,
      RegNext(memory.io.exe_rs1_addr),
      RegNext(memory.io.exe_op1_data),
      RegNext(memory.io.exe_rs2_addr),
      RegNext(memory.io.exe_op2_data),
      wb_reg_inst,
      MuxCase(Str(" "), Seq(
         io.ctl.pipeline_kill -> Str("K"),
         io.ctl.full_stall -> Str("F"),
         io.ctl.dec_stall -> Str("S"))),
      MuxLookup(io.ctl.exe_pc_sel, Str("?"))(Seq(
         PC_BRJMP -> Str("B"),
         PC_JALR -> Str("R"),
         PC_EXC -> Str("E"),
         PC_4 -> Str(" "))),
      Mux(io.ctl.mem_exception, Str("X"), Str(" ")),
      wb_reg_inst)
}
