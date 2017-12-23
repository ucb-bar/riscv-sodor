//**************************************************************************
// RISCV Processor 5-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2012 Jan 13
//
// TODO refactor stall, kill, fencei, flush signals. They're more confusing than they need to be.

package Sodor
{

import chisel3._
import chisel3.util._
import config._
import Constants._
import Common._

class DatToCtlIo(implicit p: Parameters) extends Bundle()
{
   val dec_inst    = Output(UInt(p(xprlen).W))
   val exe_br_eq   = Output(Bool())
   val exe_br_lt   = Output(Bool())
   val exe_br_ltu  = Output(Bool())
   val exe_br_type = Output(UInt(4.W))

   val mem_ctrl_dmem_val = Output(Bool())

   val csr_eret = Output(Bool())
   override def cloneType = { new DatToCtlIo().asInstanceOf[this.type] }
}

class DpathIo(implicit p: Parameters) extends Bundle()
{
   val ddpath = Flipped(new DebugDPath())
   val imem = new MemPortIo(p(xprlen))
   val dmem = new MemPortIo(p(xprlen))
   val ctl  = Flipped(new CtlToDatIo())
   val dat  = new DatToCtlIo()
}

class DatPath(implicit p: Parameters) extends Module
{
   val io = IO(new DpathIo())
   //Initialize IO
   io.dmem.req.bits := new MemReq(p(xprlen)).fromBits(0.U)
   io.imem.req.bits := new MemReq(p(xprlen)).fromBits(0.U)
   io.imem.resp.ready := true.B
   io.dmem.resp.ready := true.B
   io.imem.req.valid := false.B
   val xlen = p(xprlen)
   //**********************************
   // Pipeline State Registers

   // Instruction Fetch State
   val if_reg_pc             = Reg(init = START_ADDR)

   // Instruction Decode State
   val dec_reg_inst          = Reg(init=BUBBLE)
   val dec_reg_pc            = Reg(init=0.asUInt(xlen.W))

   // Execute State
   val exe_reg_inst          = Reg(init=BUBBLE)
   val exe_reg_pc            = Reg(init=0.asUInt(xlen.W))
   val exe_reg_wbaddr        = Reg(UInt(5.W))
   val exe_reg_rs1_addr      = Reg(UInt(5.W))
   val exe_reg_rs2_addr      = Reg(UInt(5.W))
   val exe_reg_op1_data      = Reg(UInt(xlen.W))
   val exe_reg_op2_data      = Reg(UInt(xlen.W))
   val exe_reg_rs2_data      = Reg(UInt(xlen.W))
   val exe_reg_ctrl_br_type  = Reg(init=BR_N)
   val exe_reg_ctrl_op2_sel  = Reg(UInt())
   val exe_reg_ctrl_alu_fun  = Reg(UInt())
   val exe_reg_ctrl_wb_sel   = Reg(UInt())
   val exe_reg_ctrl_rf_wen   = Reg(init=false.B)
   val exe_reg_ctrl_mem_val  = Reg(init=false.B)
   val exe_reg_ctrl_mem_fcn  = Reg(init=M_X)
   val exe_reg_ctrl_mem_typ  = Reg(init=MT_X)
   val exe_reg_ctrl_csr_cmd  = Reg(init=CSR.N)

   // Memory State
   val mem_reg_pc            = Reg(UInt(xlen.W))
   val mem_reg_inst          = Reg(UInt(xlen.W))
   val mem_reg_alu_out       = Reg(Bits())
   val mem_reg_wbaddr        = Reg(UInt())
   val mem_reg_rs1_addr      = Reg(UInt())
   val mem_reg_rs2_addr      = Reg(UInt())
   val mem_reg_op1_data      = Reg(UInt(xlen.W))
   val mem_reg_op2_data      = Reg(UInt(xlen.W))
   val mem_reg_rs2_data      = Reg(UInt(xlen.W))
   val mem_reg_ctrl_rf_wen   = Reg(init=false.B)
   val mem_reg_ctrl_mem_val  = Reg(init=false.B)
   val mem_reg_ctrl_mem_fcn  = Reg(init=M_X)
   val mem_reg_ctrl_mem_typ  = Reg(init=MT_X)
   val mem_reg_ctrl_wb_sel   = Reg(UInt())
   val mem_reg_ctrl_csr_cmd  = Reg(init=CSR.N)

   // Writeback State
   val wb_reg_wbaddr         = Reg(UInt())
   val wb_reg_wbdata         = Reg(UInt(xlen.W))
   val wb_reg_ctrl_rf_wen    = Reg(init=false.B)


   //**********************************
   // Instruction Fetch Stage
   val if_pc_next          = Wire(UInt(32.W))
   val exe_brjmp_target    = Wire(UInt(32.W))
   val exe_jump_reg_target = Wire(UInt(32.W))
   val exception_target    = Wire(UInt(32.W))

   when ((!io.ctl.dec_stall && !io.ctl.mem_stall) || io.ctl.pipeline_kill)
   {
      if_reg_pc := if_pc_next
   }

   val if_pc_plus4 = (if_reg_pc + 4.asUInt(xlen.W))

   if_pc_next := Mux(io.ctl.exe_pc_sel === PC_4,      if_pc_plus4,
                 Mux(io.ctl.exe_pc_sel === PC_BRJMP,  exe_brjmp_target,
                 Mux(io.ctl.exe_pc_sel === PC_JALR,   exe_jump_reg_target,
                 /*Mux(io.ctl.exe_pc_sel === PC_EXC*/ exception_target)))

   // for a fencei, refetch the if_pc (assuming no stall, no branch, and no exception)
   when (io.ctl.fencei && io.ctl.exe_pc_sel === PC_4 && 
         !io.ctl.dec_stall && !io.ctl.mem_stall && !io.ctl.pipeline_kill)
   {
      if_pc_next := if_reg_pc
   }

   // Instruction Memory
   io.imem.req.bits.addr := if_reg_pc
   val if_inst = io.imem.resp.bits.data

   when (io.ctl.pipeline_kill)
   {
      dec_reg_inst := BUBBLE
   }
   .elsewhen (!io.ctl.dec_stall && !io.ctl.mem_stall)
   {
      when (io.ctl.if_kill)
      {
         dec_reg_inst := BUBBLE
      }
      .otherwise
      {
         dec_reg_inst := if_inst
      }

      dec_reg_pc := if_reg_pc
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
               )).toUInt



   // Bypass Muxes
   val exe_alu_out  = Wire(UInt(xlen.W))
   val mem_wbdata   = Wire(UInt(xlen.W))

   val dec_op1_data = Wire(UInt(xlen.W))
   val dec_op2_data = Wire(UInt(xlen.W))
   val dec_rs2_data = Wire(UInt(xlen.W))

   if (p(USE_FULL_BYPASSING))
   {
      // roll the OP1 mux into the bypass mux logic
      dec_op1_data := MuxCase(rf_rs1_data, Array(
                           ((io.ctl.op1_sel === OP1_IMZ)) -> imm_z,
                           ((io.ctl.op1_sel === OP1_PC)) -> dec_reg_pc,
                           ((exe_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr != 0.U) && exe_reg_ctrl_rf_wen) -> exe_alu_out,
                           ((mem_reg_wbaddr === dec_rs1_addr) && (dec_rs1_addr != 0.U) && mem_reg_ctrl_rf_wen) -> mem_wbdata,
                           ((wb_reg_wbaddr  === dec_rs1_addr) && (dec_rs1_addr != 0.U) &&  wb_reg_ctrl_rf_wen) -> wb_reg_wbdata
                           ))

      dec_op2_data := MuxCase(dec_alu_op2, Array(
                           ((exe_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr != 0.U) && exe_reg_ctrl_rf_wen && (io.ctl.op2_sel === OP2_RS2)) -> exe_alu_out,
                           ((mem_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr != 0.U) && mem_reg_ctrl_rf_wen && (io.ctl.op2_sel === OP2_RS2)) -> mem_wbdata,
                           ((wb_reg_wbaddr  === dec_rs2_addr) && (dec_rs2_addr != 0.U) &&  wb_reg_ctrl_rf_wen && (io.ctl.op2_sel === OP2_RS2)) -> wb_reg_wbdata
                           ))

      dec_rs2_data := MuxCase(rf_rs2_data, Array(
                           ((exe_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr != 0.U) && exe_reg_ctrl_rf_wen) -> exe_alu_out,
                           ((mem_reg_wbaddr === dec_rs2_addr) && (dec_rs2_addr != 0.U) && mem_reg_ctrl_rf_wen) -> mem_wbdata,
                           ((wb_reg_wbaddr  === dec_rs2_addr) && (dec_rs2_addr != 0.U) &&  wb_reg_ctrl_rf_wen) -> wb_reg_wbdata
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


   when ((io.ctl.dec_stall && !io.ctl.mem_stall) || io.ctl.pipeline_kill)
   {
      // (kill exe stage)
      // insert NOP (bubble) into Execute stage on front-end stall (e.g., hazard clearing)
      exe_reg_inst          := BUBBLE
      exe_reg_wbaddr        := 0.U
      exe_reg_ctrl_rf_wen   := false.B
      exe_reg_ctrl_mem_val  := false.B
      exe_reg_ctrl_mem_fcn  := M_X
      exe_reg_ctrl_csr_cmd  := CSR.N
      exe_reg_ctrl_br_type  := BR_N
   }
   .elsewhen(!io.ctl.dec_stall && !io.ctl.mem_stall)
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

   val exe_alu_op1 = exe_reg_op1_data.toUInt
   val exe_alu_op2 = exe_reg_op2_data.toUInt

   // ALU
   val alu_shamt     = exe_alu_op2(4,0).toUInt
   val exe_adder_out = (exe_alu_op1 + exe_alu_op2)(xlen-1,0)

   //only for debug purposes right now until debug() works
   exe_alu_out := MuxCase(exe_reg_inst.toUInt, Array(
                  (exe_reg_ctrl_alu_fun === ALU_ADD)  -> exe_adder_out,
                  (exe_reg_ctrl_alu_fun === ALU_SUB)  -> (exe_alu_op1 - exe_alu_op2).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_AND)  -> (exe_alu_op1 & exe_alu_op2).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_OR)   -> (exe_alu_op1 | exe_alu_op2).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_XOR)  -> (exe_alu_op1 ^ exe_alu_op2).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_SLT)  -> (exe_alu_op1.toSInt < exe_alu_op2.toSInt).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_SLTU) -> (exe_alu_op1 < exe_alu_op2).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_SLL)  -> ((exe_alu_op1 << alu_shamt)(xlen-1, 0)).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_SRA)  -> (exe_alu_op1.toSInt >> alu_shamt).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_SRL)  -> (exe_alu_op1 >> alu_shamt).toUInt,
                  (exe_reg_ctrl_alu_fun === ALU_COPY_1)-> exe_alu_op1,
                  (exe_reg_ctrl_alu_fun === ALU_COPY_2)-> exe_alu_op2
                  ))

   // Branch/Jump Target Calculation
   val brjmp_offset    = exe_reg_op2_data
   exe_brjmp_target    := exe_reg_pc + brjmp_offset
   exe_jump_reg_target := exe_adder_out

   val exe_pc_plus4    = (exe_reg_pc + 4.U)(xlen-1,0)

   when (io.ctl.pipeline_kill)
   {
      mem_reg_pc            := BUBBLE
      mem_reg_ctrl_rf_wen   := false.B
      mem_reg_ctrl_mem_val  := false.B
      mem_reg_ctrl_csr_cmd  := false.B
   }
   .elsewhen (!io.ctl.mem_stall)
   {
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
   val csr = Module(new CSRFile())
   csr.io.decode.csr  := mem_reg_inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
   csr.io.rw.wdata := mem_reg_alu_out
   csr.io.rw.cmd   := mem_reg_ctrl_csr_cmd

   csr.io.retire    := !(Reg(next = mem_reg_inst) === BUBBLE) // can be made better
   csr.io.illegal := io.ctl.mem_illegal
   csr.io.pc        := mem_reg_pc
   exception_target := csr.io.evec

   io.dat.csr_eret := csr.io.eret

   // Add your own uarch counters here!
   csr.io.counters.foreach(_.inc := false.B)


   // WB Mux
   mem_wbdata := MuxCase(mem_reg_alu_out, Array(
                  (mem_reg_ctrl_wb_sel === WB_ALU) -> mem_reg_alu_out,
                  (mem_reg_ctrl_wb_sel === WB_PC4) -> mem_reg_alu_out,
                  (mem_reg_ctrl_wb_sel === WB_MEM) -> io.dmem.resp.bits.data,
                  (mem_reg_ctrl_wb_sel === WB_CSR) -> csr.io.rw.rdata
                  ))


   //**********************************
   // Writeback Stage
   when (!io.ctl.mem_stall)
   {
      wb_reg_wbaddr        := mem_reg_wbaddr
      wb_reg_wbdata        := mem_wbdata
      wb_reg_ctrl_rf_wen   := Mux(io.ctl.mem_illegal, false.B, mem_reg_ctrl_rf_wen)
   }
   .otherwise
   {
      wb_reg_ctrl_rf_wen   := false.B
   }



   //**********************************
   // External Signals

   // datapath to controlpath outputs
   io.dat.dec_inst   := dec_reg_inst
   io.dat.exe_br_eq  := (exe_reg_op1_data === exe_reg_rs2_data)
   io.dat.exe_br_lt  := (exe_reg_op1_data.toSInt < exe_reg_rs2_data.toSInt)
   io.dat.exe_br_ltu := (exe_reg_op1_data.toUInt < exe_reg_rs2_data.toUInt)
   io.dat.exe_br_type:= exe_reg_ctrl_br_type

   io.dat.mem_ctrl_dmem_val := mem_reg_ctrl_mem_val

   // datapath to data memory outputs
   io.dmem.req.valid     := mem_reg_ctrl_mem_val
   io.dmem.req.bits.addr := mem_reg_alu_out.toUInt
   io.dmem.req.bits.fcn  := mem_reg_ctrl_mem_fcn
   io.dmem.req.bits.typ  := mem_reg_ctrl_mem_typ
   io.dmem.req.bits.data := mem_reg_rs2_data

   // Printout
   printf("Cyc= %d (0x%x, 0x%x, 0x%x, 0x%x, 0x%x) WB[%c%c %x: 0x%x] %c %c %c ExeInst: DASM(%x)\n"
      , csr.io.time(31,0)
      , if_reg_pc
      , dec_reg_pc
      , exe_reg_pc
      , Reg(next=exe_reg_pc)
      , Reg(next=Reg(next=exe_reg_pc))
      , Mux(wb_reg_ctrl_rf_wen, Str("M"), Str(" ")) 
      , Mux(mem_reg_ctrl_rf_wen, Str("Z"), Str(" "))
      , wb_reg_wbaddr
      , wb_reg_wbdata
      , Mux(io.ctl.mem_stall, Str("F"),   //FREEZE-> F 
        Mux(io.ctl.dec_stall, Str("S"), Str(" ")))  //STALL->S
      , Mux(io.ctl.exe_pc_sel === 1.U, Str("B"),  //BJ -> B
        Mux(io.ctl.exe_pc_sel === 2.U, Str("J"),   //JR -> J
        Mux(io.ctl.exe_pc_sel === 3.U, Str("E"),   //EX -> E
        Mux(io.ctl.exe_pc_sel === 0.U, Str(" "), Str("?")))))
      , Mux(csr.io.illegal, Str("X"), Str(" "))
      , Mux(io.ctl.pipeline_kill, BUBBLE, exe_reg_inst)
      )

}


}
