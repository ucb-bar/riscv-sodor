package Common

import chisel3._
import chisel3.util._
import Common._
import Common.Util._
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
  def nProgBuf = 0
  def nDataCount = 1
  def hartInfo = "h111bc0".U
}


class DMIReq(addrBits : Int) extends Bundle {
  val op   = Output(UInt(DMConsts.dmiOpSize.W))
  val addr = Output(UInt(addrBits.W))
  val data = Output(UInt(DMConsts.dmiDataSize.W))

  override def cloneType = new DMIReq(addrBits).asInstanceOf[this.type]
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
class DMIIO(implicit val conf: SodorConfiguration) extends Bundle {
  val req = new  DecoupledIO(new DMIReq(DMConsts.nDMIAddrSize))
  val resp = Flipped(new DecoupledIO(new DMIResp))
}

/** This includes the clock and reset as these are passed through the
  *  hierarchy until the Debug Module is actually instantiated. 
  *  
  */

class SimDTM(implicit val conf: SodorConfiguration) extends BlackBox {
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
    }
  }
}

class DebugIo(implicit val conf: SodorConfiguration) extends Bundle
{
  val dmi = Flipped(new DMIIO())
  val debugmem = new MemPortIo(data_width = 32)
  val resetcore = Output(Bool())
}

class DebugModule(implicit val conf: SodorConfiguration) extends Module {
  val io = IO(new DebugIo())
  io := DontCare

  io.dmi.req.ready := true.B
  io.debugmem.req.valid := false.B
  io.debugmem.req.bits.fcn := M_XRD
  io.debugmem.req.bits.typ := MT_W
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
  val data0 = Reg(UInt(conf.xprlen.W))  //arg0
  val sbaddr = Reg(UInt(conf.xprlen.W))
  val sbdata = Reg(UInt(conf.xprlen.W))
  val memongoing = RegInit(false.B)
  val coreresetval = RegInit(true.B)
  val addr = RegEnable(io.dmi.req.bits.addr, io.dmi.req.valid) // DMI Address
  val op = RegEnable(io.dmi.req.bits.op, io.dmi.req.valid) // DMI Op
  val wdata = RegEnable(io.dmi.req.bits.data, io.dmi.req.valid) // DMI Data

  val read_map = collection.mutable.LinkedHashMap[Int,UInt](
    DMI_RegAddrs.DMI_ABSTRACTCS -> abstractcs.asUInt,
    DMI_RegAddrs.DMI_DMCONTROL -> dmcontrol.asUInt, 
    DMI_RegAddrs.DMI_DMSTATUS  -> dmstatus.asUInt,
    DMI_RegAddrs.DMI_COMMAND -> command.asUInt,
    DMI_RegAddrs.DMI_HARTINFO -> DMConsts.hartInfo,
    DMI_RegAddrs.DMI_ABSTRACTAUTO -> 0.U,
    DMI_RegAddrs.DMI_CFGSTRADDR0 -> 0.U,
    DMI_RegAddrs.DMI_DATA0 -> data0,
    DMI_RegAddrs.DMI_AUTHDATA -> 0.U,
    DMI_RegAddrs.DMI_SERCS -> 0.U,
    DMI_RegAddrs.DMI_SBCS -> sbcs.asUInt,
    DMI_RegAddrs.DMI_SBADDRESS0 -> sbaddr,
    DMI_RegAddrs.DMI_SBDATA0 -> sbdata)
  val decoded_addr = read_map map { case (k, v) => k -> (addr === k) }
  io.dmi.resp.bits.data := Mux1H(for ((k, v) <- read_map) yield decoded_addr(k) -> v)
  dmstatus.allhalted := dmcontrol.haltreq
  dmstatus.allrunning := dmcontrol.resumereq 
  when (op === DMConsts.dmi_OP_WRITE && io.dmi.resp.fire()){ 
    when(decoded_addr(DMI_RegAddrs.DMI_ABSTRACTCS)) { 
      val tempabstractcs = wdata.asTypeOf(new ABSTRACTCSFields)
      abstractcs.cmderr := tempabstractcs.cmderr 
    }
    when(decoded_addr(DMI_RegAddrs.DMI_COMMAND)) { 
      val tempcommand = wdata.asTypeOf(new ACCESS_REGISTERFields)
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
      val tempcontrol = wdata.asTypeOf(new DMCONTROLFields)
      dmcontrol.haltreq := tempcontrol.haltreq
      dmcontrol.resumereq := tempcontrol.resumereq 
      dmcontrol.hartreset := tempcontrol.hartreset
      dmcontrol.ndmreset := tempcontrol.ndmreset
      dmcontrol.dmactive := tempcontrol.dmactive
    }
    when(decoded_addr(DMI_RegAddrs.DMI_SBCS)){
      val tempsbcs = wdata.asTypeOf(new SBCSFields)
      sbcs.sbsingleread := tempsbcs.sbsingleread
      sbcs.sbaccess := tempsbcs.sbaccess
      sbcs.sbautoincrement := tempsbcs.sbautoincrement
      sbcs.sbautoread := tempsbcs.sbautoread
      sbcs.sberror := tempsbcs.sberror
    }
    when(decoded_addr(DMI_RegAddrs.DMI_SBADDRESS0)) { sbaddr := wdata }
    when(decoded_addr(DMI_RegAddrs.DMI_DATA0)) { data0 := wdata }
  } 

  /// abstract cs command not supported
  when(command.transfer && abstractcs.cmderr =/= 0.U){
    abstractcs.cmderr := 0.U
  }

  val waitrespack = Reg(Bool()) // Hold Valid Signal till resp is ACK'ed
  waitrespack := io.dmi.resp.valid && !io.dmi.resp.ready 
  when(decoded_addr(DMI_RegAddrs.DMI_SBDATA0)) {
    io.dmi.resp.valid := RegNext(io.debugmem.resp.valid) || waitrespack
  } .otherwise {
    io.dmi.resp.valid := RegNext(io.dmi.req.fire()) || waitrespack
  }

  when (io.debugmem.req.fire()) {
    memongoing := true.B
  } 
  when (io.debugmem.resp.fire()) {
    memongoing := false.B
    op := DMConsts.dmi_OP_NONE
  }


  when (decoded_addr(DMI_RegAddrs.DMI_SBDATA0) && (op === DMConsts.dmi_OP_WRITE)) {
    io.debugmem.req.bits.addr := sbaddr
    io.debugmem.req.bits.data := wdata
    io.debugmem.req.bits.fcn :=  M_XWR
    io.debugmem.req.valid := !memongoing
    when (sbcs.sbautoincrement && io.debugmem.resp.fire()) {  sbaddr := sbaddr + 4.U  }
  } 
  when (decoded_addr(DMI_RegAddrs.DMI_SBDATA0) && (op === DMConsts.dmi_OP_READ)) {
    io.debugmem.req.bits.addr :=  sbaddr
    io.debugmem.req.bits.fcn := M_XRD
    io.debugmem.req.valid := !memongoing
    // for async data readily available
    // so capture it in reg
    when (io.debugmem.resp.fire()) {  sbdata := io.debugmem.resp.bits.data  }
    when (sbcs.sbautoincrement && io.debugmem.resp.fire()) {  sbaddr := sbaddr + 4.U  }
  }

  io.resetcore := coreresetval

  when((io.dmi.req.bits.addr === "h44".U) && io.dmi.req.valid){
    coreresetval := false.B
    dmstatus.allhalted := false.B
    dmstatus.anyhalted := false.B
    dmstatus.allrunning := true.B
    dmstatus.anyrunning := true.B
    dmstatus.allresumeack := true.B
    dmstatus.anyresumeack := true.B
  }
}
