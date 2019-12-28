//**************************************************************************
// RISCV Processor 3-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2013 Jun 29
//
// This concerns the "backend" datapath for the Z-Scale 3-stage processor.  The
// frontend is separated out (since the front-end is relatively decoupled, and
// because the designer may wish to swap out different front-ends for different
// performance/area tradeoffs).
//
// Thus, this file covers the Execute and Writeback stages on the 3-stage.

package Sodor
{

import chisel3._
import chisel3.util._


import Constants._
import Common._
import Common.Constants._

class DatToCtlIo(implicit val conf: SodorConfiguration) extends Bundle()
{
   val br_eq  = Output(Bool())
   val br_lt  = Output(Bool())
   val br_ltu = Output(Bool())
   val csr_eret = Output(Bool())
   override def cloneType = { new DatToCtlIo().asInstanceOf[this.type] }
}

class DpathIo(implicit val conf: SodorConfiguration) extends Bundle()
{
   val ddpath = Flipped(new DebugDPath())
   val imem = Flipped(new FrontEndCpuIO())
   val dmem = new MemPortIo(conf.xprlen)
   val ctl  = Input(new CtrlSignals())
   val dat  = new DatToCtlIo()
}

class DatPath(implicit val conf: SodorConfiguration) extends Module
{
   val io = IO(new DpathIo())
   io := DontCare


   //**********************************
   // Pipeline State Registers
   val wb_reg_valid    = RegInit(false.B)
   val wb_reg_ctrl     = Reg(new CtrlSignals)
   val wb_reg_pc       = Reg(UInt(conf.xprlen.W))
   val wb_reg_alu      = Reg(UInt(conf.xprlen.W))
   val wb_reg_csr_addr = Reg(UInt(12.W))
   val wb_reg_wbaddr   = Reg(UInt(log2Ceil(32).W))

   val wb_hazard_stall = Wire(Bool()) // hazard detected, stall in IF/EXE required

   //**********************************
   // Instruction Fetch Stage
   val exe_brjmp_target    = Wire(UInt(conf.xprlen.W))
   val exe_jump_reg_target = Wire(UInt(conf.xprlen.W))
   val exception_target    = Wire(UInt(conf.xprlen.W))

   io.imem.resp.ready := !wb_hazard_stall // stall IF if we detect a WB->EXE hazard

   // if front-end mispredicted, tell it which PC to take
   val take_pc = Mux(io.ctl.pc_sel === PC_EXC,   exception_target,
                 Mux(io.ctl.pc_sel === PC_JR,    exe_jump_reg_target,
                                                 exe_brjmp_target)) // PC_BR or PC_J

   io.imem.req.bits.pc := take_pc


   //**********************************
   // Execute Stage
   val exe_valid = io.imem.resp.valid
   val exe_inst  = io.imem.resp.bits.inst
   val exe_pc    = io.imem.resp.bits.pc

   // Decode
   val exe_rs1_addr = exe_inst(RS1_MSB, RS1_LSB)
   val exe_rs2_addr = exe_inst(RS2_MSB, RS2_LSB)
   val exe_wbaddr   = exe_inst(RD_MSB,  RD_LSB)

   val wb_wbdata    = Wire(UInt(conf.xprlen.W))

   // Hazard Stall Logic
   if(NUM_MEMORY_PORTS == 1) {
      // stall for more cycles incase of store after load with read after write conflict
      val count = RegInit(1.asUInt(2.W))
      when (io.ctl.dmem_val && (wb_reg_wbaddr === exe_rs1_addr) && (exe_rs1_addr =/= 0.U) && wb_reg_ctrl.rf_wen && !wb_reg_ctrl.bypassable)
      {
         count := 0.U
      }
      when(count =/= 2.U ){
         count := count + 1.U
      }
      wb_hazard_stall := ((wb_reg_wbaddr === exe_rs1_addr) && (exe_rs1_addr =/= 0.U) && wb_reg_ctrl.rf_wen && !wb_reg_ctrl.bypassable) ||
                         ((wb_reg_wbaddr === exe_rs2_addr) && (exe_rs2_addr =/= 0.U) && wb_reg_ctrl.rf_wen && !wb_reg_ctrl.bypassable) ||
                         (io.ctl.dmem_val && !RegNext(wb_hazard_stall)) || (io.ctl.dmem_val && (count =/= 2.U))
   }
   else{
      wb_hazard_stall := ((wb_reg_wbaddr === exe_rs1_addr) && (exe_rs1_addr =/= 0.U) && wb_reg_ctrl.rf_wen && !wb_reg_ctrl.bypassable) ||
                         ((wb_reg_wbaddr === exe_rs2_addr) && (exe_rs2_addr =/= 0.U) && wb_reg_ctrl.rf_wen && !wb_reg_ctrl.bypassable)
   }



   // Register File
   val regfile = Mem(32, UInt(conf.xprlen.W))

   //// DebugModule
   io.ddpath.rdata := regfile(io.ddpath.addr)
   when(io.ddpath.validreq){
      regfile(io.ddpath.addr) := io.ddpath.wdata
   }
   ///

   when (wb_reg_ctrl.rf_wen && (wb_reg_wbaddr =/= 0.U))
   {
      regfile(wb_reg_wbaddr) := wb_wbdata
   }

   val rf_rs1_data = Mux((exe_rs1_addr =/= 0.U) , regfile(exe_rs1_addr), 0.asUInt(conf.xprlen.W))
   val rf_rs2_data = Mux((exe_rs2_addr =/= 0.U) , regfile(exe_rs2_addr), 0.asUInt(conf.xprlen.W))


   // immediates
   val imm_i = exe_inst(31, 20)
   val imm_s = Cat(exe_inst(31, 25), exe_inst(11,7))
   val imm_b = Cat(exe_inst(31), exe_inst(7), exe_inst(30,25), exe_inst(11,8))
   val imm_u = Cat(exe_inst(31, 12), Fill(12,0.U))
   val imm_j = Cat(exe_inst(31), exe_inst(19,12), exe_inst(20), exe_inst(30,21))
   val imm_z = exe_inst(19,15)

   // sign-extend immediates
   val imm_i_sext = Cat(Fill(20,imm_i(11)), imm_i)
   val imm_s_sext = Cat(Fill(20,imm_s(11)), imm_s)
   val imm_b_sext = Cat(Fill(19,imm_b(11)), imm_b, 0.U)
   val imm_j_sext = Cat(Fill(11,imm_j(19)), imm_j, 0.U)


   // Bypass Muxes
   // bypass early for branch condition checking, and to prevent needing 3 bypass muxes
   val exe_rs1_data = MuxCase(rf_rs1_data, Array(
                           ((wb_reg_wbaddr === exe_rs1_addr) && (exe_rs1_addr =/= 0.U) && wb_reg_ctrl.rf_wen && wb_reg_ctrl.bypassable) -> wb_reg_alu)
                        )
   val exe_rs2_data = MuxCase(rf_rs2_data, Array(
                           ((wb_reg_wbaddr === exe_rs2_addr) && (exe_rs2_addr =/= 0.U) && wb_reg_ctrl.rf_wen && wb_reg_ctrl.bypassable) -> wb_reg_alu)
                        )


   // Operand Muxes
   val exe_alu_op1 = Mux(io.ctl.op1_sel === OP1_IMZ, imm_z,
                     Mux(io.ctl.op1_sel === OP1_IMU, imm_u,
                                                     exe_rs1_data)).asUInt()

   val exe_alu_op2 = Mux(io.ctl.op2_sel === OP2_IMI, imm_i_sext,
                     Mux(io.ctl.op2_sel === OP2_PC,  exe_pc,
                     Mux(io.ctl.op2_sel === OP2_IMS, imm_s_sext,
                                                     exe_rs2_data))).asUInt()


   // ALU
   val alu = Module(new ALU())

      alu.io.in1 := exe_alu_op1
      alu.io.in2 := exe_alu_op2
      alu.io.fn  := io.ctl.alu_fun

   val exe_alu_out = alu.io.out

   // Branch/Jump Target Calculation
   val imm_brjmp = Mux(io.ctl.brjmp_sel, imm_j_sext, imm_b_sext)
   exe_brjmp_target := exe_pc + imm_brjmp
   exe_jump_reg_target := alu.io.adder_out


   // datapath to controlpath outputs
   io.dat.br_eq  := (exe_rs1_data === exe_rs2_data)
   io.dat.br_lt  := (exe_rs1_data.asSInt() < exe_rs2_data.asSInt())
   io.dat.br_ltu := (exe_rs1_data.asUInt() < exe_rs2_data.asUInt())

   // datapath to data memory outputs
   io.dmem.req.valid     := io.ctl.dmem_val
   if(NUM_MEMORY_PORTS == 1)
      io.dmem.req.bits.fcn  := io.ctl.dmem_fcn & exe_valid & !((wb_reg_wbaddr === exe_rs1_addr) && (exe_rs1_addr =/= 0.U) && wb_reg_ctrl.rf_wen && !wb_reg_ctrl.bypassable)
   else
      io.dmem.req.bits.fcn  := io.ctl.dmem_fcn & !wb_hazard_stall & exe_valid
   io.dmem.req.bits.typ  := io.ctl.dmem_typ
   io.dmem.req.bits.addr := exe_alu_out
   io.dmem.req.bits.data := exe_rs2_data


   // execute to wb registers
   wb_reg_valid := exe_valid
   wb_reg_ctrl :=  io.ctl
   wb_reg_pc := exe_pc
   when (wb_hazard_stall || io.ctl.exe_kill)
   {
      wb_reg_valid          := false.B
      wb_reg_ctrl.rf_wen    := false.B
      wb_reg_ctrl.csr_cmd   := CSR.N
      wb_reg_ctrl.dmem_val  := false.B
      wb_reg_ctrl.exception := false.B
   }

   wb_reg_alu      := exe_alu_out
   wb_reg_wbaddr   := exe_wbaddr
   wb_reg_csr_addr := exe_inst(CSR_ADDR_MSB,CSR_ADDR_LSB)

   //**********************************
   // Writeback Stage

   // Control Status Registers
   val csr = Module(new CSRFile())
   csr.io := DontCare
   csr.io.decode.csr   := wb_reg_csr_addr
   csr.io.rw.wdata  := wb_reg_alu
   csr.io.rw.cmd    := wb_reg_ctrl.csr_cmd
   val wb_csr_out    = csr.io.rw.rdata

   csr.io.retire    := wb_reg_valid && !wb_reg_ctrl.exception
   csr.io.exception := wb_reg_ctrl.exception
   csr.io.pc        := wb_reg_pc
   exception_target := csr.io.evec
   io.dat.csr_eret := csr.io.eret

   // Add your own uarch counters here!
   csr.io.counters.foreach(_.inc := false.B)

   // WB Mux
   // Note: I'm relying on the fact that the EXE stage is holding the
   // instruction behind our jal, which assumes we always predict PC+4, and we
   // don't clear the "mispredicted" PC when we jump.
   require (PREDICT_PCP4==true)
   wb_wbdata := MuxCase(wb_reg_alu, Array(
                  (wb_reg_ctrl.wb_sel === WB_ALU) -> wb_reg_alu,
                  (wb_reg_ctrl.wb_sel === WB_MEM) -> io.dmem.resp.bits.data,
                  (wb_reg_ctrl.wb_sel === WB_PC4) -> exe_pc,
                  (wb_reg_ctrl.wb_sel === WB_CSR) -> wb_csr_out
                  ))

   //**********************************
   // Printout

   val debug_wb_inst = RegNext(Mux((wb_hazard_stall || io.ctl.exe_kill || !exe_valid), BUBBLE, exe_inst))

   printf("Cyc= %d [%d] pc=[%x] W[r%d=%x][%d] Op1=[r%d][%x] Op2=[r%d][%x] inst=[%x] %c%c%c DASM(%x)\n",
      csr.io.time(31,0),
      csr.io.retire,
      wb_reg_pc,
      wb_reg_wbaddr,
      wb_wbdata,
      wb_reg_ctrl.rf_wen,
      RegNext(exe_rs1_addr),
      RegNext(exe_alu_op1),
      RegNext(exe_rs2_addr),
      RegNext(exe_alu_op2),
      debug_wb_inst,
      MuxCase(Str(" "), Seq(
         wb_hazard_stall -> Str("H"),
         io.ctl.exe_kill -> Str("K"))),
      MuxLookup(io.ctl.pc_sel, Str("?"), Seq(
         PC_BR -> Str("B"),
         PC_J -> Str("J"),
         PC_JR -> Str("R"),
         PC_EXC -> Str("E"),
         PC_4 -> Str(" "))),
      Mux(csr.io.exception, Str("X"), Str(" ")),
      debug_wb_inst)

   // for debugging, print out the commit information.
   // can be compared against the riscv-isa-run Spike ISA simulator's commit logger.
   // use "sed" to parse out "@@@" from the other printf code above.
   if (PRINT_COMMIT_LOG)
   {
      when (wb_reg_valid)
      {
         val rd = debug_wb_inst(RD_MSB,RD_LSB)
         when (wb_reg_ctrl.rf_wen && rd =/= 0.U)
         {
            printf("@@@ 0x%x (0x%x) x%d 0x%x\n", wb_reg_pc, debug_wb_inst, rd, Cat(Fill(32,wb_wbdata(31)),wb_wbdata))
         }
         .otherwise
         {
            printf("@@@ 0x%x (0x%x)\n", wb_reg_pc, debug_wb_inst)
         }
      }
   }

}


}
