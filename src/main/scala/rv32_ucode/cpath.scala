//**************************************************************************
// RISCV U-Coded Processor Control Path
//--------------------------------------------------------------------------
//
// Christopher Celio
// 2011 May 28

package sodor.ucode

import chisel3._
import chisel3.util._

import freechips.rocketchip.rocket.CSR

import sodor.common._
import sodor.common.Instructions._
import Constants._
import scala.collection.mutable.ArrayBuffer


class CtlToDatIo extends Bundle()
{
   val csr_cmd = Output(UInt(CSR.SZ.W))
   val ld_ir   = Output(Bool())
   val reg_sel = Output(UInt(RS_X.getWidth.W))
   val reg_wr  = Output(Bool())
   val en_reg  = Output(Bool())
   val ld_a    = Output(Bool())
   val ld_b    = Output(Bool())
   val alu_op  = Output(UInt(ALU_X.getWidth.W))
   val en_alu  = Output(Bool())
   val ld_ma   = Output(Bool())
   val mem_wr  = Output(Bool())
   val en_mem  = Output(Bool())
   val msk_sel = Output(UInt(MSK_SZ))
   val is_sel  = Output(UInt(IS_X.getWidth.W))
   val en_imm  = Output(Bool())
   val upc     = Output(UInt()) // for debugging purposes
   val upc_is_fetch = Output(Bool()) // for debugging purposes
   val illegal_exception = Output(Bool())
   val exception = Output(Bool())
   val retire = Output(Bool())
}

class CpathIo(implicit val conf: SodorCoreParams) extends Bundle()
{
   val dcpath = Flipped(new DebugCPath())
   val mem  = new MemPortIo(conf.xprlen)
   val dat  = Flipped(new DatToCtlIo())
   val ctl  = new CtlToDatIo()
 }

class CtlPath(implicit val conf: SodorCoreParams) extends Module
{
  val io = IO(new CpathIo())
  io := DontCare

   // Compile the Micro-code down into a ROM
  val (label_target_map, label_sz) = MicrocodeCompiler.constructLabelTargetMap(Microcode.codes)
  val rombits                      = MicrocodeCompiler.emitRomBits(Microcode.codes, label_target_map, label_sz)
  val opcode_dispatch_table        = MicrocodeCompiler.generateDispatchTable(label_target_map)


   // Macro Instruction Opcode Dispatch Table
   val upc_opgroup_target = Lookup (io.dat.inst, label_target_map("ILLEGAL").asUInt(label_sz.W),
                                                    opcode_dispatch_table)

   // Micro-PC State Register
   val upc_state_next = Wire(UInt())
   val upc_state = RegNext(upc_state_next, init = label_target_map("FETCH").asUInt(label_sz.W))

   // Micro-code ROM
   val micro_code = VecInit(rombits)
   val uop = micro_code(upc_state)

   // Extract Control Signals from UOP
  val cs = uop.asTypeOf(new Bundle()
  {
     val csr_cmd        = UInt(CSR.SZ.W)
     val ld_ir          = Bool()
     val reg_sel        = UInt(RS_X.getWidth.W)
     val reg_wr         = Bool()
     val en_reg         = Bool()
     val ld_a           = Bool()
     val ld_b           = Bool()
     val alu_op         = UInt(ALU_X.getWidth.W)
     val en_alu         = Bool()
     val ld_ma          = Bool()
     val mem_wr         = Bool()
     val en_mem         = Bool()
     val msk_sel        = UInt(MSK_SZ)
     val is_sel         = UInt(IS_X.getWidth.W)
     val en_imm         = Bool()
     val ubr            = UInt(UBR_N.getWidth.W)
     val upc_rom_target = UInt(label_sz.W)
//     override def clone = this.asInstanceOf[this.type]
  })
  require(label_sz == 8, "Label size must be 8")

  val mem_is_busy = !io.mem.resp.valid && (cs.en_mem || cs.mem_wr)

  val interrupt_trigger = io.dat.interrupt && io.ctl.upc_is_fetch
  val non_illegal_trap = interrupt_trigger || io.dat.addr_exception

   // Micro-PC State Logic
  val upc_sel     = MuxCase(UPC_CURRENT, Array(
                      (cs.ubr === UBR_N) -> UPC_NEXT,
                      (cs.ubr === UBR_D) -> UPC_DISPATCH,
                      (cs.ubr === UBR_J) -> UPC_ABSOLUTE,
                      (cs.ubr === UBR_EZ)-> Mux ( io.dat.alu_zero, UPC_ABSOLUTE , UPC_NEXT),
                      (cs.ubr === UBR_NZ)-> Mux (~io.dat.alu_zero, UPC_ABSOLUTE , UPC_NEXT),
                      (cs.ubr === UBR_S) -> Mux (mem_is_busy     , UPC_CURRENT  , UPC_NEXT)
                    ))


   upc_state_next := MuxCase(upc_state, Array(
                      (non_illegal_trap)         -> label_target_map("ILLEGAL").asUInt(label_sz.W),
                      (upc_sel === UPC_DISPATCH) -> upc_opgroup_target,
                      (upc_sel === UPC_ABSOLUTE) -> cs.upc_rom_target,
                      (upc_sel === UPC_NEXT)     -> (upc_state + 1.U),
		                (upc_sel === UPC_CURRENT)  -> upc_state
                    ))


   // Exception Handling ---------------------
   io.ctl.illegal_exception := label_target_map("ILLEGAL").U === upc_state && RegNext(cs.ubr) === UBR_D
   io.ctl.exception := io.ctl.illegal_exception || io.dat.addr_exception

   // Cpath Control Interface
   io.ctl.msk_sel := cs.msk_sel
   io.ctl.ld_ir   := cs.ld_ir
   io.ctl.reg_sel := cs.reg_sel
   io.ctl.reg_wr  := cs.reg_wr
   io.ctl.en_reg  := cs.en_reg
   io.ctl.ld_a    := cs.ld_a
   io.ctl.ld_b    := cs.ld_b
   io.ctl.alu_op  := cs.alu_op
   io.ctl.en_alu  := cs.en_alu
   io.ctl.ld_ma   := cs.ld_ma
   io.ctl.mem_wr  := cs.mem_wr
   io.ctl.en_mem  := cs.en_mem
   io.ctl.is_sel  := cs.is_sel
   io.ctl.en_imm  := cs.en_imm

   // convert CSR instructions with raddr1 == 0 to read-only CSR commands
   val rs1_addr = io.dat.inst(RS1_MSB, RS1_LSB)
   val csr_ren = (cs.csr_cmd === CSR.S || cs.csr_cmd === CSR.C) && rs1_addr === 0.U
   val csr_cmd1 = Mux(csr_ren, CSR.R, cs.csr_cmd)
   io.ctl.csr_cmd := csr_cmd1

   io.ctl.upc := upc_state
   io.ctl.upc_is_fetch := (upc_state === label_target_map("FETCH").U)

   // track whether current instruction caused an exception
   val en_retire = RegInit(false.B)
   when (io.ctl.upc_is_fetch) {
      en_retire := true.B
   }
   when (io.ctl.exception) {
      en_retire := false.B
   }
   io.ctl.retire := io.ctl.upc_is_fetch && en_retire

   // Memory Interface
   io.mem.req.bits.fcn := Mux(cs.mem_wr , M_XWR, M_XRD)
   io.mem.req.bits.typ := cs.msk_sel
   io.mem.req.valid    := (cs.en_mem || cs.mem_wr) && !io.dat.addr_exception

}
