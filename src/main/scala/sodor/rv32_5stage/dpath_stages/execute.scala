//**************************************************************************
// RISCV Processor Execute Stage
//--------------------------------------------------------------------------
// 
// Priyansh Saha
// 2025 Dec 8

package sodor.stage5

import chisel3._
import chisel3.util._

import org.chipsalliance.cde.config.Parameters
import freechips.rocketchip.rocket.CSR
import sodor.stage5.Constants._
import sodor.common._

class ExecuteStageIo(implicit val p: Parameters, val conf: SodorCoreParams) extends Bundle
{
   // Inputs from decode stage
   val dec_rs1_addr = Input(UInt(5.W))
   val dec_rs2_addr = Input(UInt(5.W))
   val dec_wbaddr = Input(UInt(5.W))
   val dec_op1_data = Input(UInt(conf.xprlen.W))
   val dec_op2_data = Input(UInt(conf.xprlen.W))
   val dec_rs2_data = Input(UInt(conf.xprlen.W))
   val dec_pc = Input(UInt(conf.xprlen.W))
   val dec_valid = Input(Bool())
   val dec_inst = Input(UInt(conf.xprlen.W))
   
   // Control inputs
   val alu_fun = Input(UInt())
   val wb_sel = Input(UInt())
   val br_type = Input(UInt(4.W))
   val rf_wen = Input(Bool())
   val mem_val = Input(Bool())
   val mem_fcn = Input(UInt())
   val mem_typ = Input(UInt())
   val csr_cmd = Input(UInt())
   val op2_sel = Input(UInt())
   
   val dec_kill = Input(Bool())
   val dec_stall = Input(Bool())
   val full_stall = Input(Bool())
   val pipeline_kill = Input(Bool())
   val exe_pc_sel = Input(UInt(2.W))
   
   // Outputs to memory stage
   val mem_alu_out = Output(UInt(conf.xprlen.W))
   val mem_pc = Output(UInt(conf.xprlen.W))
   val mem_inst = Output(UInt(conf.xprlen.W))
   val mem_wbaddr = Output(UInt(5.W))
   val mem_rs1_addr = Output(UInt(5.W))
   val mem_rs2_addr = Output(UInt(5.W))
   val mem_op1_data = Output(UInt(conf.xprlen.W))
   val mem_op2_data = Output(UInt(conf.xprlen.W))
   val mem_rs2_data = Output(UInt(conf.xprlen.W))
   val mem_valid = Output(Bool())
   
   // Memory control outputs (pass-through)
   val mem_ctrl_rf_wen = Output(Bool())
   val mem_ctrl_mem_val = Output(Bool())
   val mem_ctrl_mem_fcn = Output(UInt())
   val mem_ctrl_mem_typ = Output(UInt())
   val mem_ctrl_wb_sel = Output(UInt())
   val mem_ctrl_csr_cmd = Output(UInt())
   
   // Outputs to fetch stage
   val brjmp_target = Output(UInt(conf.xprlen.W))
   val jalr_target = Output(UInt(conf.xprlen.W))
   
   // Outputs to control path
   val exe_br_eq = Output(Bool())
   val exe_br_lt = Output(Bool())
   val exe_br_ltu = Output(Bool())
   val exe_br_type = Output(UInt(4.W))
   val exe_inst_misaligned = Output(Bool())
   
   // Outputs for bypass (to decode stage)
   val exe_wbaddr = Output(UInt(5.W))
   val exe_alu_out = Output(UInt(conf.xprlen.W))
   val exe_ctrl_rf_wen = Output(Bool())
   
   // Exception value output
   val mem_tval_inst_ma = Output(UInt(conf.xprlen.W))
}

class ExecuteStage(implicit val p: Parameters, val conf: SodorCoreParams) extends Module
{
   val io = IO(new ExecuteStageIo())
   
   //**********************************
   // Pipeline State Registers
   
   // Execute State
   val exe_reg_valid = RegInit(false.B)
   val exe_reg_inst = RegInit(BUBBLE)
   val exe_reg_pc = RegInit(0.asUInt(conf.xprlen.W))
   val exe_reg_wbaddr = Reg(UInt(5.W))
   val exe_reg_rs1_addr = Reg(UInt(5.W))
   val exe_reg_rs2_addr = Reg(UInt(5.W))
   val exe_reg_op1_data = Reg(UInt(conf.xprlen.W))
   val exe_reg_op2_data = Reg(UInt(conf.xprlen.W))
   val exe_reg_rs2_data = Reg(UInt(conf.xprlen.W))
   val exe_reg_ctrl_br_type = RegInit(BR_N)
   val exe_reg_ctrl_op2_sel = Reg(UInt())
   val exe_reg_ctrl_alu_fun = Reg(UInt())
   val exe_reg_ctrl_wb_sel = Reg(UInt())
   val exe_reg_ctrl_rf_wen = RegInit(false.B)
   val exe_reg_ctrl_mem_val = RegInit(false.B)
   val exe_reg_ctrl_mem_fcn = RegInit(M_X)
   val exe_reg_ctrl_mem_typ = RegInit(MT_X)
   val exe_reg_ctrl_csr_cmd = RegInit(CSR.N)
   
   // Update execute stage registers from decode stage
   when ((io.dec_stall && !io.full_stall) || io.pipeline_kill)
   {
      // Kill exe stage - insert NOP (bubble)
      exe_reg_valid := false.B
      exe_reg_inst := BUBBLE
      exe_reg_wbaddr := 0.U
      exe_reg_ctrl_rf_wen := false.B
      exe_reg_ctrl_mem_val := false.B
      exe_reg_ctrl_mem_fcn := M_X
      exe_reg_ctrl_csr_cmd := CSR.N
      exe_reg_ctrl_br_type := BR_N
   }
   .elsewhen(!io.dec_stall && !io.full_stall)
   {
      // No stalling - update from decode stage
      exe_reg_pc := io.dec_pc
      exe_reg_rs1_addr := io.dec_rs1_addr
      exe_reg_rs2_addr := io.dec_rs2_addr
      exe_reg_op1_data := io.dec_op1_data
      exe_reg_op2_data := io.dec_op2_data
      exe_reg_rs2_data := io.dec_rs2_data
      exe_reg_ctrl_op2_sel := io.op2_sel
      exe_reg_ctrl_alu_fun := io.alu_fun
      exe_reg_ctrl_wb_sel := io.wb_sel
      
      when (io.dec_kill)
      {
         exe_reg_valid := false.B
         exe_reg_inst := BUBBLE
         exe_reg_wbaddr := 0.U
         exe_reg_ctrl_rf_wen := false.B
         exe_reg_ctrl_mem_val := false.B
         exe_reg_ctrl_mem_fcn := M_X
         exe_reg_ctrl_csr_cmd := CSR.N
         exe_reg_ctrl_br_type := BR_N
      }
      .otherwise
      {
         exe_reg_valid := io.dec_valid
         exe_reg_inst := io.dec_inst
         exe_reg_wbaddr := io.dec_wbaddr
         exe_reg_ctrl_rf_wen := io.rf_wen
         exe_reg_ctrl_mem_val := io.mem_val
         exe_reg_ctrl_mem_fcn := io.mem_fcn
         exe_reg_ctrl_mem_typ := io.mem_typ
         exe_reg_ctrl_csr_cmd := io.csr_cmd
         exe_reg_ctrl_br_type := io.br_type
      }
   }
   
   //**********************************
   // Execute Stage Logic
   
   val exe_alu_op1 = exe_reg_op1_data.asUInt
   val exe_alu_op2 = exe_reg_op2_data.asUInt
   
   // ALU
   val alu_shamt = exe_alu_op2(4, 0).asUInt
   val exe_adder_out = (exe_alu_op1 + exe_alu_op2)(conf.xprlen - 1, 0)
   
   val exe_alu_out = MuxCase(exe_reg_inst.asUInt, Array(
      (exe_reg_ctrl_alu_fun === ALU_ADD) -> exe_adder_out,
      (exe_reg_ctrl_alu_fun === ALU_SUB) -> (exe_alu_op1 - exe_alu_op2).asUInt,
      (exe_reg_ctrl_alu_fun === ALU_AND) -> (exe_alu_op1 & exe_alu_op2).asUInt,
      (exe_reg_ctrl_alu_fun === ALU_OR) -> (exe_alu_op1 | exe_alu_op2).asUInt,
      (exe_reg_ctrl_alu_fun === ALU_XOR) -> (exe_alu_op1 ^ exe_alu_op2).asUInt,
      (exe_reg_ctrl_alu_fun === ALU_SLT) -> (exe_alu_op1.asSInt < exe_alu_op2.asSInt).asUInt,
      (exe_reg_ctrl_alu_fun === ALU_SLTU) -> (exe_alu_op1 < exe_alu_op2).asUInt,
      (exe_reg_ctrl_alu_fun === ALU_SLL) -> ((exe_alu_op1 << alu_shamt)(conf.xprlen - 1, 0)).asUInt,
      (exe_reg_ctrl_alu_fun === ALU_SRA) -> (exe_alu_op1.asSInt >> alu_shamt).asUInt,
      (exe_reg_ctrl_alu_fun === ALU_SRL) -> (exe_alu_op1 >> alu_shamt).asUInt,
      (exe_reg_ctrl_alu_fun === ALU_COPY_1) -> exe_alu_op1,
      (exe_reg_ctrl_alu_fun === ALU_COPY_2) -> exe_alu_op2
   ))
   
   // Branch/Jump Target Calculation
   val brjmp_offset = exe_reg_op2_data
   val brjmp_target = exe_reg_pc + brjmp_offset
   val jump_reg_target = exe_adder_out & ~1.U(conf.xprlen.W)
   
   // Branch Comparators
   val exe_br_eq = (exe_reg_op1_data === exe_reg_rs2_data)
   val exe_br_lt = (exe_reg_op1_data.asSInt < exe_reg_rs2_data.asSInt)
   val exe_br_ltu = (exe_reg_op1_data.asUInt < exe_reg_rs2_data.asUInt)
   
   // Instruction misalignment detection
   val exe_inst_misaligned = (brjmp_target(1, 0).orR && io.exe_pc_sel === PC_BRJMP) ||
                             (jump_reg_target(1, 0).orR && io.exe_pc_sel === PC_JALR)
   
   val mem_tval_inst_ma = RegNext(Mux(io.exe_pc_sel === PC_BRJMP, brjmp_target, jump_reg_target))
   
   val exe_pc_plus4 = (exe_reg_pc + 4.U)(conf.xprlen - 1, 0)
   
   //**********************************
   // Output Assignment
   
   // Outputs to memory stage
   io.mem_alu_out := Mux((exe_reg_ctrl_wb_sel === WB_PC4), exe_pc_plus4, exe_alu_out)
   io.mem_pc := exe_reg_pc
   io.mem_inst := exe_reg_inst
   io.mem_wbaddr := exe_reg_wbaddr
   io.mem_rs1_addr := exe_reg_rs1_addr
   io.mem_rs2_addr := exe_reg_rs2_addr
   io.mem_op1_data := exe_reg_op1_data
   io.mem_op2_data := exe_reg_op2_data
   io.mem_rs2_data := exe_reg_rs2_data
   io.mem_valid := exe_reg_valid
   
   // Memory control outputs (pass-through)
   io.mem_ctrl_rf_wen := exe_reg_ctrl_rf_wen
   io.mem_ctrl_mem_val := exe_reg_ctrl_mem_val
   io.mem_ctrl_mem_fcn := exe_reg_ctrl_mem_fcn
   io.mem_ctrl_mem_typ := exe_reg_ctrl_mem_typ
   io.mem_ctrl_wb_sel := exe_reg_ctrl_wb_sel
   io.mem_ctrl_csr_cmd := exe_reg_ctrl_csr_cmd
   
   // Outputs to fetch stage
   io.brjmp_target := brjmp_target
   io.jalr_target := jump_reg_target
   
   // Outputs to control path
   io.exe_br_eq := exe_br_eq
   io.exe_br_lt := exe_br_lt
   io.exe_br_ltu := exe_br_ltu
   io.exe_br_type := exe_reg_ctrl_br_type
   io.exe_inst_misaligned := exe_inst_misaligned
   
   // Outputs for bypass (to decode stage)
   io.exe_wbaddr := exe_reg_wbaddr
   io.exe_alu_out := exe_alu_out
   io.exe_ctrl_rf_wen := exe_reg_ctrl_rf_wen
   
   // Exception value output
   io.mem_tval_inst_ma := mem_tval_inst_ma
}
