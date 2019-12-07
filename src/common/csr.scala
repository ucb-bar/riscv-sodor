// See LICENSE for license details.

// TODO: add timeh, cycleh, counth, instreh counters for the full RV32I experience.
// NOTE: This is mostly a copy from the Berkeley Rocket-chip csr file. It is
//       overkill for a small, embedded processor.

package Common


import chisel3._
import collection.mutable.LinkedHashMap
import chisel3.util._
import Util._
import Instructions._


import Common.Constants._
import scala.math._

class MStatus extends Bundle {
    // not truly part of mstatus, but convenient
  val debug = Bool()
  val prv = UInt(PRV.SZ.W) // not truly part of mstatus, but convenient
  val sd = Bool()
  val zero1 = UInt(8.W)
  val tsr = Bool()
  val tw = Bool()
  val tvm = Bool()
  val mxr = Bool()
  val sum = Bool()
  val mprv = Bool()
  val xs = UInt(2.W)
  val fs = UInt(2.W)
  val mpp = UInt(2.W)
  val hpp = UInt(2.W)
  val spp = UInt(1.W)
  val mpie = Bool()
  val hpie = Bool()
  val spie = Bool()
  val upie = Bool()
  val mie = Bool()
  val hie = Bool()
  val sie = Bool()
  val uie = Bool()
}

class DCSR extends Bundle {
  val xdebugver = UInt(2.W)
  val zero4 = UInt(2.W)
  val zero3 = UInt(12.W)
  val ebreakm = Bool()
  val ebreakh = Bool()
  val ebreaks = Bool()
  val ebreaku = Bool()
  val zero2 = Bool()
  val stopcycle = Bool()
  val stoptime = Bool()
  val cause = UInt(3.W)
  // TODO: debugint is not in the Debug Spec v13
  val debugint = Bool()
  val zero1 = UInt(2.W)
  val step = Bool()
  val prv = UInt(2.W)
}

object PRV
{
  val SZ = 2
  val U = 0
  val S = 1
  val H = 2
  val M = 3
}

class MIP extends Bundle {
  val zero2 = Bool()
  val debug = Bool() // keep in sync with CSR.debugIntCause
  val zero1 = Bool()
  val rocc = Bool()
  val meip = Bool()
  val heip = Bool()
  val seip = Bool()
  val ueip = Bool()
  val mtip = Bool()
  val htip = Bool()
  val stip = Bool()
  val utip = Bool()
  val msip = Bool()
  val hsip = Bool()
  val ssip = Bool()
  val usip = Bool()
}

class PerfCounterIO(implicit val conf: SodorConfiguration) extends Bundle{
  //val eventSel = Output(UInt(conf.xprlen.W))
  val inc = Input(UInt(conf.xprlen.W))
  override def cloneType = { new PerfCounterIO().asInstanceOf[this.type] }
}

object CSR
{
  // commands
  val SZ = 3.W
  val X = 0.asUInt(SZ)
  val N = 0.asUInt(SZ)
  val W = 1.asUInt(SZ)
  val S = 2.asUInt(SZ)
  val C = 3.asUInt(SZ)
  val I = 4.asUInt(SZ)
  val R = 5.asUInt(SZ)

  val ADDRSZ = 12
  val firstCtr = CSRs.cycle
  val firstCtrH = CSRs.cycleh
  val firstHPC = CSRs.hpmcounter3
  val firstHPCH = CSRs.hpmcounter3h
  //val firstHPE = CSRs.mhpmevent3
  val firstMHPC = CSRs.mhpmcounter3
  val firstMHPCH = CSRs.mhpmcounter3h
  val firstHPM = 3
  val nCtr = 32
  val nHPM = nCtr - firstHPM
  val hpmWidth = 40
}

class CSRFileIO(implicit val conf: SodorConfiguration) extends Bundle {
  val hartid = Input(UInt(conf.xprlen.W))
  val rw = new Bundle {
    val cmd = Input(UInt(CSR.SZ))
    val rdata = Output(UInt(conf.xprlen.W))
    val wdata = Input(UInt(conf.xprlen.W))
  }

  val csr_stall = Output(Bool())
  val eret = Output(Bool())
  val singleStep = Output(Bool())

  val decode = new Bundle {
    val csr = Input(UInt(CSR.ADDRSZ.W))
    val read_illegal = Output(Bool())
    val write_illegal = Output(Bool())
    val system_illegal = Output(Bool())
  }

  val status = Output(new MStatus())
  val evec = Output(UInt(conf.xprlen.W))
  val exception = Input(Bool())
  val retire = Input(Bool())
  val pc = Input(UInt(conf.xprlen.W))
  val time = Output(UInt(conf.xprlen.W))
  val counters = Vec(60, new PerfCounterIO)

}

class CSRFile(implicit val conf: SodorConfiguration) extends Module
{
  val io = IO(new CSRFileIO)
  io := DontCare

  val reset_mstatus = WireInit(0.U.asTypeOf(new MStatus()))
  reset_mstatus.mpp := PRV.M
  reset_mstatus.prv := PRV.M
  val reg_mstatus = RegInit(reset_mstatus)
  val reg_mepc = Reg(UInt(conf.xprlen.W))
  val reg_mcause = Reg(UInt(conf.xprlen.W))
  val reg_mtval = Reg(UInt(conf.xprlen.W))
  val reg_mscratch = Reg(UInt(conf.xprlen.W))
  val reg_mtimecmp = Reg(UInt(conf.xprlen.W))
  val reg_medeleg = Reg(UInt(conf.xprlen.W))

  val reg_mip = RegInit(0.U.asTypeOf(new MIP()))
  val reg_mie = RegInit(0.U.asTypeOf(new MIP()))
  val reg_wfi = RegInit(false.B)
  val reg_mtvec = Reg(UInt(conf.xprlen.W))

  val reg_time = WideCounter(64)
  val reg_instret = WideCounter(64, io.retire)

  val reg_mcounteren = Reg(UInt(32.W))
  //val reg_hpmevent = io.counters.map(c => Reg(init = 0.asUInt(conf.xprlen.W)))
  //(io.counters zip reg_hpmevent) foreach { case (c, e) => c.eventSel := e }
  val reg_hpmcounter = io.counters.map(c => WideCounter(CSR.hpmWidth, c.inc, reset = false))

  val new_prv = WireInit(reg_mstatus.prv)
  reg_mstatus.prv := new_prv

  val reg_debug = RegInit(false.B)
  val reg_dpc = Reg(UInt(conf.xprlen.W))
  val reg_dscratch = Reg(UInt(conf.xprlen.W))
  val reg_singleStepped = Reg(Bool())
  val reset_dcsr = WireInit(0.U.asTypeOf(new DCSR()))
  reset_dcsr.xdebugver := 1
  reset_dcsr.prv := PRV.M
  val reg_dcsr = RegInit(reset_dcsr)

  val system_insn = io.rw.cmd === CSR.I
  val cpu_ren = io.rw.cmd =/= CSR.N && !system_insn

  val read_mstatus = io.status.asUInt()
  val isa_string = "I"
  val misa = BigInt(0) | isa_string.map(x => 1 << (x - 'A')).reduce(_|_)
  val impid = 0x8000 // indicates an anonymous source, which can be used
                     // during development before a Source ID is allocated.

  val read_mapping = collection.mutable.LinkedHashMap[Int,Bits](
    CSRs.mcycle -> reg_time,
    CSRs.minstret -> reg_instret,
    CSRs.mimpid -> 0.U,
    CSRs.marchid -> 0.U,
    CSRs.mvendorid -> 0.U,
    CSRs.misa -> misa.U,
    CSRs.mimpid -> impid.U,
    CSRs.mstatus -> read_mstatus,
    CSRs.mtvec -> MTVEC.U,
    CSRs.mip -> reg_mip.asUInt(),
    CSRs.mie -> reg_mie.asUInt(),
    CSRs.mscratch -> reg_mscratch,
    CSRs.mepc -> reg_mepc,
    CSRs.mtval -> reg_mtval,
    CSRs.mcause -> reg_mcause,
    CSRs.mhartid -> io.hartid,
    CSRs.dcsr -> reg_dcsr.asUInt,
    CSRs.dpc -> reg_dpc,
    CSRs.dscratch -> reg_dscratch,
    CSRs.medeleg -> reg_medeleg)

  for (i <- 0 until CSR.nCtr)
  {
    read_mapping += (i + CSR.firstMHPC) -> reg_hpmcounter(i)
    read_mapping += (i + CSR.firstMHPCH) -> reg_hpmcounter(i)
  }

/*  for (((e, c), i) <- (reg_hpmevent.padTo(CSR.nHPM, 0.U)
                       zip reg_hpmcounter.map(x => x: UInt).padTo(CSR.nHPM, 0.U)) zipWithIndex) {
    read_mapping += (i + CSR.firstHPE) -> e // mhpmeventN
    read_mapping += (i + CSR.firstMHPC) -> c // mhpmcounterN
    if (conf.usingUser) read_mapping += (i + CSR.firstHPC) -> c // hpmcounterN
    if (conf.xprlen == 32) {
      read_mapping += (i + CSR.firstMHPCH) -> c // mhpmcounterNh
      if (conf.usingUser) read_mapping += (i + CSR.firstHPCH) -> c // hpmcounterNh
    }
  }
*/
  if (conf.usingUser) {
    read_mapping += CSRs.mcounteren -> reg_mcounteren
    read_mapping += CSRs.cycle -> reg_time
    read_mapping += CSRs.instret -> reg_instret
  }

  if (conf.xprlen == 32) {
    read_mapping += CSRs.mcycleh -> 0.U //(reg_time >> 32)
    read_mapping += CSRs.minstreth -> 0.U //(reg_instret >> 32)
    if (conf.usingUser) {
      read_mapping += CSRs.cycleh -> 0.U //(reg_time >> 32)
      read_mapping += CSRs.instreth -> 0.U //(reg_instret >> 32)
    }
  }

  val decoded_addr = read_mapping map { case (k, v) => k -> (io.decode.csr === k) }

  val priv_sufficient = reg_mstatus.prv >= io.decode.csr(9,8)
  val read_only = io.decode.csr(11,10).andR
  val cpu_wen = cpu_ren && io.rw.cmd =/= CSR.R && priv_sufficient
  val wen = cpu_wen && !read_only
  val wdata = readModifyWriteCSR(io.rw.cmd, io.rw.rdata, io.rw.wdata)

  val opcode = 1.U << io.decode.csr(2,0)
  val insn_call = system_insn && opcode(0)
  val insn_break = system_insn && opcode(1)
  val insn_ret = system_insn && opcode(2) && priv_sufficient
  val insn_wfi = system_insn && opcode(5) && priv_sufficient

  private def decodeAny(m: LinkedHashMap[Int,Bits]): Bool = m.map { case(k: Int, _: Bits) => io.decode.csr === k }.reduce(_||_)
  io.decode.read_illegal := reg_mstatus.prv < io.decode.csr(9,8) || !decodeAny(read_mapping) ||
    (io.decode.csr.inRange(CSR.firstCtr, CSR.firstCtr + CSR.nCtr) || io.decode.csr.inRange(CSR.firstCtrH, CSR.firstCtrH + CSR.nCtr)) ||
    !reg_debug
  io.decode.write_illegal := io.decode.csr(11,10).andR
  io.decode.system_illegal := reg_mstatus.prv < io.decode.csr(9,8)

  io.status := reg_mstatus

  io.eret := insn_call || insn_break || insn_ret

  // ILLEGAL INSTR
  // TODO: Support misaligned address exceptions
  when (io.exception) {
    reg_mcause := Causes.illegal_instruction
  }

  assert(PopCount(insn_ret :: io.exception :: Nil) <= 1, "these conditions must be mutually exclusive")

   when (reg_time >= reg_mtimecmp) {
      reg_mip.mtip := true
   }

  // io.evec must be held stable for more than one cycle for the
  // microcoded code to correctly redirect the PC on exceptions
  io.evec := 0x80000004L.U

  //DRET
  when(insn_ret && io.decode.csr(10)){
    new_prv := reg_dcsr.prv
    reg_debug := false
    io.evec := reg_dpc
  }

  //MRET
  when (insn_ret && !io.decode.csr(10)) {
    reg_mstatus.mie := reg_mstatus.mpie
    reg_mstatus.mpie := true
    new_prv := reg_mstatus.mpp
    io.evec := reg_mepc
  }

  //ECALL
  when(insn_call){
    reg_mcause := reg_mstatus.prv + Causes.user_ecall
  }

  //EBREAK
  when(insn_break){
    reg_mcause := Causes.breakpoint
  }

  when (io.exception || insn_call || insn_break) {
    reg_mepc := io.pc
  }

  io.time := reg_time
  io.csr_stall := reg_wfi


  io.rw.rdata := Mux1H(for ((k, v) <- read_mapping) yield decoded_addr(k) -> v)

  when (wen) {

    when (decoded_addr(CSRs.dcsr)) {
        val new_dcsr = wdata.asTypeOf(new DCSR())
        reg_dcsr.step := new_dcsr.step
        reg_dcsr.ebreakm := new_dcsr.ebreakm
        if (conf.usingUser) reg_dcsr.ebreaku := new_dcsr.ebreaku
      }

    when (decoded_addr(CSRs.mstatus)) {
      val new_mstatus = wdata.asTypeOf(new MStatus())
      reg_mstatus.mie := new_mstatus.mie
      reg_mstatus.mpie := new_mstatus.mpie
    }
    when (decoded_addr(CSRs.mip)) {
      val new_mip = wdata.asTypeOf(new MIP())
      reg_mip.msip := new_mip.msip
    }
    when (decoded_addr(CSRs.mie)) {
      val new_mie = wdata.asTypeOf(new MIP())
      reg_mie.msip := new_mie.msip
      reg_mie.mtip := new_mie.mtip
    }
    for (i <- 0 until CSR.nCtr)
    {
      writeCounter(i + CSR.firstMHPC, reg_hpmcounter(i), wdata)
    }
/*    for (((e, c), i) <- (reg_hpmevent zip reg_hpmcounter) zipWithIndex) {
      writeCounter(i + CSR.firstMHPC, c, wdata)
      //when (decoded_addr(i + CSR.firstHPE)) { e := perfEventSets.maskEventSelector(wdata) }
    }*/
    writeCounter(CSRs.mcycle, reg_time, wdata)
    writeCounter(CSRs.minstret, reg_instret, wdata)

    when (decoded_addr(CSRs.dpc))      { reg_dpc := wdata }
    when (decoded_addr(CSRs.dscratch)) { reg_dscratch := wdata }

    when (decoded_addr(CSRs.mepc))     { reg_mepc := (wdata(conf.xprlen-1,0) >> 2.U) << 2.U }
    when (decoded_addr(CSRs.mscratch)) { reg_mscratch := wdata }
    when (decoded_addr(CSRs.mcause))   { reg_mcause := wdata & ((BigInt(1) << (conf.xprlen-1)) + 31).U /* only implement 5 LSBs and MSB */ }
    when (decoded_addr(CSRs.mtval))    { reg_mtval := wdata(conf.xprlen-1,0) }
    when (decoded_addr(CSRs.medeleg))    { reg_medeleg := wdata(conf.xprlen-1,0) }

    if(conf.usingUser){
      when (decoded_addr(CSRs.cycleh))   { reg_time := wdata }
      when (decoded_addr(CSRs.instreth)) { reg_instret := wdata }
    }
  }

  if (!conf.usingUser) {
    reg_mcounteren := 0
  }

  def writeCounter(lo: Int, ctr: WideCounter, wdata: UInt) = {
    val hi = lo + CSRs.mcycleh - CSRs.mcycle
    when (decoded_addr(hi)) { ctr := Cat(wdata(ctr.getWidth-33, 0), ctr(31, 0)) }
    when (decoded_addr(lo)) { ctr := Cat(ctr(ctr.getWidth-1, 32), wdata) }
  }
  def readModifyWriteCSR(cmd: UInt, rdata: UInt, wdata: UInt) =
    (Mux(cmd.isOneOf(CSR.S, CSR.C), rdata, 0.U) | wdata) & ~Mux(cmd === CSR.C, wdata, 0.U)
}
