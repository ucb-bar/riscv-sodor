//**************************************************************************
// RISCV U-Coded Processor Data Path
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 May 28

package sodor.ucode

import chisel3._
import chisel3.util._

import freechips.rocketchip.config.Parameters
import freechips.rocketchip.rocket.CSRFile
import freechips.rocketchip.tile.CoreInterrupts

import Constants._
import sodor.common._

class DatToCtlIo extends Bundle()
{
   val inst     = Output(UInt(32.W))
   val alu_zero = Output(Bool())
   val csr_eret = Output(Bool())
   val interrupt = Output(Bool())
   val addr_exception = Output(Bool())
}


class DpathIo(implicit val p: Parameters, val conf: SodorCoreParams) extends Bundle()
{
   val ddpath = Flipped(new DebugDPath())
   val mem  = new MemPortIo(conf.xprlen)
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


   // forward declarations
   val imm       = Wire(UInt(conf.xprlen.W))
   val alu       = Wire(UInt(conf.xprlen.W))
   val reg_rdata = Wire(UInt(conf.xprlen.W))
   val csr_rdata = Wire(UInt(conf.xprlen.W))
   val exception_target = Wire(UInt(conf.xprlen.W))

   // The Bus
   // (this is a bus-based RISCV implementation, so all data movement goes
   // across this wire)
   val bus = MuxCase(0.U, Array(
               (io.ctl.en_imm)                  -> imm(conf.xprlen-1,0),
               (io.ctl.en_alu)                  -> alu(conf.xprlen-1,0),
               (io.ctl.en_reg &
                 (io.ctl.reg_sel =/= RS_CR))    -> reg_rdata(conf.xprlen-1,0),
               (io.ctl.en_mem)                  -> io.mem.resp.bits.data(conf.xprlen-1,0),
               (io.ctl.en_reg &
                  (io.ctl.reg_sel === RS_CR))   -> csr_rdata
             ))

   assert(PopCount(Seq(io.ctl.en_imm, io.ctl.en_alu, io.ctl.en_reg, io.ctl.en_mem)) <= 1.U,
     "Error. Multiple components attempting to write to bus simultaneously")


   // IR Register
   val ir    = RegInit(0.asUInt(conf.xprlen.W))
   when (io.ctl.ld_ir) { ir := bus }
   io.dat.inst := ir

   // A Register
   val reg_a = RegInit("haaaa".asUInt(conf.xprlen.W))
   when (io.ctl.ld_a) { reg_a := bus }

   // B Register
   val reg_b = RegInit("hbbbb".asUInt(conf.xprlen.W))
   when (io.ctl.ld_b) { reg_b := bus }

   // MA Register
   val reg_ma  = RegInit("heeee".asUInt(conf.xprlen.W))
   when (io.ctl.ld_ma) { reg_ma := bus }

   // IR Immediate
   imm := MuxCase(0.U, Array(
             (io.ctl.is_sel === IS_I)  -> Cat(Fill(20,ir(31)),ir(31,20)),
             (io.ctl.is_sel === IS_S)  -> Cat(Fill(20,ir(31)),ir(31,25),ir(11,7)),
             (io.ctl.is_sel === IS_U)  -> Cat(ir(31,12),0.S(12.W)),
             (io.ctl.is_sel === IS_B)  -> Cat(Fill(20,ir(31)),ir(7),ir(30,25),ir(11,8),0.asUInt(1.W)),
             (io.ctl.is_sel === IS_J)  -> Cat(Fill(20,ir(31)),ir(19,12),ir(20),ir(30,21),0.asUInt(1.W)),
             (io.ctl.is_sel === IS_Z)  -> Cat(0.asUInt(27.W), ir(19,15))
           ))




  // Exception
  val tval_data_ma = Wire(UInt(conf.xprlen.W))
  val tval_inst_ma = Wire(UInt(conf.xprlen.W))
  val inst_misaligned = Wire(Bool())
  val data_misaligned = Wire(Bool())
  val mem_store = Wire(Bool())
  io.dat.addr_exception := inst_misaligned || data_misaligned

   // Register File (Single Port)
   // also holds the PC register
   val rs1 = ir(RS1_MSB, RS1_LSB)
   val rs2 = ir(RS2_MSB, RS2_LSB)
   val rd  = ir(RD_MSB,  RD_LSB)

   val reg_addr  = MuxCase(0.U, Array(
                     (io.ctl.reg_sel === RS_PC)  -> PC_IDX,
                     (io.ctl.reg_sel === RS_RD)  -> rd,
                     (io.ctl.reg_sel === RS_RS1) -> rs1,
                     (io.ctl.reg_sel === RS_RS2) -> rs2,
                     (io.ctl.reg_sel === RS_X0)  -> X0,
                     (io.ctl.reg_sel === RS_CA)  -> X0,
                     (io.ctl.reg_sel === RS_CR)  -> X0
                   ))

   //note: I could be far more clever and save myself on wasted registers here...
   //32 x-registers, 1 pc-register
   val regfile = Reg(Vec(33, UInt(32.W)))
   when (reset.asBool) {
     regfile(PC_IDX) := io.reset_vector
   }



   inst_misaligned := false.B
   tval_inst_ma := RegNext(bus & ~1.U(conf.xprlen.W))
   when (io.ctl.reg_wr & reg_addr =/= 0.U)
   {
      when (reg_addr === PC_IDX)
      {
        // Check bit 1 of the address for misalignment
        inst_misaligned := bus(1)
        // Clear LSB of the write data if we are writing to PC (required by JALR, but doesn't hurt doing this for all req)
        regfile(reg_addr) := bus & ~1.U(conf.xprlen.W)
      }
      .elsewhen (!io.ctl.exception)
      {
        regfile(reg_addr) := bus
      }
   }

   // This is a hack to make it look like the CSRFile is part of the regfile
   reg_rdata :=  MuxCase(regfile(reg_addr), Array(
                    (io.ctl.reg_sel === RS_CR) -> csr_rdata,
                    (reg_addr === 0.U)     -> 0.asUInt(conf.xprlen.W)))

   // CSR addr Register
   val csr_addr = RegInit(0.asUInt(12.W))
   when(io.ctl.reg_wr & (io.ctl.reg_sel === RS_CA)) {
     csr_addr := bus
   }

   val csr_wdata = RegInit(0.asUInt(conf.xprlen.W))
   when(io.ctl.reg_wr & (io.ctl.reg_sel === RS_CR)) {
     csr_wdata := bus
   }

   // Control Status Registers
   val csr = Module(new CSRFile(perfEventSets=CSREvents.events))
   csr.io := DontCare
   csr.io.decode(0).inst  := csr_addr << 20
   csr.io.rw.addr  := csr_addr
   csr.io.rw.wdata := csr_wdata
   csr.io.rw.cmd   := io.ctl.csr_cmd
   csr_rdata       := csr.io.rw.rdata
   csr.io.retire    := io.ctl.retire
   csr.io.pc        := regfile(PC_IDX) - 4.U
   exception_target := csr.io.evec

   csr.io.interrupts := io.interrupt
   csr.io.hartid := io.hartid

   io.dat.csr_eret := csr.io.eret

   val interrupt_handled = RegInit(false.B)
   when (io.ctl.retire) { interrupt_handled := csr.io.interrupt }
   val interrupt_edge = csr.io.interrupt && ! interrupt_handled
   io.dat.interrupt := interrupt_edge

  // Delay exception for CSR to avoid combinational loop
  // If there is an exception, we will enter ILLEGAL state in the next cycle
  val delayed_exception = io.ctl.illegal_exception || RegNext(io.dat.addr_exception)
  val exception_cause = Mux(io.ctl.illegal_exception,   Causes.illegal_instruction.U,
                        Mux(RegNext(inst_misaligned),   Causes.misaligned_fetch.U,
                        Mux(RegNext(mem_store),         Causes.misaligned_store.U,
                                                        Causes.misaligned_load.U
                        )))
  csr.io.exception := delayed_exception
  csr.io.cause := Mux(delayed_exception, exception_cause, csr.io.interrupt_cause)
  csr.io.tval := MuxCase(0.U, Array(
              (exception_cause === Causes.illegal_instruction.U)  -> io.dat.inst,
              (exception_cause === Causes.misaligned_fetch.U)     -> tval_inst_ma,
              (exception_cause === Causes.misaligned_store.U)     -> tval_data_ma,
              (exception_cause === Causes.misaligned_load.U)      -> tval_data_ma,
              ))
  csr.io.ungated_clock := clock

   // Add your own uarch counters here!
   csr.io.counters.foreach(_.inc := false.B)

   // ALU
   val alu_shamt = reg_b(4,0).asUInt()

   alu := MuxCase(0.U, Array[(Bool, UInt)](
              (io.ctl.alu_op === ALU_COPY_A)  ->  reg_a,
              (io.ctl.alu_op === ALU_COPY_B)  ->  reg_b,
              (io.ctl.alu_op === ALU_INC_A_1) ->  (reg_a  +  1.U),
              (io.ctl.alu_op === ALU_DEC_A_1) ->  (reg_a  -  1.U),
              (io.ctl.alu_op === ALU_INC_A_4) ->  (reg_a  +  4.U),
              (io.ctl.alu_op === ALU_DEC_A_4) ->  (reg_a  -  4.U),
              (io.ctl.alu_op === ALU_ADD)     ->  (reg_a  +  reg_b),
              (io.ctl.alu_op === ALU_SUB)     ->  (reg_a  -  reg_b),
              (io.ctl.alu_op === ALU_SLL)     -> ((reg_a << alu_shamt)(conf.xprlen-1,0)),
              (io.ctl.alu_op === ALU_SRL)     ->  (reg_a >> alu_shamt),
              (io.ctl.alu_op === ALU_SRA)     ->  (reg_a.asSInt() >> alu_shamt).asUInt(),
              (io.ctl.alu_op === ALU_AND)     ->  (reg_a & reg_b),
              (io.ctl.alu_op === ALU_OR)      ->  (reg_a | reg_b),
              (io.ctl.alu_op === ALU_XOR)     ->  (reg_a ^ reg_b),
              (io.ctl.alu_op === ALU_SLT)     ->  (reg_a.asSInt() < reg_b.asSInt()).asUInt(),
              (io.ctl.alu_op === ALU_SLTU)    ->  (reg_a < reg_b),
              (io.ctl.alu_op === ALU_MASK_12) ->  (reg_a & ~((1<<12)-1).asUInt(conf.xprlen.W)),
              (io.ctl.alu_op === ALU_EVEC)    ->  exception_target
            ))

   // Output Signals to the Control Path
   io.dat.alu_zero := (alu === 0.U)

   // Output Signals to the Memory
   io.mem.req.bits.addr := reg_ma.asUInt()
   io.mem.req.bits.data := bus

   // Data misalignment detection
   // For example, if type is 3 (word), the mask is ~(0b111 << (3 - 1)) = ~0b100 = 0b011.
   val misaligned_mask = Wire(UInt(3.W))
   misaligned_mask := ~(7.U(3.W) << (io.ctl.msk_sel - 1.U)(1, 0))
   data_misaligned := (misaligned_mask & reg_ma.asUInt.apply(2, 0)).orR && (io.ctl.en_mem || io.ctl.mem_wr)
   mem_store := io.ctl.mem_wr
   tval_data_ma := RegNext(reg_ma.asUInt)

   // Printout
   printf("Cyc= %d [%d] PCReg=[%x] uPC=[%x] Bus=[%x] RegSel=[%d] RegAddr=[%d] A=[%x] B=[%x] MA=[%x] InstReg=[%x] %c%c%c DASM(%x)\n",
      csr.io.time(31,0),
      csr.io.retire,
      regfile(PC_IDX),
      io.ctl.upc,
      bus,
      io.ctl.reg_sel,
      reg_addr,
      reg_a,
      reg_b,
      reg_ma,
      ir,
      Mux(io.ctl.upc_is_fetch, Str("F"), Str(" ")),
      Mux(io.ctl.en_mem, Str("M"), Str(" ")),
      Mux(io.ctl.exception, Str("X"), Str(" ")),
      ir)

}
