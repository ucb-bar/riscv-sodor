package Common

import Chisel._
import Node._
import Constants._
import scala.math._
import Util._
//import Chisel.ImplicitConversions._

class Status extends Bundle {
  val im = Bits(width = 8)
  val zero = Bits(width = 7)
  val vm = Bool()
  val s64 = Bool()
  val u64 = Bool()
  val s = Bool()
  val ps = Bool()
  val ec = Bool()
  val ev = Bool()
  val ef = Bool()
  val et = Bool()
}

object PCR
{
  // commands
  val SZ = 3
  val X = Bits("b???", 3)
  val N = Bits(0,3)
  val F = Bits(1,3) // mfpcr
  val T = Bits(4,3) // mtpcr
  val C = Bits(6,3) // clearpcr
  val S = Bits(7,3) // setpcr

  // regs
  val STATUS   =  0
  val EPC      =  1
  val BADVADDR =  2
  val EVEC     =  3
  val COUNT    =  4
  val COMPARE  =  5
  val CAUSE    =  6
  val PTBR     =  7
  val SEND_IPI =  8
  val CLR_IPI  =  9
  val COREID   = 10
  val IMPL     = 11
  val K0       = 12
  val K1       = 13
  val VECBANK  = 18
  val VECCFG   = 19
  val RESET    = 29
  val TOHOST   = 30
  val FROMHOST = 31
}

class PCR(implicit conf: SodorConfiguration) extends Module
{
  val io = new Bundle {
    val host = new HTIFIO()
    val r = new ioReadPort(conf.nxpr, conf.xprlen)
    val w = new ioWritePort(conf.nxpr, conf.xprlen)
    
    val status = new Status().asOutput
    val ptbr = UInt(OUTPUT, PADDR_BITS)
    val evec = UInt(OUTPUT, VADDR_BITS)
    val exception = Bool(INPUT)
    val cause = UInt(INPUT, 6)
    val badvaddr_wen = Bool(INPUT)
    val vec_irq_aux = Bits(INPUT, conf.xprlen)
    val vec_irq_aux_wen = Bool(INPUT)
    val pc = UInt(INPUT, VADDR_BITS+1)
    val eret = Bool(INPUT)
    val ei = Bool(INPUT)
    val di = Bool(INPUT)
    val ptbr_wen = Bool(OUTPUT)
    val irq_timer = Bool(OUTPUT)
    val irq_ipi = Bool(OUTPUT)
    val replay = Bool(OUTPUT)
    val vecbank = Bits(OUTPUT, 8)
    val vecbankcnt = UInt(OUTPUT, 4)
    val vec_appvl = UInt(INPUT, 12)
    val vec_nxregs = UInt(INPUT, 6)
    val vec_nfregs = UInt(INPUT, 6)
  }
  import PCR._
 
  val reg_epc = Reg(Bits(width = conf.xprlen))
  val reg_badvaddr = Reg(Bits(width = conf.xprlen))
  val reg_ebase = Reg(Bits(width = conf.xprlen))
  val reg_count = WideCounter(32)
  val reg_compare = Reg(Bits(width = 32))
  val reg_cause = Reg(Bits(width = io.cause.getWidth))
  val reg_tohost = Reg(init=Bits(0, conf.xprlen))
  val reg_fromhost = Reg(init=Bits(0, conf.xprlen))
  val reg_coreid = Reg(Bits(width = 16))
  val reg_k0 = Reg(Bits(width = conf.xprlen))
  val reg_k1 = Reg(Bits(width = conf.xprlen))
  val reg_ptbr = Reg(UInt(width = PADDR_BITS))
  val reg_vecbank = Reg(init=SInt(-1,8).toBits)
  val reg_error_mode  = Reg(init=Bool(false))
  val reg_status = Reg(new Status) // reset down below

  val r_irq_timer = Reg(init=Bool(false))
  val r_irq_ipi   = Reg(init=Bool(true))
  
  val rdata = Bits();

  val host_pcr_req_valid = Reg(Bool()) // don't reset
  val host_pcr_req_fire = host_pcr_req_valid && !io.r.en && !io.w.en
  val host_pcr_rep_valid = Reg(Bool()) // don't reset
  val host_pcr_bits = Reg(io.host.pcr_req.bits)
  io.host.pcr_req.ready := !host_pcr_req_valid && !host_pcr_rep_valid
  io.host.pcr_rep.valid := host_pcr_rep_valid
  io.host.pcr_rep.bits := host_pcr_bits.data
  when (io.host.pcr_req.fire()) {
    host_pcr_req_valid := true
    host_pcr_bits := io.host.pcr_req.bits

  }
  when (host_pcr_req_fire) {
    host_pcr_req_valid := false
    host_pcr_rep_valid := true
    host_pcr_bits.data := rdata
  }
  when (io.host.pcr_rep.fire()) { host_pcr_rep_valid := false }

  val raddr = Mux(io.r.en, io.r.addr, host_pcr_bits.addr)
  val wen = io.w.en || !io.r.en && host_pcr_req_valid && host_pcr_bits.rw
  val waddr = Mux(io.w.en, io.w.addr, host_pcr_bits.addr)
  val wdata = Mux(io.w.en, io.w.data, host_pcr_bits.data)

  io.status := reg_status
  io.ptbr_wen := wen && waddr === PTBR
  io.evec := Mux(io.exception, reg_ebase, reg_epc).toUInt
  io.ptbr := reg_ptbr
  io.host.debug.error_mode := reg_error_mode
  io.r.data := rdata

  io.vecbank := reg_vecbank
  var cnt = UInt(0,4)
  for (i <- 0 until 8)
    cnt = cnt + reg_vecbank(i)
  io.vecbankcnt := cnt(3,0)

  when (io.badvaddr_wen || io.vec_irq_aux_wen) {
    val wdata = Mux(io.badvaddr_wen, io.w.data, io.vec_irq_aux)
    // this code is broken for a 32b address space
//    println("wdata size: " + wdata.getWidth)
//    println("vaddrbits : " + VADDR_BITS)
//    val (upper, lower) = Split(wdata, VADDR_BITS)
//    val sign = Mux(lower.toSInt < SInt(0), upper.andR, upper.orR)
//    reg_badvaddr := Cat(sign, lower).toSInt
    reg_badvaddr := wdata
  }

  when (io.exception) {
    when (!reg_status.et) {
      reg_error_mode := true
    }
    reg_status.s := true
    reg_status.ps := reg_status.s
    reg_status.et := false
    reg_epc := io.pc.toSInt
    reg_cause := io.cause
  }
  
  when (io.eret) {
    reg_status.s := reg_status.ps
    reg_status.et := true
  }
  
  when (reg_count === reg_compare) {
    r_irq_timer := Bool(true);
  }

//  io.irq_timer := r_irq_timer;
//  io.irq_ipi := r_irq_ipi;
//  io.host.ipi_req.valid := io.w.en && io.w.addr === SEND_IPI
//  io.host.ipi_req.bits := io.w.data
//  io.replay := io.host.ipi_req.valid && !io.host.ipi_req.ready

  when (host_pcr_req_fire && !host_pcr_bits.rw && host_pcr_bits.addr === TOHOST) { reg_tohost := UInt(0) }

  val read_impl = Bits(2)
  val read_ptbr = reg_ptbr(PADDR_BITS-1,PGIDX_BITS) << PGIDX_BITS
  val read_veccfg = Bits(0) // Sodor has no vector unit
  val read_cause = reg_cause(reg_cause.getWidth-1) << conf.xprlen-1 | reg_cause(reg_cause.getWidth-2,0)
  rdata := AVec[Bits](
    reg_status.toBits, reg_epc,          reg_badvaddr,     reg_ebase,
    reg_count,         reg_compare,      read_cause,       read_ptbr,
    reg_coreid/*x*/,   read_impl/*x*/,   reg_coreid,       read_impl,
    reg_k0,            reg_k1,           reg_k0/*x*/,      reg_k1/*x*/,
    reg_vecbank/*x*/,  read_veccfg/*x*/, reg_vecbank,      read_veccfg,
    reg_vecbank/*x*/,  read_veccfg/*x*/, reg_vecbank/*x*/, read_veccfg/*x*/,
    reg_vecbank/*x*/,  read_veccfg/*x*/, reg_tohost/*x*/,  reg_fromhost/*x*/,
    reg_vecbank/*x*/,  read_veccfg/*x*/, reg_tohost,       reg_fromhost
  )(raddr)

  when (wen) {
    when (waddr === STATUS) {
      reg_status := new Status().fromBits(wdata)
      reg_status.zero := 0
      reg_status.ev := false // Sodor has no vector unit
      reg_status.ef := false // Sodor has no FPU unit
      if (!conf.rvc) reg_status.ec := false
    }
    when (waddr === EPC)      { reg_epc := wdata(VADDR_BITS,0).toSInt }
    when (waddr === EVEC)     { reg_ebase := wdata(VADDR_BITS-1,0).toSInt }
    when (waddr === COUNT)    { reg_count := wdata.toUInt }
    when (waddr === COMPARE)  { reg_compare := wdata(31,0).toUInt; r_irq_timer := Bool(false); }
    when (waddr === COREID)   { reg_coreid := wdata(15,0) }
    when (waddr === FROMHOST) { when (reg_fromhost === UInt(0) || io.w.en) { reg_fromhost := wdata } }
    when (waddr === TOHOST)   { when (reg_tohost === UInt(0)) { reg_tohost := wdata } }
    when (waddr === CLR_IPI)  { r_irq_ipi := wdata(0) }
    when (waddr === K0)       { reg_k0 := wdata; }
    when (waddr === K1)       { reg_k1 := wdata; }
    when (waddr === PTBR)     { reg_ptbr := Cat(wdata(PADDR_BITS-1, PGIDX_BITS), Bits(0, PGIDX_BITS)).toUInt; }
    when (waddr === VECBANK)  { reg_vecbank:= wdata(7,0) }
  }

//  io.host.ipi_rep.ready := Bool(true)
//  when (io.host.ipi_rep.valid) { r_irq_ipi := Bool(true) }

  when (this.reset) {
    reg_status.et := false
    reg_status.ef := false
    reg_status.ev := false
    reg_status.ec := false
    reg_status.ps := false
    reg_status.s := true
    reg_status.u64 := true
    reg_status.s64 := true
    reg_status.vm := false
    reg_status.zero := 0
    reg_status.im := 0
  }
}

class ioReadPort(d: Int, w: Int) extends Bundle
{
  val addr = UInt(INPUT, log2Up(d))
  val en   = Bool(INPUT)
  val data = Bits(OUTPUT, w)
  override def clone = new ioReadPort(d, w).asInstanceOf[this.type]
}

class ioWritePort(d: Int, w: Int) extends Bundle
{
  val addr = UInt(INPUT, log2Up(d))
  val en   = Bool(INPUT)
  val data = Bits(INPUT, w)
  override def clone = new ioWritePort(d, w).asInstanceOf[this.type]
}
