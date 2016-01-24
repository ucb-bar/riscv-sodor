//**************************************************************************
// RISCV Processor 1-Stage Datapath
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2012 Jan 11

package Sodor
{

import Chisel._
import Node._

import Constants._
import Common._
import Common.Constants._

class DatToCtlIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val inst   = Bits(OUTPUT, 32)
   val br_eq  = Bool(OUTPUT)
   val br_lt  = Bool(OUTPUT)
   val br_ltu = Bool(OUTPUT)
//   val csr    = new CSRFileIO()
//   val status = new MStatus().asOutput()
   val csr_eret = Bool(OUTPUT)
   val csr_interrupt = Bool(OUTPUT)
   val csr_xcpt = Bool(OUTPUT)
   val csr_interrupt_cause = UInt(OUTPUT, conf.xprlen)
}

class DpathIo(implicit conf: SodorConfiguration) extends Bundle() 
{
   val host  = new HTIFIO()
   val imem = new MemPortIo(conf.xprlen)
   val dmem = new MemPortIo(conf.xprlen)
   val ctl  = new CtlToDatIo().flip()
   val dat  = new DatToCtlIo()
}

class DatPath(implicit conf: SodorConfiguration) extends Module
{
   val io = new DpathIo()
   
   
   // Instruction Fetch
   val pc_next          = UInt()
   val pc_plus4         = UInt()
   val br_target        = UInt()
   val jmp_target       = UInt()
   val jump_reg_target  = UInt()
   val exception_target = UInt()
 
   // PC Register
   val pc_reg = Reg(init=UInt(START_ADDR, conf.xprlen))

   when (!io.ctl.stall) 
   {
      pc_reg := pc_next
   }

   pc_plus4 := (pc_reg + UInt(4, conf.xprlen))               

   pc_next := MuxCase(pc_plus4, Array(
                  (io.ctl.pc_sel === PC_4)   -> pc_plus4,
                  (io.ctl.pc_sel === PC_BR)  -> br_target,
                  (io.ctl.pc_sel === PC_J )  -> jmp_target,
                  (io.ctl.pc_sel === PC_JR)  -> jump_reg_target,
                  (io.ctl.pc_sel === PC_EXC) -> exception_target
                  ))
   
   io.imem.req.bits.addr := pc_reg
   val inst = Mux(io.imem.resp.valid, io.imem.resp.bits.data, BUBBLE) 
                 
   
   // Decode
   val rs1_addr = inst(RS1_MSB, RS1_LSB)
   val rs2_addr = inst(RS2_MSB, RS2_LSB)
   val wb_addr  = inst(RD_MSB,  RD_LSB)
   
   val wb_data = Bits(width = conf.xprlen)
 
   // Register File
   val regfile = Mem(Bits(width = conf.xprlen), 32)

   when (io.ctl.rf_wen && (wb_addr != UInt(0)) && !io.dat.csr_xcpt)
   {
      regfile(wb_addr) := wb_data
   }

   val rs1_data = Mux((rs1_addr != UInt(0)), regfile(rs1_addr), UInt(0, conf.xprlen))
   val rs2_data = Mux((rs2_addr != UInt(0)), regfile(rs2_addr), UInt(0, conf.xprlen))
   
   
   // immediates
   val imm_i = inst(31, 20) 
   val imm_s = Cat(inst(31, 25), inst(11,7))
   val imm_b = Cat(inst(31), inst(7), inst(30,25), inst(11,8))
   val imm_u = inst(31, 12)
   val imm_j = Cat(inst(31), inst(19,12), inst(20), inst(30,21))
   val imm_z = Cat(Fill(UInt(0), 27), inst(19,15))

   // sign-extend immediates
   val imm_i_sext = Cat(Fill(imm_i(11), 20), imm_i)
   val imm_s_sext = Cat(Fill(imm_s(11), 20), imm_s)
   val imm_b_sext = Cat(Fill(imm_b(11), 19), imm_b, UInt(0))
   val imm_u_sext = Cat(imm_u, Fill(UInt(0), 12))
   val imm_j_sext = Cat(Fill(imm_j(19), 11), imm_j, UInt(0))


   val alu_op1 = MuxCase(UInt(0), Array(
               (io.ctl.op1_sel === OP1_RS1) -> rs1_data,
               (io.ctl.op1_sel === OP1_IMU) -> imm_u_sext,
               (io.ctl.op1_sel === OP1_IMZ) -> imm_z
               )).toUInt

   val alu_op2 = MuxCase(UInt(0), Array(
               (io.ctl.op2_sel === OP2_RS2) -> rs2_data,
               (io.ctl.op2_sel === OP2_PC)  -> pc_reg,
               (io.ctl.op2_sel === OP2_IMI) -> imm_i_sext,
               (io.ctl.op2_sel === OP2_IMS) -> imm_s_sext
               )).toUInt



   // ALU
   val alu_out   = UInt(width = conf.xprlen)

   val alu_shamt = alu_op2(4,0).toUInt

   alu_out := MuxCase(UInt(0), Array(
                  (io.ctl.alu_fun === ALU_ADD)  -> (alu_op1 + alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_SUB)  -> (alu_op1 - alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_AND)  -> (alu_op1 & alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_OR)   -> (alu_op1 | alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_XOR)  -> (alu_op1 ^ alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_SLT)  -> (alu_op1.toSInt < alu_op2.toSInt).toUInt,
                  (io.ctl.alu_fun === ALU_SLTU) -> (alu_op1 < alu_op2).toUInt,
                  (io.ctl.alu_fun === ALU_SLL)  -> ((alu_op1 << alu_shamt)(conf.xprlen-1, 0)).toUInt,
                  (io.ctl.alu_fun === ALU_SRA)  -> (alu_op1.toSInt >> alu_shamt).toUInt,
                  (io.ctl.alu_fun === ALU_SRL)  -> (alu_op1 >> alu_shamt).toUInt,
                  (io.ctl.alu_fun === ALU_COPY1)-> alu_op1
                  ))

   // Branch/Jump Target Calculation
   br_target       := pc_reg + imm_b_sext
   jmp_target      := pc_reg + imm_j_sext
   jump_reg_target := (rs1_data.toUInt + imm_i_sext.toUInt)

   // Control Status Registers
   val csr = Module(new CSRFile())
   csr.io.host <> io.host
//   csr.io <> io.dat.csr
   csr.io.rw.addr  := inst(CSR_ADDR_MSB,CSR_ADDR_LSB)
   csr.io.rw.cmd   := io.ctl.csr_cmd
   csr.io.rw.wdata := alu_out

   csr.io.retire    := !io.ctl.stall
   csr.io.exception := io.ctl.exception
//   io.dat.status    := csr.io.status
   csr.io.cause     := io.ctl.exc_cause
   csr.io.pc        := pc_reg
   exception_target := csr.io.evec

   io.dat.csr_eret := csr.io.eret
   io.dat.csr_xcpt := csr.io.csr_xcpt
   io.dat.csr_interrupt := csr.io.interrupt
   io.dat.csr_interrupt_cause := csr.io.interrupt_cause
   // TODO replay? stall?

   // Add your own uarch counters here!
   csr.io.uarch_counters.foreach(_ := Bool(false))

   // WB Mux
   wb_data := MuxCase(alu_out, Array(
                  (io.ctl.wb_sel === WB_ALU) -> alu_out,
                  (io.ctl.wb_sel === WB_MEM) -> io.dmem.resp.bits.data, 
                  (io.ctl.wb_sel === WB_PC4) -> pc_plus4,
                  (io.ctl.wb_sel === WB_CSR) -> csr.io.rw.rdata
                  )).toSInt()
                                  

   // datapath to controlpath outputs
   io.dat.inst   := inst
   io.dat.br_eq  := (rs1_data === rs2_data)
   io.dat.br_lt  := (rs1_data.toSInt < rs2_data.toSInt) 
   io.dat.br_ltu := (rs1_data.toUInt < rs2_data.toUInt)
   
   
   // datapath to data memory outputs
   io.dmem.req.bits.addr  := alu_out
   io.dmem.req.bits.data := rs2_data.toUInt 
   
   
   // Printout
   // pass output through the spike-dasm binary (found in riscv-tools) to turn
   // the DASM(%x) into a disassembly string.
   printf("Cyc= %d Op1=[0x%x] Op2=[0x%x] W[%s,%d= 0x%x] %s Mem[%s %d: 0x%x] PC= 0x%x %s%s DASM(%x)\n"
      , csr.io.time(31,0)
      , alu_op1
      , alu_op2
      , Mux(io.ctl.rf_wen, Str("W"), Str("_"))
      , wb_addr
      , wb_data
      , Mux(io.ctl.exception, Str("EXC"), Str("   "))
      , Mux(io.ctl.debug_dmem_val, Str("V"), Str("_"))
      , io.ctl.debug_dmem_typ
      , io.dmem.resp.bits.data
      , pc_reg
      , Mux(io.ctl.stall, Str("stall"), Str("     "))
      , Mux(io.ctl.pc_sel  === UInt(1), Str("BR"),
         Mux(io.ctl.pc_sel === UInt(2), Str("J "),
         Mux(io.ctl.pc_sel === UInt(3), Str("JR"),
         Mux(io.ctl.pc_sel === UInt(4), Str("EX"),
         Mux(io.ctl.pc_sel === UInt(0), Str("  "), Str("??"))))))
      , inst
      )
 
   if (PRINT_COMMIT_LOG)
   {
      when (!io.ctl.stall)
      {
         // use "sed" to parse out "@@@" from the other printf code above.
         val rd = inst(RD_MSB,RD_LSB)
         when (io.ctl.rf_wen && rd != UInt(0))
         {
            printf("@@@ 0x%x (0x%x) x%d 0x%x\n", pc_reg, inst, rd, Cat(Fill(wb_data(31),32),wb_data))
         }
         .otherwise
         {
            printf("@@@ 0x%x (0x%x)\n", pc_reg, inst)
         }
      }
   }   
}

 
}
