//**************************************************************************
// RISCV Processor Memory Stage
//--------------------------------------------------------------------------
// 
// Priyansh Saha
// 2025 Dec 9

package sodor.stage5

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.rocket.{CSR, CSRFile, Causes}
import freechips.rocketchip.rocket.CoreInterrupts
import sodor.stage5.Constants._
import sodor.common._

class MemoryStageIo(implicit val p: Parameters, val conf: SodorCoreParams) extends Bundle
{
   // Inputs from execute stage
   val exe_alu_out = Input(UInt(conf.xprlen.W))
   val exe_pc = Input(UInt(conf.xprlen.W))
   val exe_inst = Input(UInt(conf.xprlen.W))
   val exe_wbaddr = Input(UInt(5.W))
   val exe_rs1_addr = Input(UInt(5.W))
   val exe_rs2_addr = Input(UInt(5.W))
   val exe_op1_data = Input(UInt(conf.xprlen.W))
   val exe_op2_data = Input(UInt(conf.xprlen.W))
   val exe_rs2_data = Input(UInt(conf.xprlen.W))
   val exe_valid = Input(Bool())
   
   // Control inputs
   val ctrl_rf_wen = Input(Bool())
   val ctrl_mem_val = Input(Bool())
   val ctrl_mem_fcn = Input(UInt())
   val ctrl_mem_typ = Input(UInt())
   val ctrl_wb_sel = Input(UInt())
   val ctrl_csr_cmd = Input(UInt())
   
   val pipeline_kill = Input(Bool())
   val full_stall = Input(Bool())
   
   // Exception inputs
   val mem_exception = Input(Bool())
   val mem_exception_cause = Input(UInt())
   val mem_tval_inst_ma = Input(UInt(conf.xprlen.W))
   
   // Interrupt and system inputs
   val interrupt = Input(new CoreInterrupts(false))
   val hartid = Input(UInt())
   
   // Writeback valid input (for CSR retire signal)
   val wb_valid = Input(Bool())
   
   // Data memory interface
   val dmem = new MemPortIo(conf.xprlen)
   
   // Outputs to writeback stage
   val wb_wbaddr = Output(UInt(5.W))
   val wb_wbdata = Output(UInt(conf.xprlen.W))
   val wb_valid_out = Output(Bool())
   val wb_ctrl_rf_wen = Output(Bool())
   
   // Outputs to control path
   val mem_data_misaligned = Output(Bool())
   val mem_store = Output(Bool())
   val mem_ctrl_dmem_val = Output(Bool())
   val csr_interrupt = Output(Bool())
   val csr_eret = Output(Bool())
   
   // Outputs for bypass (to decode stage)
   val mem_wbaddr = Output(UInt(5.W))
   val mem_wbdata = Output(UInt(conf.xprlen.W))
   val mem_ctrl_rf_wen = Output(Bool())
   
   // Output for exception handling
   val exception_target = Output(UInt(conf.xprlen.W))
   
   // Debug output
   val csr_time = Output(UInt(64.W))
   val csr_retire = Output(Bool())
}

class MemoryStage(implicit val p: Parameters, val conf: SodorCoreParams) extends Module
{
   val io = IO(new MemoryStageIo())
   
   //**********************************
   // Pipeline State Registers
   
   // Memory State
   val mem_reg_valid = RegInit(false.B)
   val mem_reg_pc = Reg(UInt(conf.xprlen.W))
   val mem_reg_inst = Reg(UInt(conf.xprlen.W))
   val mem_reg_alu_out = Reg(Bits())
   val mem_reg_wbaddr = Reg(UInt())
   val mem_reg_rs1_addr = Reg(UInt())
   val mem_reg_rs2_addr = Reg(UInt())
   val mem_reg_op1_data = Reg(UInt(conf.xprlen.W))
   val mem_reg_op2_data = Reg(UInt(conf.xprlen.W))
   val mem_reg_rs2_data = Reg(UInt(conf.xprlen.W))
   val mem_reg_ctrl_rf_wen = RegInit(false.B)
   val mem_reg_ctrl_mem_val = RegInit(false.B)
   val mem_reg_ctrl_mem_fcn = RegInit(M_X)
   val mem_reg_ctrl_mem_typ = RegInit(MT_X)
   val mem_reg_ctrl_wb_sel = Reg(UInt())
   val mem_reg_ctrl_csr_cmd = RegInit(CSR.N)
   
   // Update memory stage registers from execute stage
   when (io.pipeline_kill)
   {
      mem_reg_valid := false.B
      mem_reg_inst := BUBBLE
      mem_reg_ctrl_rf_wen := false.B
      mem_reg_ctrl_mem_val := false.B
      mem_reg_ctrl_csr_cmd := false.B
   }
   .elsewhen (!io.full_stall)
   {
      mem_reg_valid := io.exe_valid
      mem_reg_pc := io.exe_pc
      mem_reg_inst := io.exe_inst
      mem_reg_alu_out := io.exe_alu_out
      mem_reg_wbaddr := io.exe_wbaddr
      mem_reg_rs1_addr := io.exe_rs1_addr
      mem_reg_rs2_addr := io.exe_rs2_addr
      mem_reg_op1_data := io.exe_op1_data
      mem_reg_op2_data := io.exe_op2_data
      mem_reg_rs2_data := io.exe_rs2_data
      mem_reg_ctrl_rf_wen := io.ctrl_rf_wen
      mem_reg_ctrl_mem_val := io.ctrl_mem_val
      mem_reg_ctrl_mem_fcn := io.ctrl_mem_fcn
      mem_reg_ctrl_mem_typ := io.ctrl_mem_typ
      mem_reg_ctrl_wb_sel := io.ctrl_wb_sel
      mem_reg_ctrl_csr_cmd := io.ctrl_csr_cmd
   }
   
   //**********************************
   // Memory Stage Logic
   
   // Control Status Registers
   val csr = Module(new CSRFile(perfEventSets=CSREvents.events))
   csr.io := DontCare
   csr.io.decode(0).inst := mem_reg_inst
   csr.io.rw.addr := mem_reg_inst(CSR_ADDR_MSB, CSR_ADDR_LSB)
   csr.io.rw.wdata := mem_reg_alu_out
   csr.io.rw.cmd := mem_reg_ctrl_csr_cmd
   
   csr.io.retire := io.wb_valid
   csr.io.exception := io.mem_exception
   csr.io.pc := mem_reg_pc
   
   val mem_tval_data_ma = Wire(UInt(conf.xprlen.W))
   mem_tval_data_ma := mem_reg_alu_out.asUInt
   
   csr.io.tval := MuxCase(0.U, Array(
      (io.mem_exception_cause === Causes.illegal_instruction.U) -> mem_reg_inst,
      (io.mem_exception_cause === Causes.misaligned_fetch.U) -> io.mem_tval_inst_ma,
      (io.mem_exception_cause === Causes.misaligned_store.U) -> mem_tval_data_ma,
      (io.mem_exception_cause === Causes.misaligned_load.U) -> mem_tval_data_ma,
   ))
   
   // Interrupt rising edge detector
   val reg_interrupt_handled = RegNext(csr.io.interrupt, false.B)
   val interrupt_edge = csr.io.interrupt && !reg_interrupt_handled
   
   csr.io.interrupts := io.interrupt
   csr.io.hartid := io.hartid
   csr.io.cause := Mux(io.mem_exception, io.mem_exception_cause, csr.io.interrupt_cause)
   csr.io.ungated_clock := clock
   
   // Add your own uarch counters here!
   csr.io.counters.foreach(_.inc := false.B)
   
   // Data misalignment detection
   val misaligned_mask = Wire(UInt(3.W))
   misaligned_mask := ~(7.U(3.W) << (mem_reg_ctrl_mem_typ - 1.U)(1, 0))
   val mem_data_misaligned = (misaligned_mask & mem_reg_alu_out.asUInt.apply(2, 0)).orR && mem_reg_ctrl_mem_val
   
   // WB Mux
   val mem_wbdata = MuxCase(mem_reg_alu_out, Array(
      (mem_reg_ctrl_wb_sel === WB_ALU) -> mem_reg_alu_out,
      (mem_reg_ctrl_wb_sel === WB_PC4) -> mem_reg_alu_out,
      (mem_reg_ctrl_wb_sel === WB_MEM) -> io.dmem.resp.bits.data,
      (mem_reg_ctrl_wb_sel === WB_CSR) -> csr.io.rw.rdata
   ))
   
   //**********************************
   // Output Assignment
   
   // Outputs to writeback stage
   io.wb_wbaddr := mem_reg_wbaddr
   io.wb_wbdata := mem_wbdata
   io.wb_valid_out := mem_reg_valid && !io.mem_exception && !interrupt_edge
   io.wb_ctrl_rf_wen := Mux(io.mem_exception || interrupt_edge, false.B, mem_reg_ctrl_rf_wen)
   
   // Outputs to control path
   io.mem_data_misaligned := mem_data_misaligned
   io.mem_store := mem_reg_ctrl_mem_fcn === M_XWR
   io.mem_ctrl_dmem_val := mem_reg_ctrl_mem_val
   io.csr_interrupt := interrupt_edge
   io.csr_eret := csr.io.eret
   
   // Outputs for bypass (to decode stage)
   io.mem_wbaddr := mem_reg_wbaddr
   io.mem_wbdata := mem_wbdata
   io.mem_ctrl_rf_wen := mem_reg_ctrl_rf_wen
   
   // Output for exception handling
   io.exception_target := csr.io.evec
   
   // Data memory interface
   io.dmem.req.valid := mem_reg_ctrl_mem_val && !mem_data_misaligned
   io.dmem.req.bits.addr := mem_reg_alu_out.asUInt
   io.dmem.req.bits.fcn := mem_reg_ctrl_mem_fcn
   io.dmem.req.bits.typ := mem_reg_ctrl_mem_typ
   io.dmem.req.bits.data := mem_reg_rs2_data
   
   // Debug outputs
   io.csr_time := csr.io.time
   io.csr_retire := csr.io.retire
}
