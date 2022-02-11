package sodor.common

import chisel3._
import chisel3.util._
import sodor.common.Util._
import Util._
import Constants._


object DMConsts{

  def dmiDataSize = 32
  def nDMIAddrSize = 7
  def dmiOpSize = 2
  def dmi_OP_NONE            = "b00".U
  def dmi_OP_READ            = "b01".U
  def dmi_OP_WRITE           = "b10".U

  def dmiRespSize = 2
  def dmi_RESP_SUCCESS     = "b00".U
  def dmi_RESP_FAILURE     = "b01".U
  def dmi_RESP_HW_FAILURE  = "b10".U
  // This is used outside this block
  // to indicate 'busy'.
  def dmi_RESP_RESERVED    = "b11".U

  def dmi_haltStatusAddr   = 0x40
  def nProgBuf = 4
  def nDataCount = 1
  def hartInfo = "h111bc0".U
}


class DMIReq(addrBits : Int) extends Bundle {
  val op   = Output(UInt(DMConsts.dmiOpSize.W))
  val addr = Output(UInt(addrBits.W))
  val data = Output(UInt(DMConsts.dmiDataSize.W))
}

/** Structure to define the contents of a Debug Bus Response
  */
class DMIResp() extends Bundle {
  val data = Output(UInt(DMConsts.dmiDataSize.W))
  val resp = Output(UInt(DMConsts.dmiRespSize.W))
}

/** Structure to define the top-level DMI interface
  *  of DebugModule.
  *  DebugModule is the consumer of this interface.
  *  Therefore it has the 'flipped' version of this.
  */
class DMIIO(implicit val conf: SodorCoreParams) extends Bundle {
  val req = new  DecoupledIO(new DMIReq(DMConsts.nDMIAddrSize))
  val resp = Flipped(new DecoupledIO(new DMIResp))
}

/** This includes the clock and reset as these are passed through the
  *  hierarchy until the Debug Module is actually instantiated.
  *
  */

class SimDTM(implicit val conf: SodorCoreParams) extends BlackBox {
  val io = IO(new Bundle {
      val clk = Input(Clock())
      val reset = Input(Bool())
      val debug = new DMIIO()
      val exit = Output(UInt(32.W))
    })

  def connect(tbclk: Clock, tbreset: Bool, dutio: DMIIO, tbsuccess: Bool) = {
    io.clk := tbclk
    io.reset := tbreset
    dutio <> io.debug

    tbsuccess := io.exit === 1.U
    when (io.exit >= 2.U) {
      printf("*** FAILED *** (exit code = %d)\n", io.exit >> 1.U)
      //stop(1)
    }
  }
}

class DebugDPath(implicit val conf: SodorCoreParams) extends Bundle
{
  // REG access
  val addr = Output(UInt(5.W))
  val wdata = Output(UInt(32.W))
  val validreq = Output(Bool())
  val rdata = Input(UInt(32.W))
  val resetpc = Output(Bool())
}

class DebugCPath(implicit val conf: SodorCoreParams) extends Bundle
{
  val halt = Output(Bool())
}

class DebugIo(implicit val conf: SodorCoreParams) extends Bundle
{
  val dmi = Flipped(new DMIIO())
  val ddpath = new DebugDPath()
  val dcpath = new DebugCPath()
  val debugmem = new MemPortIo(data_width = 32)
  val resetcore = Output(Bool())
}

class DebugModule(implicit val conf: SodorCoreParams) extends Module {
  val io = IO(new DebugIo())
  io := DontCare

  io.dmi.req.ready := io.dmi.req.valid
  val dmireq = io.dmi.req.valid
  io.dmi.resp.bits.resp := DMConsts.dmi_RESP_SUCCESS
  val dmstatusReset  = Wire(new DMSTATUSFields())
  dmstatusReset := DontCare
  dmstatusReset.authenticated := true.B
  dmstatusReset.versionlo := "b10".U
  val dmstatus = RegInit(dmstatusReset)
  val sbcsreset = Wire(new SBCSFields())
  sbcsreset := DontCare
  sbcsreset.sbaccess := 2.U
  sbcsreset.sbasize := 32.U
  sbcsreset.sbaccess32 := true.B
  sbcsreset.sbaccess16 := false.B
  sbcsreset.sbaccess8 := false.B
  val sbcs = RegInit(sbcsreset)
  val abstractcsReset = Wire(new ABSTRACTCSFields())
  abstractcsReset := DontCare
  abstractcsReset.datacount := DMConsts.nDataCount.U
  abstractcsReset.progsize := DMConsts.nProgBuf.U
  val abstractcs = RegInit(abstractcsReset)
  val command = Reg(new ACCESS_REGISTERFields())
  val dmcontrol = Reg(new DMCONTROLFields())
  val progbuf = Reg(Vec(DMConsts.nProgBuf, UInt(conf.xprlen.W)))
  val data0 = Reg(UInt(conf.xprlen.W))  //arg0
  val data1 = Reg(UInt(conf.xprlen.W))  //arg1
  val data2 = Reg(UInt(conf.xprlen.W))  //arg2
  val sbaddr = Reg(UInt(conf.xprlen.W))
  val sbdata = Reg(UInt(conf.xprlen.W))
  val memreadfire = RegInit(false.B)
  val coreresetval = RegInit(true.B)

  val read_map = collection.mutable.LinkedHashMap[Int,UInt](
    DMI_RegAddrs.DMI_ABSTRACTCS -> abstractcs.asUInt,
    DMI_RegAddrs.DMI_DMCONTROL -> dmcontrol.asUInt,
    DMI_RegAddrs.DMI_DMSTATUS  -> dmstatus.asUInt,
    DMI_RegAddrs.DMI_COMMAND -> command.asUInt,
    DMI_RegAddrs.DMI_HARTINFO -> DMConsts.hartInfo,
    DMI_RegAddrs.DMI_ABSTRACTAUTO -> 0.U,
    DMI_RegAddrs.DMI_CFGSTRADDR0 -> 0.U,
    DMI_RegAddrs.DMI_DATA0 -> data0,
    (DMI_RegAddrs.DMI_DATA0 + 1) -> data1,
    (DMI_RegAddrs.DMI_DATA0 + 2) -> data2,
    DMI_RegAddrs.DMI_PROGBUF0 -> progbuf(0),
    DMI_RegAddrs.DMI_PROGBUF1 -> progbuf(1),
    DMI_RegAddrs.DMI_PROGBUF2 -> progbuf(2),
    DMI_RegAddrs.DMI_PROGBUF3 -> progbuf(3),
    DMI_RegAddrs.DMI_AUTHDATA -> 0.U,
    DMI_RegAddrs.DMI_SERCS -> 0.U,
    DMI_RegAddrs.DMI_SBCS -> sbcs.asUInt,
    DMI_RegAddrs.DMI_SBADDRESS0 -> sbaddr,
    DMI_RegAddrs.DMI_SBDATA0 -> sbdata)
  val decoded_addr = read_map map { case (k, v) => k -> (io.dmi.req.bits.addr === k) }
  io.dmi.resp.bits.data := Mux1H(for ((k, v) <- read_map) yield decoded_addr(k) -> v)
  val wdata = io.dmi.req.bits.data
  dmstatus.allhalted := dmcontrol.haltreq
  dmstatus.allrunning := dmcontrol.resumereq
  io.dcpath.halt := dmstatus.allhalted && !dmstatus.allrunning
  when (io.dmi.req.bits.op === DMConsts.dmi_OP_WRITE){
    when((decoded_addr(DMI_RegAddrs.DMI_ABSTRACTCS)) && io.dmi.req.valid) {
      val tempabstractcs = wdata.asTypeOf(new ABSTRACTCSFields())
      abstractcs.cmderr := tempabstractcs.cmderr
    }
    when(decoded_addr(DMI_RegAddrs.DMI_COMMAND)) {
      val tempcommand = wdata.asTypeOf(new ACCESS_REGISTERFields())
      when(tempcommand.size === 2.U){
        command.postexec := tempcommand.postexec
        command.regno := tempcommand.regno
        command.transfer := tempcommand.transfer
        command.write := tempcommand.write
        abstractcs.cmderr := 1.U
      } .otherwise {
        abstractcs.cmderr := 2.U
      }
    }
    when(decoded_addr(DMI_RegAddrs.DMI_DMCONTROL)) {
      val tempcontrol = wdata.asTypeOf(new DMCONTROLFields())
      dmcontrol.haltreq := tempcontrol.haltreq
      dmcontrol.resumereq := tempcontrol.resumereq
      dmcontrol.hartreset := tempcontrol.hartreset
      dmcontrol.ndmreset := tempcontrol.ndmreset
      dmcontrol.dmactive := tempcontrol.dmactive
    }
    when(decoded_addr(DMI_RegAddrs.DMI_SBCS)){
      val tempsbcs = wdata.asTypeOf(new SBCSFields())
      sbcs.sbsingleread := tempsbcs.sbsingleread
      sbcs.sbaccess := tempsbcs.sbaccess
      sbcs.sbautoincrement := tempsbcs.sbautoincrement
      sbcs.sbautoread := tempsbcs.sbautoread
      sbcs.sberror := tempsbcs.sberror
    }
    when(decoded_addr(DMI_RegAddrs.DMI_SBADDRESS0)) { sbaddr := wdata}
    when(decoded_addr(DMI_RegAddrs.DMI_SBDATA0)) {
      sbdata := wdata
      io.debugmem.req.bits.addr := sbaddr
      io.debugmem.req.bits.data := sbdata
      io.debugmem.req.bits.fcn :=  M_XWR
      io.debugmem.req.valid := io.dmi.req.valid
      when(sbcs.sbautoincrement && io.dmi.req.valid)
      {
        sbaddr := sbaddr + 4.U
      }
    }
    when(decoded_addr(DMI_RegAddrs.DMI_DATA0)) ( data0 := wdata )
    when(decoded_addr(DMI_RegAddrs.DMI_DATA0+1)) ( data1 := wdata )
    when(decoded_addr(DMI_RegAddrs.DMI_DATA0+2)) ( data2 := wdata )
  }

  /// abstract cs command regfile access
  io.ddpath.addr := command.regno & "hfff".U
  when(command.transfer && abstractcs.cmderr =/= 0.U){
    when(command.write){
      io.ddpath.wdata := data0
      io.ddpath.validreq := true.B
    } .otherwise {
      data0 := io.ddpath.rdata
    }
    abstractcs.cmderr := 0.U
  }

  when(!(decoded_addr(DMI_RegAddrs.DMI_SBDATA0) && io.dmi.req.bits.op === DMConsts.dmi_OP_WRITE)){
    io.debugmem.req.bits.fcn := false.B
  }


  val firstreaddone = Reg(Bool())

  io.dmi.resp.valid := Mux(firstreaddone, RegNext(io.debugmem.resp.valid), io.dmi.req.valid)

  when ((decoded_addr(DMI_RegAddrs.DMI_SBDATA0) && (io.dmi.req.bits.op === DMConsts.dmi_OP_READ)) || (sbcs.sbautoread && firstreaddone)){
    io.debugmem.req.bits.addr :=  sbaddr
    io.debugmem.req.bits.fcn := M_XRD
    io.debugmem.req.valid := io.dmi.req.valid
    // for async data readily available
    // so capture it in reg
    when(io.debugmem.resp.valid){
      sbdata := io.debugmem.resp.bits.data
    }
    memreadfire := true.B
    firstreaddone := true.B
  }

  when(memreadfire && io.debugmem.resp.valid)
  {  // following is for sync data available in
    // next cycle memreadfire a reg allows
    // entering this reg only in next
    sbdata := io.debugmem.resp.bits.data
    memreadfire := false.B
    when(sbcs.sbautoincrement)
    {
      sbaddr := sbaddr + 4.U
    }
  }

  when(!decoded_addr(DMI_RegAddrs.DMI_SBDATA0)){
    firstreaddone := false.B
  }

  io.resetcore := coreresetval

  when((io.dmi.req.bits.addr === "h44".U) && io.dmi.req.valid){
    coreresetval := false.B
  }
}
