// See LICENSE for license details.

// TODO: add timeh, cycleh, counth, instreh counters for the full RV32I experience.
// NOTE: This is mostly a copy from the Berkeley Rocket-chip csr file. It is
//       overkill for a small, embedded processor. 

package Common


import chisel3._
import chisel3.util._
import Util._
import Instructions._


import Common.Constants._
import scala.math._

class MStatus extends Bundle {
  val sd = Bool()
  val zero2 = Wire(UInt(31.W))
  val sd_rv32 = Wire(UInt(1.W))
  val zero1 = Wire(UInt(9.W))
  val vm = Wire(UInt(5.W))
  val mprv = Bool()
  val xs = Wire(UInt(2.W))
  val fs = Wire(UInt(2.W))
  val prv3 = Wire(UInt(2.W))
  val ie3 = Bool()
  val prv2 = Wire(UInt(2.W))
  val ie2 = Bool()
  val prv1 = Wire(UInt(2.W))
  val ie1 = Bool()
  val prv = Wire(UInt(2.W))
  val ie = Bool()
}

class MIP extends Bundle {
  val mtip = Bool()
  val htip = Bool()
  val stip = Bool()
  val utip = Bool()
  val msip = Bool()
  val hsip = Bool()
  val ssip = Bool()
  val usip = Bool()
}

object CSR
{
  // commands
  val SZ = 3.W
//  val X = UInt.DC(SZ)
  val X = 0.asUInt(SZ)
  val N = 0.asUInt(SZ)
  val W = 1.asUInt(SZ)
  val S = 2.asUInt(SZ)
  val C = 3.asUInt(SZ)
  val I = 4.asUInt(SZ)
  val R = 5.asUInt(SZ)
}

class CSRFileIO(implicit conf: SodorConfiguration) extends Bundle {
  val host = new HTIFIO
  val rw = new Bundle {
    val addr = Input(UInt(12.W))
    val cmd = Input(UInt(CSR.SZ))
    val rdata = Output(UInt(conf.xprlen))
    val wdata = Input(UInt(conf.xprlen))
  }

  val csr_replay = Output(Bool())
  val csr_stall = Output(Bool())
  val csr_xcpt = Output(Bool())
  val eret = Output(Bool())

  val status = new MStatus().asOutput
  val ptbr = Output(UInt(PADDR_BITS))
  val evec = Output(UInt(VADDR_BITS))
  val exception = Input(Bool())
  val retire = Input(Bool())
  val uarch_counters = Vec.fill(16)(Input(Bool()))
  val cause = Input(UInt(conf.xprlen))
  val pc = Input(UInt(VADDR_BITS))
  val fatc = Output(Bool())
  val time = Output(UInt(conf.xprlen))
  val interrupt = Output(Bool())
  val interrupt_cause = Output(UInt(conf.xprlen))
}

class CSRFile(implicit conf: SodorConfiguration) extends Module
{
  val io = new CSRFileIO

  val reg_mstatus = Reg(new MStatus)
  val reg_mie = Reg(init=new MIP().fromBits(0))
  val reg_mip = Reg(init=new MIP().fromBits(0))
  val reg_mepc = Reg(Wire(UInt(VADDR_BITS)))
  val reg_mcause = Reg(Wire(UInt(conf.xprlen)))
  val reg_mbadaddr = Reg(Wire(UInt(VADDR_BITS)))
  val reg_mscratch = Reg(Wire(UInt(conf.xprlen)))
  val reg_mtimecmp = Reg(Wire(UInt(conf.xprlen)))
  val reg_wfi = Reg(init=Bool(false))

  val reg_tohost = Reg(init=Bits(0, conf.xprlen))
  val reg_fromhost = Reg(init=Bits(0, conf.xprlen))
  val reg_stats = Reg(init=Bool(false))
  val reg_time = WideCounter(conf.xprlen)
  val reg_instret = WideCounter(conf.xprlen, io.retire)
  val reg_uarch_counters = io.uarch_counters.map(WideCounter(conf.xprlen, _))

  io.interrupt_cause := 0
  io.interrupt := io.interrupt_cause(conf.xprlen-1)
  val some_interrupt_pending = Bool(); some_interrupt_pending := false
  def checkInterrupt(max_priv: UInt, cond: Bool, num: Int) = {
    when (cond && (reg_mstatus.prv < max_priv || reg_mstatus.prv === max_priv && reg_mstatus.ie)) {
      io.interrupt_cause := UInt((BigInt(1) << (conf.xprlen-1)) + num)
    }
    when (cond && reg_mstatus.prv <= max_priv) {
      some_interrupt_pending := true
    }
  }

  checkInterrupt(PRV_M, reg_mie.msip && reg_mip.msip, 0)
  checkInterrupt(PRV_M, reg_fromhost != UInt(0), 2)
  checkInterrupt(PRV_M, reg_mie.mtip && reg_mip.mtip, 1)

  val system_insn = io.rw.cmd === CSR.I
  val cpu_ren = io.rw.cmd != CSR.N && !system_insn

  val host_pcr_req_valid = Reg(Bool()) // don't reset
  val host_pcr_req_fire = host_pcr_req_valid && !cpu_ren
  val host_pcr_rep_valid = Reg(Bool()) // don't reset
  val host_pcr_bits = Reg(io.host.csr_req.bits)
  io.host.csr_req.ready := !host_pcr_req_valid && !host_pcr_rep_valid
  io.host.csr_rep.valid := host_pcr_rep_valid
  io.host.csr_rep.bits := host_pcr_bits.data
  when (io.host.csr_req.fire()) {
    host_pcr_req_valid := true
    host_pcr_bits := io.host.csr_req.bits
  }
  when (host_pcr_req_fire) {
    host_pcr_req_valid := false
    host_pcr_rep_valid := true
    host_pcr_bits.data := io.rw.rdata
  }
  when (io.host.csr_rep.fire()) { host_pcr_rep_valid := false }
  
  io.host.debug_stats_csr := reg_stats // direct export up the hierarchy

  val read_mstatus = io.status.toBits
  val isa_string = "I"
  val cpuid = BigInt(0) | isa_string.map(x => 1 << (x - 'A')).reduce(_|_)
  val impid = 0x8000 // indicates an anonymous source, which can be used
                     // during development before a Source ID is allocated.

  val read_mapping = collection.mutable.LinkedHashMap[Int,Bits](
    CSRs.fflags -> UInt(0),
    CSRs.frm -> UInt(0),
    CSRs.fcsr -> UInt(0),
    CSRs.cycle -> reg_time,
    CSRs.cyclew -> reg_time,
    CSRs.instret -> reg_instret,
    CSRs.instretw -> reg_instret,
    CSRs.time -> reg_time,
    CSRs.timew -> reg_time,
    CSRs.stime -> reg_time,
    CSRs.stimew -> reg_time,
    CSRs.mcpuid -> UInt(cpuid),
    CSRs.mimpid -> UInt(impid),
    CSRs.mstatus -> read_mstatus,
    CSRs.mtdeleg -> UInt(0),
    CSRs.mtvec -> UInt(MTVEC),
    CSRs.mip -> reg_mip.toBits,
    CSRs.mie -> reg_mie.toBits,
    CSRs.mscratch -> reg_mscratch,
    CSRs.mepc -> reg_mepc,
    CSRs.mbadaddr -> reg_mbadaddr,
    CSRs.mcause -> reg_mcause,
    CSRs.mtimecmp -> reg_mtimecmp,
    CSRs.mhartid -> io.host.id,
    CSRs.send_ipi -> io.host.id, /* don't care */
    CSRs.stats -> reg_stats,
    CSRs.mtohost -> reg_tohost,
    CSRs.mfromhost -> reg_fromhost)

  for (i <- 0 until reg_uarch_counters.size)
    read_mapping += (CSRs.uarch0 + i) -> reg_uarch_counters(i)

  val addr = Mux(cpu_ren, io.rw.addr, host_pcr_bits.addr)
  val decoded_addr = read_mapping map { case (k, v) => k -> (addr === k) }

  val addr_valid = decoded_addr.values.reduce(_||_)
  val fp_csr = decoded_addr(CSRs.fflags) || decoded_addr(CSRs.frm) || decoded_addr(CSRs.fcsr)
  val csr_addr_priv = io.rw.addr(9,8)
  val priv_sufficient = reg_mstatus.prv >= csr_addr_priv
  val read_only = io.rw.addr(11,10).andR
  val cpu_wen = cpu_ren && io.rw.cmd != CSR.R && priv_sufficient
  val wen = cpu_wen && !read_only || host_pcr_req_fire && host_pcr_bits.rw
  val wdata = Mux(io.rw.cmd === CSR.W, io.rw.wdata,
              Mux(io.rw.cmd === CSR.C, io.rw.rdata & ~io.rw.wdata,
              Mux(io.rw.cmd === CSR.S, io.rw.rdata | io.rw.wdata,
              host_pcr_bits.data)))

  val opcode = io.rw.addr
  val insn_call = !opcode(8) && !opcode(0) && system_insn
  val insn_break = !opcode(8) && opcode(0) && system_insn
  val insn_ret = opcode(8) && !opcode(1) && !opcode(0) && system_insn && priv_sufficient
  val insn_sfence_vm = opcode(8) && !opcode(1) && opcode(0) && system_insn && priv_sufficient
  val insn_wfi = opcode(8) && opcode(1) && !opcode(0) && system_insn && priv_sufficient

  val csr_xcpt = (cpu_wen && read_only) ||
    (cpu_ren && (!priv_sufficient || !addr_valid || fp_csr && !io.status.fs.orR)) ||
    (system_insn && !priv_sufficient) ||
    insn_call || insn_break

  when (insn_wfi) { reg_wfi := true }
  when (some_interrupt_pending) { reg_wfi := false }

  io.fatc := insn_sfence_vm
  io.evec := Mux(io.exception || csr_xcpt, (reg_mstatus.prv << 6) + MTVEC,
                                           reg_mepc)
  io.csr_xcpt := csr_xcpt
  io.eret := insn_ret
  io.status := reg_mstatus
  io.status.fs := reg_mstatus.fs.orR.toSInt // either off or dirty (no clean/initial support yet)
  io.status.xs := reg_mstatus.xs.orR.toSInt // either off or dirty (no clean/initial support yet)
  io.status.sd := reg_mstatus.xs.orR || reg_mstatus.fs.orR
  if (conf.xprlen == 32)
    io.status.sd_rv32 := io.status.sd

  when (io.exception || csr_xcpt) {
    reg_mstatus.ie := false
    reg_mstatus.prv := PRV_M
    reg_mstatus.mprv := false
    reg_mstatus.prv1 := reg_mstatus.prv
    reg_mstatus.ie1 := reg_mstatus.ie
    reg_mstatus.prv2 := reg_mstatus.prv1
    reg_mstatus.ie2 := reg_mstatus.ie1

    reg_mepc := (io.pc >> 2.U) << 2 // clear low-2 bits
    reg_mcause := io.cause
    when (csr_xcpt) {
      reg_mcause := Causes.illegal_instruction
      when (insn_break) { reg_mcause := Causes.breakpoint }
      when (insn_call) { reg_mcause := reg_mstatus.prv + Causes.user_ecall }
    }

    reg_mbadaddr := io.pc // misaligned memory exceptions not supported...
  }
  
  when (insn_ret) {
    reg_mstatus.ie := reg_mstatus.ie1
    reg_mstatus.prv := reg_mstatus.prv1
    reg_mstatus.prv1 := reg_mstatus.prv2
    reg_mstatus.ie1 := reg_mstatus.ie2
    reg_mstatus.prv2 := PRV_U
    reg_mstatus.ie2 := true
  }
  
  assert(PopCount(insn_ret :: io.exception :: csr_xcpt :: io.csr_replay :: Nil) <= 1, "these conditions must be mutually exclusive")

   when (reg_time >= reg_mtimecmp) {
      reg_mip.mtip := true
   }

  io.time := reg_time
  io.host.ipi_req.valid := cpu_wen && decoded_addr(CSRs.send_ipi)
  io.host.ipi_req.bits := io.rw.wdata
  io.csr_replay := io.host.ipi_req.valid && !io.host.ipi_req.ready
  io.csr_stall := reg_wfi

  when (host_pcr_req_fire && !host_pcr_bits.rw && decoded_addr(CSRs.mtohost)) { reg_tohost := UInt(0) }

  io.rw.rdata := Mux1H(for ((k, v) <- read_mapping) yield decoded_addr(k) -> v)

  when (wen) {
    when (decoded_addr(CSRs.mstatus)) {
      val new_mstatus = new MStatus().fromBits(wdata)
      reg_mstatus.ie := new_mstatus.ie
      reg_mstatus.ie1 := new_mstatus.ie1

      val supportedModes = Vec((PRV_M :: PRV_U :: (if (conf.vm) List(PRV_S) else Nil)).map(UInt(_)))
      if (supportedModes.size > 1) {
        reg_mstatus.mprv := new_mstatus.mprv
        when (supportedModes contains new_mstatus.prv) { reg_mstatus.prv := new_mstatus.prv }
        when (supportedModes contains new_mstatus.prv1) { reg_mstatus.prv1 := new_mstatus.prv1 }
        if (supportedModes.size > 2) {
          when (supportedModes contains new_mstatus.prv2) { reg_mstatus.prv2 := new_mstatus.prv2 }
          reg_mstatus.ie2 := new_mstatus.ie2
        }
      }
    }
    when (decoded_addr(CSRs.mip)) {
      val new_mip = new MIP().fromBits(wdata)
      reg_mip.msip := new_mip.msip
    }
    when (decoded_addr(CSRs.mie)) {
      val new_mie = new MIP().fromBits(wdata)
      reg_mie.msip := new_mie.msip
      reg_mie.mtip := new_mie.mtip
    }
    when (decoded_addr(CSRs.mepc))     { reg_mepc := wdata(VADDR_BITS-1,0).toSInt & SInt(-4) }
    when (decoded_addr(CSRs.mscratch)) { reg_mscratch := wdata }
    when (decoded_addr(CSRs.mcause))   { reg_mcause := wdata & UInt((BigInt(1) << (conf.xprlen-1)) + 31) /* only implement 5 LSBs and MSB */ }
    when (decoded_addr(CSRs.mbadaddr)) { reg_mbadaddr := wdata(VADDR_BITS-1,0) }
    when (decoded_addr(CSRs.cyclew))   { reg_time := wdata }
    when (decoded_addr(CSRs.instretw)) { reg_instret := wdata }
    when (decoded_addr(CSRs.timew))    { reg_time := wdata }
    when (decoded_addr(CSRs.mtimecmp)) { reg_mtimecmp := wdata; reg_mip.mtip := false }
    when (decoded_addr(CSRs.mfromhost)){ when (reg_fromhost === UInt(0) || !host_pcr_req_fire) { reg_fromhost := wdata } }
    when (decoded_addr(CSRs.mtohost))  { when (reg_tohost === UInt(0) || host_pcr_req_fire) { reg_tohost := wdata } }
    when (decoded_addr(CSRs.stats))    { reg_stats := wdata(0) }
  }

  io.host.ipi_rep.ready := true
  when (io.host.ipi_rep.valid) { reg_mip.msip := true }

  when(this.reset) {
    reg_mstatus.zero1 := 0
    reg_mstatus.zero2 := 0
    reg_mstatus.ie := false
    reg_mstatus.prv := PRV_M
    reg_mstatus.ie1 := false
    reg_mstatus.prv1 := PRV_M /* hard-wired to M when missing user mode */
    reg_mstatus.ie2 := false  /* hard-wired to 0 when missing supervisor mode */
    reg_mstatus.prv2 := PRV_U /* hard-wired to 0 when missing supervisor mode */
    reg_mstatus.ie3 := false  /* hard-wired to 0 when missing hypervisor mode */
    reg_mstatus.prv3 := PRV_U /* hard-wired to 0 when missing hypervisor mode */
    reg_mstatus.mprv := false
    reg_mstatus.vm := 0
    reg_mstatus.fs := 0
    reg_mstatus.xs := 0
    reg_mstatus.sd_rv32 := false
    reg_mstatus.sd := false
  }
}
