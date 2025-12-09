//**************************************************************************
// RISCV Processor Writeback Stage
//--------------------------------------------------------------------------
// 
// Priyansh Saha
// 2025 Dec 8

package sodor.stage5

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import sodor.stage5.Constants._
import sodor.common._

class WritebackStageIo(implicit val p: Parameters, val conf: SodorCoreParams) extends Bundle
{
   // Inputs from memory stage
   val mem_wbaddr = Input(UInt(5.W))
   val mem_wbdata = Input(UInt(conf.xprlen.W))
   val mem_valid = Input(Bool())
   val mem_ctrl_rf_wen = Input(Bool())
   
   // Control inputs
   val full_stall = Input(Bool())
   
   // Outputs to decode stage (for bypass)
   val wb_wbaddr = Output(UInt(5.W))
   val wb_wbdata = Output(UInt(conf.xprlen.W))
   val wb_ctrl_rf_wen = Output(Bool())
   val wb_valid = Output(Bool())
}

class WritebackStage(implicit val p: Parameters, val conf: SodorCoreParams) extends Module
{
   val io = IO(new WritebackStageIo())
   
   //**********************************
   // Pipeline State Registers
   
   // Writeback State
   val wb_reg_valid = RegInit(false.B)
   val wb_reg_wbaddr = Reg(UInt())
   val wb_reg_wbdata = Reg(UInt(conf.xprlen.W))
   val wb_reg_ctrl_rf_wen = RegInit(false.B)
   
   // Update writeback registers from memory stage
   when (!io.full_stall)
   {
      wb_reg_valid := io.mem_valid
      wb_reg_wbaddr := io.mem_wbaddr
      wb_reg_wbdata := io.mem_wbdata
      wb_reg_ctrl_rf_wen := io.mem_ctrl_rf_wen
   }
   .otherwise
   {
      wb_reg_valid := false.B
      wb_reg_ctrl_rf_wen := false.B
   }
   
   //**********************************
   // Output Assignment
   
   io.wb_wbaddr := wb_reg_wbaddr
   io.wb_wbdata := wb_reg_wbdata
   io.wb_ctrl_rf_wen := wb_reg_ctrl_rf_wen
   io.wb_valid := wb_reg_valid
}
