//**************************************************************************
// RISCV Processor Instruction Decode Stage
//--------------------------------------------------------------------------
// 
// Priyansh Saha
// 2025 Dec 7

package sodor.stage5

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import sodor.stage5.Constants._
import sodor.common._

class InstructionDecodeStageIo(implicit val p: Parameters, val conf: SodorCoreParams) extends Bundle
{
   // Inputs from fetch stage
   val fetch_inst = Input(UInt(conf.xprlen.W))
   val fetch_pc = Input(UInt(conf.xprlen.W))
   val fetch_valid = Input(Bool())
   
   // Control inputs
   val op1_sel = Input(UInt(2.W))
   val op2_sel = Input(UInt(3.W))
   val dec_kill = Input(Bool())
   val dec_stall = Input(Bool())
   val full_stall = Input(Bool())
   val pipeline_kill = Input(Bool())
   
   // Bypass inputs from execute stage
   val exe_wbaddr = Input(UInt(5.W))
   val exe_alu_out = Input(UInt(conf.xprlen.W))
   val exe_ctrl_rf_wen = Input(Bool())
   
   // Bypass inputs from memory stage
   val mem_wbaddr = Input(UInt(5.W))
   val mem_wbdata = Input(UInt(conf.xprlen.W))
   val mem_ctrl_rf_wen = Input(Bool())
   
   // Bypass inputs from writeback stage
   val wb_wbaddr = Input(UInt(5.W))
   val wb_wbdata = Input(UInt(conf.xprlen.W))
   val wb_ctrl_rf_wen = Input(Bool())
   
   // Register file write interface
   val rf_waddr = Input(UInt(5.W))
   val rf_wdata = Input(UInt(conf.xprlen.W))
   val rf_wen = Input(Bool())
   
   // Debug interface
   val ddpath = Flipped(new DebugDPath())
   
   // Outputs to execute stage
   val exe_rs1_addr = Output(UInt(5.W))
   val exe_rs2_addr = Output(UInt(5.W))
   val dec_wbaddr = Output(UInt(5.W))
   val exe_op1_data = Output(UInt(conf.xprlen.W))
   val exe_op2_data = Output(UInt(conf.xprlen.W))
   val exe_rs2_data = Output(UInt(conf.xprlen.W))
   val exe_pc = Output(UInt(conf.xprlen.W))
   
   // Registered instruction output for control path (breaks combinational cycle)
   val dec_inst_reg = Output(UInt(conf.xprlen.W))
}

class InstructionDecodeStage(implicit val p: Parameters, val conf: SodorCoreParams) extends Module
{
   val io = IO(new InstructionDecodeStageIo())
   
   //**********************************
   // Pipeline State Registers
   
   // Instruction Decode State
   val dec_reg_inst = RegInit(BUBBLE)
   val dec_reg_pc = RegInit(0.asUInt(conf.xprlen.W))
   
   // Update decode registers from fetch stage
   when (io.pipeline_kill)
   {
      dec_reg_inst := BUBBLE
   }
   .elsewhen (!io.dec_stall && !io.full_stall)
   {
      dec_reg_inst := io.fetch_inst
      dec_reg_pc := io.fetch_pc
   }
   
   //**********************************
   // Decode Stage Logic
   
   val dec_rs1_addr = dec_reg_inst(19, 15)
   val dec_rs2_addr = dec_reg_inst(24, 20)
   val dec_wbaddr = dec_reg_inst(11, 7)
   
   // Register File
   val regfile = Module(new RegisterFile())
   regfile.io.rs1_addr := dec_rs1_addr
   regfile.io.rs2_addr := dec_rs2_addr
   val rf_rs1_data = regfile.io.rs1_data
   val rf_rs2_data = regfile.io.rs2_data
   regfile.io.waddr := io.rf_waddr
   regfile.io.wdata := io.rf_wdata
   regfile.io.wen := io.rf_wen
   
   // DebugModule
   regfile.io.dm_addr := io.ddpath.addr
   io.ddpath.rdata := regfile.io.dm_rdata
   regfile.io.dm_en := io.ddpath.validreq
   regfile.io.dm_wdata := io.ddpath.wdata
   
   // Immediates
   val imm_itype = dec_reg_inst(31, 20)
   val imm_stype = Cat(dec_reg_inst(31, 25), dec_reg_inst(11, 7))
   val imm_sbtype = Cat(dec_reg_inst(31), dec_reg_inst(7), dec_reg_inst(30, 25), dec_reg_inst(11, 8))
   val imm_utype = dec_reg_inst(31, 12)
   val imm_ujtype = Cat(dec_reg_inst(31), dec_reg_inst(19, 12), dec_reg_inst(20), dec_reg_inst(30, 21))
   
   val imm_z = Cat(Fill(27, 0.U), dec_reg_inst(19, 15))
   
   // Sign-extend immediates
   val imm_itype_sext = Cat(Fill(20, imm_itype(11)), imm_itype)
   val imm_stype_sext = Cat(Fill(20, imm_stype(11)), imm_stype)
   val imm_sbtype_sext = Cat(Fill(19, imm_sbtype(11)), imm_sbtype, 0.U)
   val imm_utype_sext = Cat(imm_utype, Fill(12, 0.U))
   val imm_ujtype_sext = Cat(Fill(11, imm_ujtype(19)), imm_ujtype, 0.U)
   
   // Operand 2 Mux
   val dec_alu_op2 = MuxCase(0.U, Array(
      (io.op2_sel === OP2_RS2) -> rf_rs2_data,
      (io.op2_sel === OP2_ITYPE) -> imm_itype_sext,
      (io.op2_sel === OP2_STYPE) -> imm_stype_sext,
      (io.op2_sel === OP2_SBTYPE) -> imm_sbtype_sext,
      (io.op2_sel === OP2_UTYPE) -> imm_utype_sext,
      (io.op2_sel === OP2_UJTYPE) -> imm_ujtype_sext
   )).asUInt
   
   // Bypass Muxes
   val dec_op1_data = Wire(UInt(conf.xprlen.W))
   val dec_op2_data = Wire(UInt(conf.xprlen.W))
   val dec_rs2_data = Wire(UInt(conf.xprlen.W))
   
   if (USE_FULL_BYPASSING)
   {
      // Roll the OP1 mux into the bypass mux logic
      dec_op1_data := MuxCase(rf_rs1_data, Array(
         ((io.op1_sel === OP1_IMZ)) -> imm_z,
         ((io.op1_sel === OP1_PC)) -> dec_reg_pc,
         ((io.exe_wbaddr === dec_rs1_addr) && (dec_rs1_addr =/= 0.U) && io.exe_ctrl_rf_wen) -> io.exe_alu_out,
         ((io.mem_wbaddr === dec_rs1_addr) && (dec_rs1_addr =/= 0.U) && io.mem_ctrl_rf_wen) -> io.mem_wbdata,
         ((io.wb_wbaddr === dec_rs1_addr) && (dec_rs1_addr =/= 0.U) && io.wb_ctrl_rf_wen) -> io.wb_wbdata
      ))
      
      dec_op2_data := MuxCase(dec_alu_op2, Array(
         ((io.exe_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && io.exe_ctrl_rf_wen && (io.op2_sel === OP2_RS2)) -> io.exe_alu_out,
         ((io.mem_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && io.mem_ctrl_rf_wen && (io.op2_sel === OP2_RS2)) -> io.mem_wbdata,
         ((io.wb_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && io.wb_ctrl_rf_wen && (io.op2_sel === OP2_RS2)) -> io.wb_wbdata
      ))
      
      dec_rs2_data := MuxCase(rf_rs2_data, Array(
         ((io.exe_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && io.exe_ctrl_rf_wen) -> io.exe_alu_out,
         ((io.mem_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && io.mem_ctrl_rf_wen) -> io.mem_wbdata,
         ((io.wb_wbaddr === dec_rs2_addr) && (dec_rs2_addr =/= 0.U) && io.wb_ctrl_rf_wen) -> io.wb_wbdata
      ))
   }
   else
   {
      // Rely only on control interlocking to resolve hazards
      dec_op1_data := MuxCase(rf_rs1_data, Array(
         ((io.op1_sel === OP1_IMZ)) -> imm_z,
         ((io.op1_sel === OP1_PC)) -> dec_reg_pc
      ))
      dec_rs2_data := rf_rs2_data
      dec_op2_data := dec_alu_op2
   }
   
   //**********************************
   // Output Assignment
   
   io.exe_rs1_addr := dec_rs1_addr
   io.exe_rs2_addr := dec_rs2_addr
   io.dec_wbaddr := dec_wbaddr
   io.exe_op1_data := dec_op1_data
   io.exe_op2_data := dec_op2_data
   io.exe_rs2_data := dec_rs2_data
   io.exe_pc := dec_reg_pc
   io.dec_inst_reg := dec_reg_inst
}
